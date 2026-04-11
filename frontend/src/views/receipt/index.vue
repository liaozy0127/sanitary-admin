<template>
  <div class="receipt-page">
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
          <span>收货单列表</span>
          <div>
            <el-button type="success" :loading="exporting" @click="handleExport">导出 Excel</el-button>
            <el-button type="primary" :icon="Plus" @click="openDialog()">新增收货</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%"
        max-height="calc(100vh - 260px)" row-key="id" @expand-change="onExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-area">
              <el-table :data="row.items || []" border size="small" style="width: 100%" :row-class-name="itemRowClass">
                <el-table-column prop="materialName" label="产品名称" min-width="150" show-overflow-tooltip />
                <el-table-column prop="spec" label="型号规格" width="120" />
                <el-table-column prop="processName" label="工艺" width="100" />
                <el-table-column prop="receiptSource" label="收货来源" width="100" />
                <el-table-column prop="quantity" label="收货数量" width="90" align="right" />
                <el-table-column prop="unitPrice" label="单价" width="80" align="right">
                  <template #default="{ row: item }">
                    <el-tooltip v-if="item.receiptSource === '正常' && (!item.unitPrice || Number(item.unitPrice) === 0)"
                      content="正常收货未设置单价" placement="top">
                      <span style="color:#f56c6c;font-weight:bold">未设价</span>
                    </el-tooltip>
                    <span v-else>{{ item.unitPrice }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="金额" width="90" align="right">
                  <template #default="{ row: item }">
                    {{ item.amount ? Number(item.amount).toFixed(2) : '0.00' }}
                  </template>
                </el-table-column>
                <el-table-column prop="customerOrderNo" label="客户单号" width="120" />
                <el-table-column prop="detailRemark" label="明细备注" min-width="120" show-overflow-tooltip />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="receiptNo" label="收货单号" width="160" />
        <el-table-column prop="receiptDate" label="收货日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)">编辑</el-button>
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
            <el-form-item label="收货日期" prop="receiptDate">
              <el-date-picker v-model="formData.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择日期" />
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
            <el-form-item label="备注">
              <el-input v-model="formData.remark" placeholder="备注" :maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 明细表格 -->
      <div class="items-section">
        <div class="items-header">
          <span>收货明细</span>
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
          <el-table-column label="收货来源" width="110">
            <template #default="{ row }">
              <el-select v-model="row.receiptSource" size="small" style="width:100%" @change="onSourceChange(row)">
                <el-option value="正常" label="正常" />
                <el-option value="返工" label="返工" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="收货数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" size="small" style="width:100%"
                @change="calcItemAmount(row)" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100%"
                :disabled="row.receiptSource === '返工'"
                @change="calcItemAmount(row)" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="90">
            <template #default="{ row }">
              <span>{{ row.amount ? Number(row.amount).toFixed(2) : '0.00' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户单号" width="120">
            <template #default="{ row }">
              <el-input v-model="row.customerOrderNo" size="small" :maxlength="100" />
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
import { Search, Refresh, Plus, Edit, Delete, View } from '@element-plus/icons-vue'
import { getReceiptList, createReceipt, updateReceipt, deleteReceipt, downloadTemplate, importReceipts, exportReceipts } from '@/api/receipt'
import { getCustomerAll } from '@/api/customer'
import { getProcessAll } from '@/api/process'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增收货单')
const showImportDialog = ref(false)
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
const importFile = ref(null)

const expandedRowIds = ref(new Set())

const searchForm = reactive({ keyword: '', customerId: null, dateRange: [] })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const today = new Date().toISOString().split('T')[0]
const formData = reactive({
  receiptDate: today, customerId: null, customerName: '', remark: '',
  items: []
})

const rules = {
  receiptDate: [{ required: true, message: '请选择收货日期', trigger: 'change' }],
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
    const res = await getReceiptList(params)
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
  const res = await getCustomerAll()
  customerList.value = res.data
}

const loadProcesses = async () => {
  const res = await getProcessAll()
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
    const res = await request.get('/receipt-items', { params: { receiptId: row.id } })
    row.items = Array.isArray(res) ? res : (res.data || [])
  } catch (e) {
    row.items = []
  }
}

const itemRowClass = ({ row: item }) => {
  if (item.receiptSource === '正常' && (!item.unitPrice || Number(item.unitPrice) === 0)) {
    return 'row-no-price'
  }
  return ''
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
    item.spec = ''
    item.processId = null
    item.processName = ''
    item.receiptSource = '正常'
    item.quantity = 0
    item.unitPrice = 0
    item.amount = '0.00'
    item.customerOrderNo = ''
    item.detailRemark = ''
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
  // 自动带出工艺：查该客户+物料最近收货单里的工艺
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
  row._matLoading = true
  try {
    const res = await request.get('/materials/search', {
      params: { keyword: (query || '').trim(), customerId: formData.customerId }
    })
    const list = Array.isArray(res) ? res : (res.data || [])
    row._matOptions.splice(0, row._matOptions.length, ...list)
    // 同步更新默认列表缓存（无关键词时）
    if (!query || !query.trim()) {
      defaultMatOptions.value = list
    }
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

const onSourceChange = (row) => {
  if (row.receiptSource === '返工') {
    row.unitPrice = 0
    row.amount = '0.00'
  }
}

const addItem = () => {
  formData.items.push({
    materialId: null, materialName: '', materialCode: '', spec: '',
    processId: null, processName: '', receiptSource: '正常', _matOptions: [...defaultMatOptions.value], _matLoading: false,
    quantity: 0, unitPrice: 0, amount: '0.00',
    customerOrderNo: '', detailRemark: ''
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
    dialogTitle.value = '编辑收货单'
    editId.value = row.id
    Object.assign(formData, {
      receiptDate: row.receiptDate,
      customerId: row.customerId,
      customerName: row.customerName,
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
      const res = await request.get('/receipt-items', { params: { receiptId: row.id } })
      const rawItems = Array.isArray(res) ? res : (res.data || [])
      formData.items = rawItems.map(item => ({ ...item, _matOptions: item.materialId ? [{ id: item.materialId, name: item.materialName, code: item.materialCode, spec: item.spec }] : [], _matLoading: false }))
    } catch (e) {
      formData.items = []
    }
  } else {
    dialogTitle.value = '新增收货单'
    editId.value = null
    formData.receiptDate = today
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    receiptDate: today, customerId: null, customerName: '', remark: '',
    items: []
  })
  materialList.value = []
}

const handleSubmit = async () => {
  await formRef.value.validate()
  // 校验明细数量不能全部为0
  const hasValidQty = formData.items.some(item => (Number(item.quantity) || 0) > 0)
  if (!hasValidQty) {
    ElMessage.warning('请至少填写一条收货数量大于0的明细')
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
      await updateReceipt(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createReceipt(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除收货单「${row.receiptNo}」？`, '确认', { type: 'warning' })
  await deleteReceipt(row.id)
  ElMessage.success('已删除')
  fetchList()
}

const handleDownloadTemplate = async () => {
  const res = await downloadTemplate()
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = '收货单导入模板.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}

const handleFileChange = (file) => {
  importFile.value = file.raw
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    const formDataObj = new FormData()
    formDataObj.append('file', importFile.value)
    const res = await importReceipts(formDataObj)
    ElMessage.success(`导入完成：成功 ${res.data.success} 条，跳过 ${res.data.skip} 条，失败 ${res.data.fail} 条`)
    showImportDialog.value = false
    fetchList()
  } finally {
    importLoading.value = false
    importFile.value = null
  }
}

onMounted(() => {
  fetchList()
  loadCustomers()
  loadProcesses()
})

const exporting = ref(false)
const handleExport = async () => {
  exporting.value = true
  try {
    const res = await exportReceipts({
      keyword: searchForm.keyword,
      customerId: searchForm.customerId,
      startDate: searchForm.dateRange?.[0],
      endDate: searchForm.dateRange?.[1]
    })
    const url = URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    link.href = url
    link.download = `收货单_${today}.xlsx`
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
.upload-area { width: 100%; }
.expand-area { padding: 12px 20px; background: #f5f7fa; }
.items-section { margin-top: 16px; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }

/* 展开明细表头固定 */
:deep(.expand-area .el-table__header-wrapper) { position: sticky; top: 0; z-index: 10; background: #fff; }

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
/* 未设单价行标红 */
:deep(.row-no-price td) { background-color: #fff0f0 !important; }
:deep(.row-no-price td .cell) { color: #f56c6c; }
</style>
