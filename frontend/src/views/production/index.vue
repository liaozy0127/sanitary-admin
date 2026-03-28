<template>
  <div class="production-page">
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
          <span>排产单列表</span>
          <div>
            <el-button type="warning" :icon="Upload" @click="showImportDialog = true">批量导入</el-button>
            <el-button type="success" :loading="exporting" @click="handleExport">导出 Excel</el-button>
            <el-button type="primary" :icon="Plus" @click="openDialog()">新增排产</el-button>
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
                <el-table-column prop="receiptType" label="收货类型" width="100" />
                <el-table-column prop="unit" label="单位" width="80" />
                <el-table-column prop="plannedQty" label="排产数量" width="90" align="right" />
                <el-table-column prop="platingPrice" label="单价" width="90" align="right" />
                <el-table-column prop="productionType" label="排产方式" width="90" />
                <el-table-column prop="detailRemark" label="明细备注" min-width="120" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productionNo" label="排产单号" width="160" />
        <el-table-column prop="productionDate" label="排产日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="remark" label="备注" min-width="120" />
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
            <el-form-item label="排产日期" prop="productionDate">
              <el-date-picker v-model="formData.productionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择日期" />
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
              <el-input v-model="formData.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 明细表格 -->
      <div class="items-section">
        <div class="items-header">
          <span>排产明细</span>
          <el-button type="primary" size="small" :icon="Plus" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="formData.items" border size="small" style="width: 100%" max-height="400">
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
          <el-table-column label="收货类型" width="100">
            <template #default="{ row }">
              <el-select v-model="row.receiptType" size="small" style="width:100%">
                <el-option value="正常" label="正常" />
                <el-option value="返工" label="返工" />
                <el-option value="不良数" label="不良数" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="{ row }">
              <el-input v-model="row.unit" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="排产数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.plannedQty" :min="0" :precision="0" size="small" style="width:100%"
                controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.platingPrice" :min="0" :precision="2" size="small" style="width:100%"
                controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="排产方式" width="90">
            <template #default="{ row }">
              <el-input v-model="row.productionType" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="明细备注" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.detailRemark" size="small" />
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

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="批量导入排产单" width="500px">
      <el-upload class="upload-area" drag accept=".xlsx,.xls" :auto-upload="false" :on-change="handleFileChange" :limit="1">
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip><div class="el-upload__tip">只支持 .xlsx .xls 格式</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Upload, View, Printer } from '@element-plus/icons-vue'
import { exportProductions } from '@/api/production'
import { getPrintConfig } from '@/api/config'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增排产单')
const showImportDialog = ref(false)
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])
const materialList = ref([])
const processList = ref([])
const defaultMatOptions = ref([])  // 当前客户默认前100条物料
const importFile = ref(null)

const expandedRowIds = ref(new Set())

const searchForm = reactive({ keyword: '', customerId: null })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const today = new Date().toISOString().split('T')[0]
const formData = reactive({
  productionDate: today, customerId: null, customerName: '', remark: '',
  items: []
})

const rules = {
  productionDate: [{ required: true, message: '请选择排产日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined,
      customerId: searchForm.customerId || undefined,
      prodStatus: undefined // prodStatus param retained for API compatibility but no longer stored on main entity
    }
    const res = await request.get('/productions', { params })
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
}

const loadMaterials = async (customerId) => {
  if (!customerId) { materialList.value = []; return }
  const res = await request.get('/materials/search', { params: { customerId } })
  materialList.value = res.data
}

const loadItems = async (row) => {
  if (row.items && row.items.length > 0) return
  try {
    const res = await request.get('/production-items', { params: { productionId: row.id } })
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
  row.platingPrice = 0
  row.processId = null
  row.processName = ''
  if (!id) return
  // 从当前行搜索结果找物料信息
  const material = (row._matOptions || []).find(m => m.id === id)
  if (material) {
    row.materialName = material.name
    row.materialCode = material.code || ''
    row.spec = material.spec || ''
    // 带出默认单价填充电镀单价
    if (material.defaultPrice && Number(material.defaultPrice) > 0) {
      row.platingPrice = Number(material.defaultPrice)
    }
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

const addItem = () => {
  formData.items.push({
    materialId: null, materialName: '', materialCode: '', spec: '',
    processId: null, processName: '', receiptType: '正常', unit: '个', _matOptions: [...defaultMatOptions.value], _matLoading: false,
    plannedQty: 0, platingPrice: 0,
    productionType: '自制', detailRemark: ''
  })
}

const removeItem = (index) => {
  formData.items.splice(index, 1)
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.customerId = null
  pagination.page = 1
  fetchList()
}

const openDialog = async (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑排产单'
    editId.value = row.id
    Object.assign(formData, {
      productionDate: row.productionDate,
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
      const res = await request.get('/production-items', { params: { productionId: row.id } })
      const rawItems = Array.isArray(res) ? res : (res.data || [])
      formData.items = rawItems.map(item => ({ ...item, _matOptions: item.materialId ? [{ id: item.materialId, name: item.materialName, code: item.materialCode, spec: item.spec }] : [], _matLoading: false }))
    } catch (e) {
      formData.items = []
    }
  } else {
    dialogTitle.value = '新增排产单'
    editId.value = null
    formData.productionDate = today
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    productionDate: today, customerId: null, customerName: '', remark: '',
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
      const res = await request.put(`/productions/${editId.value}`, payload)
      ElMessage.success('更新成功')
    } else {
      const res = await request.post('/productions', payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除排产单「${row.productionNo}」？`, '确认', { type: 'warning' })
  await request.delete(`/productions/${row.id}`)
  ElMessage.success('已删除')
  fetchList()
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
    const res = await request.post('/productions/import', formDataObj, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: { mode: 'history' }
    })
    ElMessage.success(`导入完成：成功 ${res.data.success} 条，跳过 ${res.data.skip} 条，失败 ${res.data.fail} 条`)
    showImportDialog.value = false
    fetchList()
  } finally {
    importLoading.value = false
    importFile.value = null
  }
}

const handlePrint = async (row) => {
  try {
    const [detailRes, configRes] = await Promise.all([
      request.get(`/productions/${row.id}`),
      getPrintConfig()
    ])
    const detail = detailRes.data || detailRes
    const config = configRes.data || configRes
    const items = detail.items || []

    const docTitle = config.printTitleProduction || '致恒（致越）金属表面加工厂生产安排表'
    const sig1 = config.printSignature1Label || '生产班长'
    const sig2 = config.printSignature2Label || '仓管'
    const sig3 = config.printSignature3Label || '签名'
    const remark = detail.remark || ''

    const totalQty = items.reduce((sum, item) => sum + (parseFloat(item.plannedQty) || 0), 0)

    // 根据纸张和CSS自动计算每页行数
    // 纸张: 241mm × 120mm，页边距 5mm → 可用高度 110mm
    // 1pt = 0.353mm（印刷分辨率），浏览器默认行高 1.2
    const R = 0.353
    const LH = 1.2
    const titleH  = 13 * R * LH + 4  // 标题行: 13pt × 1.2 + 上下各2mm ≈ 9.5mm
    const metaH   = 9  * R * LH + 2  // 日期行: 9pt × 1.2 + 上下各1mm ≈ 5.8mm
    const hdrH    = 8.5* R * LH + 2.5 // 列标题行: 8.5pt × 1.2 + 1mm+1.5mm ≈ 6.1mm
    const rowH    = hdrH              // 数据行与列标题行同高
    const remarkH = rowH              // 备注行同高
    const sigH    = 7                 // 签名栏 div ≈ 7mm
    const overhead = titleH + metaH + hdrH * 2 + remarkH + sigH + 3 // +3mm 边框余量
    const ROWS_PER_PAGE = Math.max(1, Math.floor((110 - overhead) / rowH))
    // 实际计算约 12 行/页

    // 分割数据
    const chunks = []
    if (items.length === 0) chunks.push([])
    else for (let i = 0; i < items.length; i += ROWS_PER_PAGE) chunks.push(items.slice(i, i + ROWS_PER_PAGE))

    // 空白填充行（11列）—— 用 &nbsp; 保证行高与数据行一致
    const emptyRow = `<tr>${'<td>&nbsp;</td>'.repeat(11)}</tr>`

    const makePage = (chunk, isLast) => {
      const dataRows = chunk.map(item => `<tr>
        <td>${detail.customerName || ''}</td>
        <td>${item.materialName || ''}</td>
        <td>${item.spec || ''}</td>
        <td>${item.processName || ''}</td>
        <td style="text-align:right">${item.plannedQty != null ? item.plannedQty : ''}</td>
        <td style="text-align:center">${item.receiptType || ''}</td>
        <td></td><td></td><td></td><td></td>
        <td>${item.detailRemark || ''}</td>
      </tr>`).join('')

      // 末页留1行给合计，其余页填满
      const fillTarget = isLast ? ROWS_PER_PAGE - 1 : ROWS_PER_PAGE
      const padRows = emptyRow.repeat(Math.max(0, fillTarget - chunk.length))

      const totalRow = isLast ? `<tr>
        <td colspan="4" style="text-align:right;font-weight:bold;">合计</td>
        <td style="text-align:right;font-weight:bold;">${totalQty || ''}</td>
        <td colspan="6"></td>
      </tr>` : ''

      return `<div class="page${isLast ? ' last' : ''}">
        <div class="stamp-side"></div>
        <div class="page-body">
        <table class="pt">
          <thead>
            <tr><th colspan="11" class="title-cell">${docTitle}</th></tr>
            <tr><td colspan="11" class="meta-cell">
              <div class="meta-flex">
                <span>排产日期：${detail.productionDate || ''}</span>
                <span>单号：${detail.productionNo || ''}</span>
                <span>班别：</span>
                <span>客户：${detail.customerName || ''}</span>
              </div>
            </td></tr>
            <tr>
              <th rowspan="2">客户</th><th rowspan="2">品名</th><th rowspan="2">规格</th>
              <th rowspan="2">工艺</th><th rowspan="2">数量</th><th rowspan="2">类型</th>
              <th colspan="4">完成情况</th><th rowspan="2">备注</th>
            </tr>
            <tr><th>1良品</th><th>2良品</th><th>3良品</th><th>不良</th></tr>
          </thead>
          <tbody>${dataRows}${padRows}${totalRow}</tbody>
          <tfoot>
            <tr><td colspan="11" class="foot-remark">备注：${remark}</td></tr>
          </tfoot>
        </table>
        <div class="sig-line">
          <span>${sig3}</span>
          <span>${sig1}</span>
          <span>${sig2}</span>
        </div>
        </div>
        <div class="stamp-side"></div>
      </div>`
    }

    const pages = chunks.map((chunk, i) => makePage(chunk, i === chunks.length - 1)).join('\n')

    const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>排产单 ${detail.productionNo || ''}</title>
<style>
  @page { size: 241mm 120mm; margin: 5mm; }
  * { box-sizing: border-box; }
  body { font-family: SimSun, "宋体", serif; font-size: 9pt; margin: 0; }
  .page { display: flex; align-items: stretch; gap: 2mm; min-height: 108mm; page-break-after: always; }
  .page.last { page-break-after: auto; min-height: 0; }
  .stamp-side { flex: 0 0 10mm; }
  .page-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  .pt { width: 100%; border-collapse: collapse; font-size: 8.5pt; }
  .pt th, .pt td { border: 0.5pt solid #0066CC; padding: 1mm 1.5mm; }
  .pt th { text-align: center; font-weight: bold; background: #f0f6ff; }
  .title-cell { border: none !important; text-align: center; font-size: 13pt; font-weight: bold; padding: 2mm 0 !important; background: white !important; }
  .meta-cell { border: none !important; padding: 1mm 0 !important; background: white !important; font-weight: normal; }
  .meta-flex { display: flex; justify-content: space-around; }
  .meta-flex span { flex: 1; text-align: center; }
  .foot-remark { background: white; }
  .sig-line { display: flex; justify-content: flex-start; font-size: 9pt; margin-top: 1.5mm; padding: 0 2mm; }
  .sig-line span { flex: 1; text-align: left; }
</style></head><body>
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

onMounted(() => {
  fetchList()
  loadCustomers()
  loadProcesses()
})

const exporting = ref(false)
const handleExport = async () => {
  exporting.value = true
  try {
    const res = await exportProductions({ keyword: searchForm.keyword, customerId: searchForm.customerId })
    const url = URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    link.href = url
    link.download = `排产单_${today}.xlsx`
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

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>