<template>
  <div class="shipment-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="单号/客户" clearable style="width: 180px" @keyup.enter="fetchList" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 160px" @change="fetchList" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="-"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>发货单列表</span>
          <div>
            <el-button type="success" :loading="exporting" @click="handleExport">导出 Excel</el-button>
            <el-button type="primary" :icon="Plus" @click="openDialog()">新增发货</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%"
        max-height="calc(100vh - 260px)" row-key="id" @expand-change="onExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-area">
              <el-table :data="row.items || []" border size="small" style="width: 100%" show-summary :summary-method="(p) => getShipmentSummary(p, row.customerId)">
                <el-table-column prop="materialName" label="产品名称" min-width="150" show-overflow-tooltip />
                <el-table-column prop="spec" label="型号规格" width="120" />
                <el-table-column prop="processName" label="工艺" width="100" />
                <el-table-column prop="quantity" label="良品数量" width="90" align="right" />
                <el-table-column prop="defectiveQty" label="原件退回" width="90" align="right" />
                <el-table-column prop="unitPrice" label="单价" width="80" align="right">
                  <template #default="{ row: item }">
                    {{ item.unitPrice ? Number(item.unitPrice).toFixed(2) : '0.00' }}
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="金额" width="90" align="right">
                  <template #default="{ row: item }">
                    {{ item.amount ? Number(item.amount).toFixed(2) : '0.00' }}
                  </template>
                </el-table-column>
                <el-table-column prop="detailRemark" label="明细备注" min-width="120" show-overflow-tooltip />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="shipmentNo" label="发货单号" width="160" />
        <el-table-column prop="shipmentDate" label="发货日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="operator" label="制单人" width="90" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="success" :icon="Printer" @click="handlePrint(row)">打印</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList" @current-change="fetchList" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="发货日期" prop="shipmentDate">
              <el-date-picker v-model="formData.shipmentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择日期" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="formData.customerId" placeholder="选择客户" style="width:100%" @change="onCustomerChange" filterable>
                <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制单人">
              <el-input v-model="formData.operator" placeholder="制单人" :maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" placeholder="备注" :maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 明细表格 -->
      <div class="items-section">
        <div class="items-header">
          <span>发货明细</span>
          <el-button type="primary" size="small" :icon="Plus" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="formData.items" border size="small" style="width: 100%" max-height="400">
          <el-table-column label="产品名称" min-width="160">
            <template #default="{ row, $index }">
              <el-select v-model="row.materialId" placeholder="输入物料名称搜索" filterable clearable size="small" remote
                :remote-method="(q) => searchMaterial(q, $index)" :loading="row._matLoading || false"
                style="width:100%" @change="(id) => onItemMaterialChange(id, $index)" :disabled="!formData.customerId">
                <el-option v-for="m in (row._matOptions || [])" :key="m.id" :label="m.spec ? m.name + '（' + m.spec + '）' : m.name" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="型号规格" width="120">
            <template #default="{ row }">
              <el-input v-model="row.spec" size="small" :maxlength="200" />
            </template>
          </el-table-column>
          <el-table-column label="工艺" width="120">
            <template #default="{ row, $index }">
              <el-select v-model="row.processId" placeholder="工艺" filterable clearable size="small"
                style="width:100%" @change="(id) => onItemProcessChange(id, $index)"
                :filter-method="filterProcess">
                <el-option v-for="p in filteredProcessList" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="库存" width="90" align="right">
            <template #default="{ row }">
              <template v-if="row._invLoading">
                <el-icon class="is-loading"><Loading /></el-icon>
              </template>
              <template v-else-if="row.materialId">
                <el-tooltip :content="`总库存：${row._invQty ?? '-'}　返工：${row._invRework ?? '-'}`" placement="top">
                  <span :class="getInvClass(row)">{{ getInvRemain(row) }}</span>
                </el-tooltip>
              </template>
              <template v-else><span style="color:#ccc">—</span></template>
            </template>
          </el-table-column>
          <el-table-column label="良品数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" size="small" style="width:100%"
                @change="calcItemAmount(row)" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="原件退回" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.defectiveQty" :min="0" :precision="0" size="small" style="width:100%"
                controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100%"
                @change="calcItemAmount(row)" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="90">
            <template #default="{ row }">
              <span>{{ row.amount ? Number(row.amount).toFixed(2) : '0.00' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="明细备注" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.detailRemark" size="small" :maxlength="500" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button size="small" type="danger" :icon="Delete" @click="removeItem($index)" circle />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View, Printer, Loading } from '@element-plus/icons-vue'
import { exportShipments } from '@/api/shipment'
import { getPrintConfig } from '@/api/config'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增发货单')
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])
const materialList = ref([])
const processList = ref([])
const filteredProcessList = ref([])
const filterProcess = (query) => {
  if (!query) {
    filteredProcessList.value = processList.value
  } else {
    const q = query.toLowerCase()
    filteredProcessList.value = processList.value.filter(p =>
      (p.name && p.name.toLowerCase().includes(q)) ||
      (p.code && p.code.toLowerCase().includes(q))
    )
  }
}
const defaultMatOptions = ref([])  // 当前客户默认前100条物料

const expandedRowIds = ref(new Set())

const searchForm = reactive({ keyword: '', customerId: null, dateRange: [] })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const today = new Date().toISOString().split('T')[0]
const formData = reactive({
  shipmentDate: today, customerId: null, customerName: '', operator: '', remark: '',
  items: []
})

const rules = {
  shipmentDate: [{ required: true, message: '请选择发货日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined,
      customerId: searchForm.customerId || undefined,
      startDate: searchForm.dateRange?.[0] || undefined,
      endDate: searchForm.dateRange?.[1] || undefined
    }
    const res = await request.get('/shipments', { params })
    tableData.value = res.data.records
    pagination.total = res.data.total
    // 刷新后重新加载已展开行的明细（新 row 对象不含 items）
    if (expandedRowIds.value.size > 0) {
      tableData.value.forEach(row => {
        if (expandedRowIds.value.has(row.id)) loadItems(row)
      })
    }
  } finally {
    loading.value = false
  }
}

const loadCustomers = async () => {
  const res = await request.get('/customers/all')
  customerList.value = res.data
}

const loadProcesses = async () => {
  const res = await request.get('/processes/all')
  processList.value = Array.isArray(res) ? res : (res.data || [])
  filteredProcessList.value = processList.value
}

const loadMaterials = async (customerId) => {
  if (!customerId) { materialList.value = []; return }
  const res = await request.get('/materials/search', { params: { customerId } })
  materialList.value = res.data
}

const loadItems = async (row) => {
  if (row.items && row.items.length > 0) return
  try {
    const res = await request.get('/shipment-items', { params: { shipmentId: row.id } })
    row.items = Array.isArray(res) ? res : (res.data || [])
  } catch (e) {
    row.items = []
  }
}

const onExpandChange = async (row, expandedRows) => {
  if (expandedRows.some(r => r.id === row.id)) {
    expandedRowIds.value.add(row.id)
    if (!row.items || row.items.length === 0) await loadItems(row)
  } else {
    expandedRowIds.value.delete(row.id)
  }
}

const onCustomerChange = async (id) => {
  const customer = customerList.value.find(c => c.id === id)
  formData.customerName = customer?.name || ''
  // 切换客户时清空所有行，并预加载默认100条
  formData.items.forEach(item => {
    item._matOptions = []
    item.materialId = null
    item.materialName = ''
    item.materialCode = ''
  })
  if (id) {
    try {
      const res = await request.get('/materials/search', { params: { customerId: id, keyword: '' } })
      defaultMatOptions.value = Array.isArray(res) ? res : (res.data || [])
    } catch (e) { defaultMatOptions.value = [] }
  } else {
    defaultMatOptions.value = []
  }
}

const onItemMaterialChange = async (id, index) => {
  const row = formData.items[index]
  if (!row) return
  // 先重置单价和工艺
  row.unitPrice = 0
  row.processId = null
  row.processName = ''

  if (typeof calcItemAmount === 'function') calcItemAmount(row)
  if (!id) return
  // 从当前行搜索结果找物料信息
  const material = (row._matOptions || []).find(m => m.id === id)
  if (material) {
    row.materialName = material.name
    row.materialCode = material.code || ''
    row.spec = material.spec || ''
  }
  // 自动带出工艺
  if (formData.customerId && id) {
    try {
      const res = await request.get('/receipt-items/latest-process', {
        params: { customerId: formData.customerId, materialId: id }
      })
      const data = Array.isArray(res) ? null : res
      if (data && data.processId) {
        row.processId = data.processId
        row.processName = data.processName || ''
        // 工艺带出后立即查价格
        await queryAndSetPrice(row)
      }
    } catch (e) { /* 查不到工艺不影响录入 */ }
  }
  // 如果仍未拿到价格，回退到物料默认单价
  if ((!row.unitPrice || Number(row.unitPrice) === 0) && material && material.defaultPrice && Number(material.defaultPrice) > 0) {
    row.unitPrice = Number(material.defaultPrice)
    if (typeof calcItemAmount === 'function') calcItemAmount(row)
  }
  // 查询当前库存（选完物料/工艺后）
  await loadItemInventory(row)
}

const onItemProcessChange = async (id, index) => {
  const row = formData.items[index]
  if (!row) return
  const process = processList.value.find(p => p.id === id)
  row.processName = process?.name || ''
  // 工艺变化时先重置单价
  row.unitPrice = 0
  calcItemAmount(row)
  // 工艺选定后查价格表
  if (id) await queryAndSetPrice(row)
  // 工艺变化后重新查库存（不同工艺对应不同库存记录）
  loadItemInventory(row)
}

const queryAndSetPrice = async (row) => {
  if (!row.materialId || !row.processId || !formData.customerId) return
  try {
    const res = await request.get('/material-process-prices/query', {
      params: { customerId: formData.customerId, materialId: row.materialId, processId: row.processId }
    })
    const data = res.data || res
    if (data.unitPrice != null && Number(data.unitPrice) > 0) {
      row.unitPrice = Number(data.unitPrice)
      calcItemAmount(row)
    }
  } catch (e) { /* 查不到不影响录入 */ }
}


const searchMaterial = async (query, index) => {
  if (!formData.customerId) return
  const row = formData.items[index]
  if (!row) return
  // 无关键词时直接用默认列表
  if (!query || !query.trim()) {
    row._matOptions.splice(0, row._matOptions.length, ...defaultMatOptions.value)
    return
  }
  row._matLoading = true
  try {
    const res = await request.get('/materials/search', {
      params: { keyword: query.trim(), customerId: formData.customerId }
    })
    const list = Array.isArray(res) ? res : (res.data || [])
    row._matOptions.splice(0, row._matOptions.length, ...list)
  } catch (e) {
    row._matOptions.splice(0, row._matOptions.length)
  } finally {
    row._matLoading = false
  }
}

const calcItemAmount = (item) => {
  const qty = Number(item.quantity) || 0
  const price = Number(item.unitPrice) || 0
  item.amount = (qty * price).toFixed(2)
}

// 判断客户是否为现金客户
const isCashCustomer = (customerId) => {
  const c = customerList.value.find(c => c.id === customerId)
  return c && c.customerType === '现金'
}

// 发货展开明细合计行
const getShipmentSummary = ({ columns, data }, customerId) => {
  const sums = []
  columns.forEach((col, index) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    if (col.property === 'quantity') {
      sums[index] = data.reduce((s, it) => s + (Number(it.quantity) || 0), 0)
    } else if (col.property === 'defectiveQty') {
      sums[index] = data.reduce((s, it) => s + (Number(it.defectiveQty) || 0), 0)
    } else if (col.property === 'amount') {
      sums[index] = data.reduce((s, it) => s + (Number(it.amount) || 0), 0).toFixed(2)
    } else {
      sums[index] = ''
    }
  })
  return sums
}

// 查询该行的当前库存（选物料/工艺后调用）
const loadItemInventory = async (row) => {
  if (!row.materialId || !formData.customerId) {
    row._invQty = null; row._invRework = null; return
  }
  row._invLoading = true
  try {
    const res = await request.get('/inventory/query', {
      params: { materialId: row.materialId, customerId: formData.customerId, processId: row.processId || undefined }
    })
    const data = res.data || res
    row._invQty = Number(data.quantity) || 0
    row._invRework = Number(data.reworkQty) || 0
  } catch (e) {
    row._invQty = null; row._invRework = null
  } finally {
    row._invLoading = false
  }
}

// 库存剩余 = (当前库存 + 原已发量) - 本行填写数量
// 新增时 _origShipQty=0；编辑时 _origShipQty=打开弹窗时该行原有发货量
const getInvRemain = (row) => {
  if (row._invQty == null) return '—'
  const ship = (Number(row.quantity) || 0) + (Number(row.defectiveQty) || 0)
  return row._invQty + (row._origShipQty || 0) - ship
}

// 剩余库存不足时标红
const getInvClass = (row) => {
  if (row._invQty == null) return ''
  const ship = (Number(row.quantity) || 0) + (Number(row.defectiveQty) || 0)
  return row._invQty + (row._origShipQty || 0) - ship < 0 ? 'inv-insufficient' : 'inv-ok'
}

const addItem = () => {
  formData.items.push({
    materialId: null, materialName: '', materialCode: '', spec: '',
    processId: null, processName: '', _matOptions: [...defaultMatOptions.value], _matLoading: false,
    quantity: 0, defectiveQty: 0, unitPrice: 0, amount: '0.00',
    detailRemark: '', _invQty: null, _invRework: null, _invLoading: false, _origShipQty: 0
  })
}

const removeItem = (index) => {
  formData.items.splice(index, 1)
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.customerId = null
  searchForm.dateRange = []
  pagination.page = 1
  fetchList()
}

const openDialog = async (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑发货单'
    editId.value = row.id
    Object.assign(formData, {
      shipmentDate: row.shipmentDate,
      customerId: row.customerId,
      customerName: row.customerName,
      operator: row.operator || '',
      remark: row.remark || '',
      items: []
    })
    // 加载默认物料列表（前100条）
    try {
      const mres = await request.get('/materials/search', { params: { customerId: row.customerId, keyword: '' } })
      defaultMatOptions.value = Array.isArray(mres) ? mres : (mres.data || [])
    } catch(e) { defaultMatOptions.value = [] }
    // Load items
    try {
      const res = await request.get('/shipment-items', { params: { shipmentId: row.id } })
      const rawItems = Array.isArray(res) ? res : (res.data || [])
      formData.items = rawItems.map(item => ({
        ...item,
        _matOptions: item.materialId ? [{ id: item.materialId, name: item.materialName, code: item.materialCode, spec: item.spec }] : [],
        _matLoading: false,
        _invQty: null, _invRework: null, _invLoading: false,
        _origShipQty: (Number(item.quantity) || 0) + (Number(item.defectiveQty) || 0)
      }))
      // 异步加载每行库存
      formData.items.forEach(item => loadItemInventory(item))
    } catch (e) {
      formData.items = []
    }
  } else {
    dialogTitle.value = '新增发货单'
    editId.value = null
    formData.shipmentDate = today
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    shipmentDate: today, customerId: null, customerName: '', operator: '', remark: '',
    items: []
  })
  materialList.value = []
}

const handleSubmit = async () => {
  await formRef.value.validate()
  // 校验明细数量不能全部为0
  const hasValidQty = formData.items.some(item => (Number(item.quantity) || 0) > 0 || (Number(item.defectiveQty) || 0) > 0)
  if (!hasValidQty) {
    ElMessage.warning('请至少填写一条良品数量或废品数量大于0的明细')
    return
  }
  // 校验明细中物料和工艺必填
  const invalidItem = formData.items.find(item => !item.materialId || !item.processId)
  if (invalidItem) {
    ElMessage.warning('明细中物料和工艺均为必填')
    return
  }
  submitLoading.value = true
  try {
    const payload = { ...formData, items: formData.items }
    if (editId.value) {
      const res = await request.put(`/shipments/${editId.value}`, payload)
      ElMessage.success('更新成功')
    } else {
      const res = await request.post('/shipments', payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除发货单「${row.shipmentNo}」？`, '确认', { type: 'warning' })
  await request.delete(`/shipments/${row.id}`)
  ElMessage.success('已删除')
  fetchList()
}

onMounted(() => {
  fetchList()
  loadCustomers()
  loadProcesses()
})

const handlePrint = async (row) => {
  try {
    const [detailRes, configRes] = await Promise.all([
      request.get(`/shipments/${row.id}`),
      getPrintConfig()
    ])
    const detail = detailRes.data || detailRes
    const config = configRes.data || configRes
    const items = detail.items || []

    // 判断客户类型
    const isCash = isCashCustomer(detail.customerId)
    const colCount = isCash ? 10 : 9  // 月结客户去掉"单价"列

    const docTitle = config.printTitleDelivery || '致恒（致越）金属表面加工厂送货单'
    const delSig1 = config.printDeliverySig1Label || '制单人'
    const sig1Label = config.printDeliverySig3Label || '收货单位'
    const sig2Label = config.printDeliverySig2Label || '仓管员'
    const deliveryRemark = config.printDeliveryRemark || '1. 货到当场验收，签收后概不负责\n2. 如有质量问题，3天内退货\n3. 本单据一式三联（客户、财务、仓库各一联）'
    const companyPhone = config.printCompanyPhone || ''
    const contact1 = config.printContact1 || ''
    const contact2 = config.printContact2 || ''
    const companyAddress = config.printCompanyAddress || ''

    const totalGoodQty = items.reduce((sum, item) => sum + (parseFloat(item.quantity) || 0), 0)
    const totalDefectiveQty = items.reduce((sum, item) => sum + (parseFloat(item.defectiveQty) || 0), 0)

    // 纸张: 241mm × 140mm，页边距 5mm，96dpi 下 1mm ≈ 3.7795px
    // 可用高度: 130mm，page-body 宽度 = 241 - 5*2(margin) - 10*2(孔戳) = 211mm
    const MM_TO_PX = 3.7795
    const PAGE_HEIGHT_PX = Math.round(130 * MM_TO_PX)
    const PAGE_BODY_WIDTH_PX = Math.round(211 * MM_TO_PX)

    const remarkLines = deliveryRemark.split('\n').join('<br>')

    const CSS = `
  @page { size: 241mm 140mm; margin: 5mm; }
  * { box-sizing: border-box; }
  body { font-family: SimSun, "宋体", serif; font-size: 9pt; margin: 0; }
  .page { display: flex; align-items: stretch; page-break-after: always; }
  .page.last { page-break-after: auto; }
  .stamp-side { flex: 0 0 10mm; }
  .page-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  .pt { width: 100%; border-collapse: collapse; font-size: 12pt; }
  .pt th, .pt td { border: 0.5pt solid #0066CC; padding: 1mm 1.5mm; word-break: break-all; }
  .pt th { text-align: center; font-weight: bold; background: #f0f6ff; white-space: nowrap; }
  .title-cell { border: none !important; text-align: center; font-size: 16pt; font-weight: bold; padding: 2mm 0 !important; background: white !important; }
  .meta-cell { border: none !important; padding: 1mm 0 !important; background: white !important; font-weight: normal; }
  .meta-flex { display: flex; justify-content: space-around; }
  .meta-flex span { flex: 1; text-align: center; }
  .info-cell { border: none !important; padding: 0.5mm 0 !important; background: white !important; font-weight: normal; }
  .info-flex { display: flex; justify-content: center; gap: 8mm; padding: 0 2mm; }
  .info-flex span { text-align: center; }
  .foot-remark { background: white; font-size: 9pt; }
  .sig-line { display: flex; justify-content: flex-start; font-size: 10pt; margin-top: 1.5mm; padding: 0 2mm; }
  .sig-line span { flex: 1; text-align: left; }
  .measure-wrap { position: absolute; top: -9999px; left: 0; width: ${PAGE_BODY_WIDTH_PX}px; visibility: hidden; }`

    const colWidthsCash = ['5%','29%','13%','5%','11%','6%','8%','7%','8%','8%']
    const colWidthsMonthly = ['5%','30%','14%','5%','12%','6%','10%','10%','8%']
    const colWidths = isCash ? colWidthsCash : colWidthsMonthly
    const colGroupHtml = `<colgroup>${colWidths.map(w => `<col style="width:${w}">`).join('')}</colgroup>`

    // 表头行 HTML（不含 <tr> 包裹）
    const headerCellsHtml = isCash
      ? `<th style="width:5%">序号</th><th style="width:29%">品名</th><th style="width:13%">规格</th><th style="width:5%">单位</th><th style="width:11%">工艺要求</th><th style="width:6%">类型</th><th style="width:8%">良品数量</th><th style="width:7%">单价</th><th style="width:8%">原件退回</th><th style="width:8%">备注</th>`
      : `<th style="width:5%">序号</th><th style="width:30%">品名</th><th style="width:14%">规格</th><th style="width:5%">单位</th><th style="width:12%">工艺要求</th><th style="width:6%">类型</th><th style="width:10%">良品数量</th><th style="width:10%">原件退回</th><th style="width:8%">备注</th>`

    const makeDataRowHtml = (item, seqNo) => {
      if (isCash) {
        return `<tr>
          <td style="text-align:center">${seqNo}</td>
          <td>${item.materialName || ''}</td>
          <td>${item.spec || ''}</td>
          <td style="text-align:center">${item.unit || '个'}</td>
          <td>${item.processName || ''}</td>
          <td style="text-align:center">${item.productionType || '正常'}</td>
          <td style="text-align:right">${item.quantity != null ? item.quantity : ''}</td>
          <td style="text-align:right">${item.unitPrice != null ? item.unitPrice : ''}</td>
          <td style="text-align:right">${item.defectiveQty != null ? item.defectiveQty : ''}</td>
          <td>${item.detailRemark || ''}</td>
        </tr>`
      } else {
        return `<tr>
          <td style="text-align:center">${seqNo}</td>
          <td>${item.materialName || ''}</td>
          <td>${item.spec || ''}</td>
          <td style="text-align:center">${item.unit || '个'}</td>
          <td>${item.processName || ''}</td>
          <td style="text-align:center">${item.productionType || '正常'}</td>
          <td style="text-align:right">${item.quantity != null ? item.quantity : ''}</td>
          <td style="text-align:right">${item.defectiveQty != null ? item.defectiveQty : ''}</td>
          <td>${item.detailRemark || ''}</td>
        </tr>`
      }
    }

    const totalRowHtml = isCash
      ? `<tr><td colspan="6" style="text-align:right;font-weight:bold;">合计</td><td style="text-align:right;font-weight:bold;">${totalGoodQty || ''}</td><td></td><td style="text-align:right;font-weight:bold;">${totalDefectiveQty || ''}</td><td></td></tr>`
      : `<tr><td colspan="6" style="text-align:right;font-weight:bold;">合计</td><td style="text-align:right;font-weight:bold;">${totalGoodQty || ''}</td><td style="text-align:right;font-weight:bold;">${totalDefectiveQty || ''}</td><td></td></tr>`

    // ── 第一步：测量每行实际渲染高度 ──
    const measureIframe = document.createElement('iframe')
    measureIframe.style.cssText = 'position:fixed;top:-9999px;left:-9999px;width:241mm;height:140mm;border:none;'
    document.body.appendChild(measureIframe)

    const measureHtml = `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>${CSS}</style></head><body>
<div class="measure-wrap">
  <table class="pt" style="width:100%">
    ${colGroupHtml}
    <tbody id="mbody">
      ${items.map((item, i) => {
        const inner = makeDataRowHtml(item, i + 1).replace(/^<tr>/, '').replace(/<\/tr>$/, '')
        return `<tr id="mr${i}">${inner}</tr>`
      }).join('')}
      <tr id="mr_total">${totalRowHtml.replace(/^<tr>/, '').replace(/<\/tr>$/, '')}</tr>
      <tr id="mr_empty"><td>&nbsp;</td>${'<td>&nbsp;</td>'.repeat(colCount - 1)}</tr>
    </tbody>
  </table>
</div>
</body></html>`

    await new Promise(resolve => {
      measureIframe.onload = resolve
      measureIframe.srcdoc = measureHtml
    })

    const mDoc = measureIframe.contentDocument
    const rowHeights = items.map((_, i) => mDoc.getElementById(`mr${i}`)?.offsetHeight || 20)
    const totalRowHeight = mDoc.getElementById('mr_total')?.offsetHeight || 20
    const emptyRowHeight = mDoc.getElementById('mr_empty')?.offsetHeight || 20

    // 测量 overhead（固定头尾）
    const overheadHtml = `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>${CSS}</style></head><body>
<div class="measure-wrap" id="owrap">
  <table class="pt" style="width:100%">
    ${colGroupHtml}
    <thead>
      <tr><th colspan="${colCount}" class="title-cell" id="oh_title">${docTitle}</th></tr>
      <tr><td colspan="${colCount}" class="info-cell" id="oh_info1">
        <div class="info-flex">
          <span>电话/传真：${companyPhone}</span>
          <span>${contact1}</span>
          <span>${contact2}</span>
        </div>
      </td></tr>
      <tr><td colspan="${colCount}" class="info-cell" id="oh_info2">
        <div class="info-flex"><span>地址：${companyAddress}</span></div>
      </td></tr>
      <tr><td colspan="${colCount}" class="meta-cell" id="oh_meta">
        <div class="meta-flex">
          <span>客户：${detail.customerName || ''}</span>
          <span>发货日期：${detail.shipmentDate || ''}</span>
          <span>单号：${detail.shipmentNo || ''}</span>
        </div>
      </td></tr>
      <tr id="oh_hdr">${headerCellsHtml}</tr>
    </thead>
    <tbody><tr><td>&nbsp;</td>${'<td>&nbsp;</td>'.repeat(colCount - 1)}</tr></tbody>
    <tfoot>
      <tr><td colspan="${colCount}" class="foot-remark" id="oh_remark">${remarkLines}</td></tr>
    </tfoot>
  </table>
  <div class="sig-line" id="oh_sig">
    <span>${delSig1}</span><span>${sig2Label}</span><span>${sig1Label}</span>
  </div>
</div>
</body></html>`

    const overheadIframe = document.createElement('iframe')
    overheadIframe.style.cssText = 'position:fixed;top:-9999px;left:-9999px;width:241mm;height:140mm;border:none;'
    document.body.appendChild(overheadIframe)

    await new Promise(resolve => {
      overheadIframe.onload = resolve
      overheadIframe.srcdoc = overheadHtml
    })

    const oDoc = overheadIframe.contentDocument
    // 直接量整个容器高度，避免逐元素累加带来的 border 折叠误差
    const overheadPx = (oDoc.getElementById('owrap')?.offsetHeight || 0) + 2 // +2px 安全余量

    document.body.removeChild(measureIframe)
    document.body.removeChild(overheadIframe)

    const availableBodyPx = PAGE_HEIGHT_PX - overheadPx

    // ── 第二步：贪心分页 ──
    // 非末页用满 availableBodyPx，分完后检查末页合计行是否溢出
    const chunks = []
    let currentChunk = []
    let usedPx = 0

    for (let i = 0; i < items.length; i++) {
      const h = rowHeights[i]
      if (currentChunk.length > 0 && usedPx + h > availableBodyPx) {
        chunks.push(currentChunk)
        currentChunk = []
        usedPx = 0
      }
      currentChunk.push({ item: items[i], globalIdx: i })
      usedPx += h
    }
    chunks.push(currentChunk)

    if (chunks.length === 0) chunks.push([])

    // 检查末页：数据行 + 合计行是否超出，超出则把最后一条移入新页
    const lastChunk = chunks[chunks.length - 1]
    const lastUsedPx = lastChunk.reduce((s, { globalIdx }) => s + rowHeights[globalIdx], 0)
    if (lastChunk.length > 0 && lastUsedPx + totalRowHeight > availableBodyPx) {
      const overflow = lastChunk.splice(lastChunk.length - 1)
      chunks.push(overflow)
    }

    // ── 第三步：生成最终 HTML ──
    const emptyRow = `<tr>${'<td>&nbsp;</td>'.repeat(colCount)}</tr>`

    let globalSeq = 0
    const makePage = (chunk, isLast) => {
      const dataRows = chunk.map(({ item }) => {
        globalSeq++
        return makeDataRowHtml(item, globalSeq)
      }).join('')

      const chunkUsedPx = chunk.reduce((s, { globalIdx }) => s + rowHeights[globalIdx], 0)

      let padRows = ''
      if (isLast) {
        const remaining = availableBodyPx - totalRowHeight - chunkUsedPx
        padRows = emptyRow.repeat(Math.max(0, Math.floor(remaining / emptyRowHeight)))
      } else {
        const remaining = availableBodyPx - chunkUsedPx
        padRows = emptyRow.repeat(Math.max(0, Math.floor(remaining / emptyRowHeight)))
      }

      const totalRow = isLast ? totalRowHtml : ''

      return `<div class="page${isLast ? ' last' : ''}">
        <div class="stamp-side"></div>
        <div class="page-body">
        <table class="pt">
          ${colGroupHtml}
          <thead>
            <tr><th colspan="${colCount}" class="title-cell">${docTitle}</th></tr>
            <tr><td colspan="${colCount}" class="info-cell">
              <div class="info-flex">
                <span>电话/传真：${companyPhone}</span>
                <span>${contact1}</span>
                <span>${contact2}</span>
              </div>
            </td></tr>
            <tr><td colspan="${colCount}" class="info-cell">
              <div class="info-flex">
                <span>地址：${companyAddress}</span>
              </div>
            </td></tr>
            <tr><td colspan="${colCount}" class="meta-cell">
              <div class="meta-flex">
                <span>客户：${detail.customerName || ''}</span>
                <span>发货日期：${detail.shipmentDate || ''}</span>
                <span>单号：${detail.shipmentNo || ''}</span>
              </div>
            </td></tr>
            <tr>${headerCellsHtml}</tr>
          </thead>
          <tbody>${dataRows}${padRows}${totalRow}</tbody>
          <tfoot>
            <tr><td colspan="${colCount}" class="foot-remark">${remarkLines}</td></tr>
          </tfoot>
        </table>
        <div class="sig-line">
          <span>${delSig1}</span>
          <span>${sig2Label}</span>
          <span>${sig1Label}</span>
        </div>
        </div>
        <div class="stamp-side"></div>
      </div>`
    }

    const pages = chunks.map((chunk, i) => makePage(chunk, i === chunks.length - 1)).join('\n')

    const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>发货单 ${detail.shipmentNo || ''}</title>
<style>${CSS}</style></head><body>
${pages}
</body></html>`

    const iframe = document.createElement('iframe')
    iframe.style.cssText = 'position:fixed;top:-9999px;left:-9999px;width:0;height:0;border:none;'
    document.body.appendChild(iframe)
    iframe.contentDocument.write(html)
    iframe.contentDocument.close()
    iframe.contentWindow.onafterprint = () => document.body.removeChild(iframe)
    iframe.contentWindow.print()
  } catch (e) {
    ElMessage.error('打印失败，请重试')
  }
}

const exporting = ref(false)
const handleExport = async () => {
  exporting.value = true
  try {
    const res = await exportShipments({
      keyword: searchForm.keyword,
      customerId: searchForm.customerId,
      startDate: searchForm.dateRange?.[0],
      endDate: searchForm.dateRange?.[1]
    })
    const url = URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    link.href = url
    link.download = `发货单_${today}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.expand-area { padding: 12px 20px; background: #f5f7fa; }
.items-section { margin-top: 16px; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }

/* 展开明细表头固定 */
:deep(.expand-area .el-table__header-wrapper) { position: sticky; top: 0; z-index: 10; background: #fff; }

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
.inv-ok { color: #67c23a; font-weight: 600; }
.inv-insufficient { color: #f56c6c; font-weight: 600; }
</style>