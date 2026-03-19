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
              <el-table :data="row.items || []" border size="small" style="width: 100%">
                <el-table-column prop="materialName" label="产品名称" min-width="150" />
                <el-table-column prop="spec" label="型号规格" width="120" />
                <el-table-column prop="processName" label="工艺" width="100" />
                <el-table-column prop="quantity" label="良品数量" width="90" align="right" />
                <el-table-column prop="defectiveQty" label="废品数量" width="90" align="right" />
                <el-table-column prop="unitPrice" label="单价" width="80" align="right" />
                <el-table-column prop="amount" label="金额" width="90" align="right">
                  <template #default="{ row: item }">
                    {{ item.amount ? Number(item.amount).toFixed(2) : '0.00' }}
                  </template>
                </el-table-column>
                <el-table-column prop="detailRemark" label="明细备注" min-width="120" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="shipmentNo" label="发货单号" width="160" />
        <el-table-column prop="shipmentDate" label="发货日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="operator" label="制单人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '作废' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)" :disabled="row.status === 0">编辑</el-button>
            <el-button size="small" type="success" :icon="Printer" @click="handlePrint(row)">打印</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">作废</el-button>
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
              <el-input v-model="formData.operator" placeholder="制单人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" placeholder="备注" />
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
        <el-table :data="formData.items" border size="small" style="width: 100%">
          <el-table-column label="产品名称" min-width="160">
            <template #default="{ row, $index }">
              <el-select v-model="row.materialId" placeholder="输入物料名称搜索" filterable clearable size="small" remote
                :remote-method="(q) => searchMaterial(q, $index)" :loading="row._matLoading || false"
                style="width:100%" @change="(id) => onItemMaterialChange(id, $index)" :disabled="!formData.customerId">
                <el-option v-for="m in (row._matOptions || [])" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="型号规格" width="120">
            <template #default="{ row }">
              <el-input v-model="row.spec" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="工艺" width="120">
            <template #default="{ row, $index }">
              <el-select v-model="row.processId" placeholder="工艺" filterable clearable size="small"
                style="width:100%" @change="(id) => onItemProcessChange(id, $index)">
                <el-option v-for="p in processList" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="良品数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" size="small" style="width:100%"
                @change="calcItemAmount(row)" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="废品数量" width="100">
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
              <el-input v-model="row.detailRemark" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
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
import { Search, Refresh, Plus, Edit, Delete, View, Printer } from '@element-plus/icons-vue'
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
const defaultMatOptions = ref([])  // 当前客户默认前100条物料

const searchForm = reactive({ keyword: '', customerId: null, dateRange: [] })
const pagination = reactive({ page: 1, size: 20, total: 0 })

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
  } finally {
    loading.value = false
  }
}

const loadCustomers = async () => {
  const res = await request.get('/customers/all')
  customerList.value = res.data
}

const loadProcesses = async () => {
  const res = await request.get('/processes')
  processList.value = res.data
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
  // 只在展开时加载，且未加载过
  if (expandedRows.some(r => r.id === row.id) && (!row.items || row.items.length === 0)) {
    await loadItems(row)
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
    if (material.defaultPrice && Number(material.defaultPrice) > 0) {
      row.unitPrice = Number(material.defaultPrice)
      if (typeof calcItemAmount === 'function') calcItemAmount(row)
    }
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
      }
    } catch (e) { /* 查不到工艺不影响录入 */ }
  }
}

const onItemProcessChange = (id, index) => {
  const process = processList.value.find(p => p.id === id)
  formData.items[index].processName = process?.name || ''
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

const addItem = () => {
  formData.items.push({
    materialId: null, materialName: '', materialCode: '', spec: '',
    processId: null, processName: '', _matOptions: [...defaultMatOptions.value], _matLoading: false,
    quantity: 0, defectiveQty: 0, unitPrice: 0, amount: '0.00',
    detailRemark: ''
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
      formData.items = res.data || []
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
  await ElMessageBox.confirm(`确定作废发货单「${row.shipmentNo}」？`, '确认', { type: 'warning' })
  await request.delete(`/shipments/${row.id}`)
  ElMessage.success('已作废')
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

    const docTitle = config.printTitleDelivery || '致恒（致越）金属表面加工厂送货单'
    const companyName = config.printCompanyName || '致恒（致越）金属表面加工厂'
    const companyPhone = config.printCompanyPhone || '0750-2766036'
    const companyAddress = config.printCompanyAddress || '开平市，水口镇，唐良良兴村矮岗山'
    const contact1 = config.printContact1 || '廖总：13536094788'
    const contact2 = config.printContact2 || '仓管：13672842611'
    const sig1Label = config.printSignature1Label || '收货单位'
    const sig2Label = config.printSignature2Label || '仓管'
    const makerLabel = config.printMakerLabel || '制单人'
    const makerName = config.makerName || detail.operator || ''
    const deliveryRemark = config.printDeliveryRemark || '1. 货到当场验收，签收后概不负责\n2. 如有质量问题，3天内退货\n3. 本单据一式三联（客户、财务、仓库各一联）'

    const totalGoodQty = items.reduce((sum, item) => sum + (parseFloat(item.quantity) || 0), 0)
    const totalDefectiveQty = items.reduce((sum, item) => sum + (parseFloat(item.defectiveQty) || 0), 0)

    const itemRows = items.map((item, i) => {
      return `<tr>
        <td style="text-align:center">${i + 1}</td>
        <td>${item.materialName || ''}</td>
        <td>${item.spec || ''}</td>
        <td style="text-align:center">${item.unit || ''}</td>
        <td>${item.processName || ''}</td>
        <td style="text-align:center">${item.productionType || ''}</td>
        <td style="text-align:right">${item.quantity != null ? item.quantity : ''}</td>
        <td style="text-align:right">${item.defectiveQty != null ? item.defectiveQty : ''}</td>
        <td></td>
        <td>${item.detailRemark || ''}</td>
        <td style="text-align:right">${item.quantity != null ? item.quantity : ''}</td>
      </tr>`
    }).join('')

    const remarkLines = deliveryRemark.split('\n').join('<br>')

    const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>发货单 ${detail.shipmentNo || ''}</title>
<style>
  @page { size: 241mm 120mm; margin: 10mm 5mm; }
  * { box-sizing: border-box; }
  body { width: 231mm; font-family: SimSun, "宋体", serif; font-size: 9pt; margin: 0; }
  .header { display: flex; justify-content: flex-end; margin-bottom: 2mm; }
  .company-info { text-align: right; font-size: 9pt; line-height: 1.5; }
  .company-info .title { font-size: 14pt; font-weight: bold; }
  .order-info { display: flex; gap: 6mm; margin-bottom: 2mm; font-size: 9pt; }
  .content { display: flex; gap: 1mm; }
  .remark-vertical { writing-mode: vertical-rl; text-orientation: upright; border: 0.5pt solid #000; padding: 1mm; font-size: 8pt; min-width: 8mm; }
  .items-table { flex: 1; width: 100%; border-collapse: collapse; font-size: 8.5pt; }
  .items-table th, .items-table td { border: 0.5pt solid #000; padding: 1mm 1.5mm; }
  .items-table th { text-align: center; font-weight: bold; }
  .items-table tfoot td { font-weight: bold; }
  .items-table tfoot td.total { text-align: right; }
  .signature-row { margin-top: 3mm; display: flex; justify-content: space-between; font-size: 9pt; }
</style>
</head><body>
<div class="header">
  <div class="company-info">
    <div class="title">${docTitle}</div>
    <div>电话/传真：${companyPhone}</div>
    <div>地址：${companyAddress}</div>
    <div>${contact1} &nbsp;&nbsp; ${contact2}</div>
  </div>
</div>
<div class="order-info">
  <span>客户：${detail.customerName || ''}</span>
  <span>日期：${detail.shipmentDate || ''}</span>
  <span>单据编号：${detail.shipmentNo || ''}</span>
</div>
<div class="content">
  <div class="remark-vertical">备注：${remarkLines}</div>
  <table class="items-table">
    <thead>
      <tr>
        <th style="width:5%">序号</th>
        <th style="width:20%">品名</th>
        <th style="width:10%">规格</th>
        <th style="width:6%">单位</th>
        <th style="width:12%">工艺要求</th>
        <th style="width:8%">类型</th>
        <th style="width:10%">良品数量</th>
        <th style="width:8%">不良品</th>
        <th style="width:8%">原件退回</th>
        <th style="width:8%">备注</th>
        <th style="width:5%">合计</th>
      </tr>
    </thead>
    <tbody>${itemRows}</tbody>
    <tfoot>
      <tr>
        <td colspan="6">合计</td>
        <td class="total">${totalGoodQty || ''}</td>
        <td class="total">${totalDefectiveQty || ''}</td>
        <td class="total"></td>
        <td colspan="2"></td>
      </tr>
    </tfoot>
  </table>
</div>
<div class="signature-row">
  <span>${sig1Label}：________________</span>
  <span>${sig2Label}：________________</span>
  <span>${makerLabel}：${makerName}</span>
</div>
</body></html>`

    const win = window.open('', '_blank', 'width=900,height=650')
    win.document.write(html)
    win.document.close()
    win.onload = () => { win.print() }
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

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>