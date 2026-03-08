<template>
  <div class="statement-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 180px" @change="fetchList" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="月份">
          <el-date-picker v-model="searchForm.statementMonth" type="month" value-format="YYYY-MM"
            placeholder="选择月份" style="width: 160px" @change="fetchList" />
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
          <span>对账单列表</span>
          <div>
            <el-button type="warning" :icon="Upload" @click="showImportDialog = true">历史导入</el-button>
            <el-button type="primary" :icon="Plus" @click="showGenerateDialog = true">生成对账单</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%"
        max-height="calc(100vh - 260px)" row-key="id" @expand-change="onExpandChange">
        <!-- 展开行：物料明细 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-area">
              <el-table :data="row.items || []" border size="small" style="width: 100%"
                v-loading="row._itemsLoading" element-loading-text="加载明细中...">
                <el-table-column prop="materialCode" label="产品代码" width="110" />
                <el-table-column prop="materialName" label="产品名称" min-width="160" />
                <el-table-column prop="processName" label="工艺要求" width="100" />
                <el-table-column prop="prevBalanceQty" label="上月结余" width="90" align="right">
                  <template #default="{ row: item }">{{ fmtQty(item.prevBalanceQty) }}</template>
                </el-table-column>
                <el-table-column prop="receiptQty" label="本月收货" width="90" align="right">
                  <template #default="{ row: item }">{{ fmtQty(item.receiptQty) }}</template>
                </el-table-column>
                <el-table-column prop="shipmentQty" label="本月发货" width="90" align="right">
                  <template #default="{ row: item }">{{ fmtQty(item.shipmentQty) }}</template>
                </el-table-column>
                <el-table-column prop="currBalanceQty" label="本月结余" width="90" align="right">
                  <template #default="{ row: item }">{{ fmtQty(item.currBalanceQty) }}</template>
                </el-table-column>
                <el-table-column prop="unitPrice" label="单价" width="80" align="right">
                  <template #default="{ row: item }">{{ fmtPrice(item.unitPrice) }}</template>
                </el-table-column>
                <el-table-column prop="shipmentAmount" label="发货金额" width="100" align="right">
                  <template #default="{ row: item }">{{ fmtAmt(item.shipmentAmount) }}</template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="120" />
              </el-table>
              <div v-if="row.items && row.items.length" class="items-summary">
                合计：收货 <b>{{ sumField(row.items, 'receiptQty') }}</b> &nbsp;|&nbsp;
                发货 <b>{{ sumField(row.items, 'shipmentQty') }}</b> &nbsp;|&nbsp;
                金额 <b>¥{{ sumField(row.items, 'shipmentAmount') }}</b>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="statementNo" label="对账单号" width="170" />
        <el-table-column prop="statementMonth" label="对账月份" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="收货数量" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.receiptQty) }}</template>
        </el-table-column>
        <el-table-column label="发货数量" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.shipmentQty) }}</template>
        </el-table-column>
        <el-table-column label="发货金额" width="110" align="right">
          <template #default="{ row }">¥{{ fmtAmt(row.shipmentAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '已确认' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleConfirm(row)"
              :disabled="row.status === '已确认'">确认</el-button>
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

    <!-- 生成对账单弹窗 -->
    <el-dialog v-model="showGenerateDialog" title="生成对账单" width="480px">
      <el-form :model="generateForm" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="generateForm.customerId" placeholder="选择客户" style="width:100%" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对账月份" required>
          <el-date-picker v-model="generateForm.statementMonth" type="month" value-format="YYYY-MM"
            style="width:100%" placeholder="选择月份" />
        </el-form-item>
        <el-alert type="info" :closable="false" style="margin-top:8px">
          将自动汇总选定客户在该月份的收发货数据，生成对账单（含物料明细）。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generateLoading" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 历史导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="导入历史对账单" width="520px">
      <el-form :model="importForm" label-width="110px">
        <el-form-item label="对账单文件" required>
          <el-upload drag accept=".xlsx,.xls" :auto-upload="false"
            :on-change="handleFileChange" :limit="1" :file-list="importFileList">
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
            <template #tip><div class="el-upload__tip">支持 .xlsx .xls 格式（老系统对账单格式）</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="importForm.customerId" placeholder="选择客户（Excel无客户列，需手动指定）"
            style="width:100%" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对账月份" required>
          <el-date-picker v-model="importForm.statementMonth" type="month" value-format="YYYY-MM"
            style="width:100%" placeholder="选择月份" />
        </el-form-item>
        <el-form-item label="初始化库存">
          <el-checkbox v-model="importForm.initInventory">
            将上月结余数量导入库存（仅在库存为空时生效）
          </el-checkbox>
        </el-form-item>
        <el-alert v-if="importForm.initInventory" type="warning" :closable="false" style="margin-top:4px" show-icon>
          勾选后将把 Excel「上月结余」列写入库存表（仅首次初始化，已有库存数据时自动跳过）。
        </el-alert>
      </el-form>
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
import { Search, Refresh, Plus, Delete, Upload } from '@element-plus/icons-vue'
import { getStatementList, generateStatement, confirmStatement, deleteStatement } from '@/api/statement'
import { getCustomerAll } from '@/api/customer'
import request from '@/utils/request'

const loading = ref(false)
const generateLoading = ref(false)
const importLoading = ref(false)
const tableData = ref([])
const customerList = ref([])
const showGenerateDialog = ref(false)
const showImportDialog = ref(false)
const importFile = ref(null)
const importFileList = ref([])

const searchForm = reactive({ customerId: null, statementMonth: '' })
const generateForm = reactive({ customerId: null, statementMonth: '' })
const importForm = reactive({ customerId: null, statementMonth: '', initInventory: false })
const pagination = reactive({ page: 1, size: 20, total: 0 })

// ---- 格式化工具 ----
const fmtQty = (v) => (v == null ? '-' : Number(v).toLocaleString())
const fmtPrice = (v) => (v == null ? '-' : Number(v).toFixed(4))
const fmtAmt = (v) => (v == null ? '0.00' : Number(v).toFixed(2))
const sumField = (items, field) => {
  const s = items.reduce((acc, it) => acc + (Number(it[field]) || 0), 0)
  return field === 'shipmentAmount' ? s.toFixed(2) : s.toLocaleString()
}

// ---- 列表 ----
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getStatementList({
      page: pagination.page, size: pagination.size,
      customerId: searchForm.customerId || undefined,
      statementMonth: searchForm.statementMonth || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadCustomers = async () => {
  const res = await getCustomerAll()
  customerList.value = res.data
}

const resetSearch = () => {
  searchForm.customerId = null
  searchForm.statementMonth = ''
  pagination.page = 1
  fetchList()
}

// ---- 展开行自动加载明细 ----
const onExpandChange = async (row, expandedRows) => {
  if (!expandedRows.some(r => r.id === row.id)) return  // 收起时不处理
  if (row.items && row.items.length > 0) return  // 已加载
  row._itemsLoading = true
  try {
    const res = await request.get('/statement-items', { params: { statementId: row.id } })
    row.items = Array.isArray(res) ? res : (res.data || [])
  } catch (e) {
    row.items = []
  } finally {
    row._itemsLoading = false
  }
}

// ---- 生成对账单 ----
const handleGenerate = async () => {
  if (!generateForm.customerId || !generateForm.statementMonth) {
    ElMessage.warning('请选择客户和月份'); return
  }
  generateLoading.value = true
  try {
    await generateStatement({ customerId: generateForm.customerId, statementMonth: generateForm.statementMonth })
    ElMessage.success('对账单生成成功')
    showGenerateDialog.value = false
    generateForm.customerId = null; generateForm.statementMonth = ''
    fetchList()
  } finally { generateLoading.value = false }
}

// ---- 历史导入 ----
const handleFileChange = (file) => {
  importFile.value = file.raw
  importFileList.value = [file]
}

const handleImport = async () => {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  if (!importForm.customerId) { ElMessage.warning('请选择客户'); return }
  if (!importForm.statementMonth) { ElMessage.warning('请选择对账月份'); return }
  importLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    const res = await request.post(
      `/statements/import?customerId=${importForm.customerId}&statementMonth=${importForm.statementMonth}&initInventory=${importForm.initInventory}`,
      fd, { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    const r = res.data
    let msg = `导入完成：对账单 ${r.success} 条，明细 ${r.itemCount || 0} 行`
    if (r.inventoryInit) msg += `，库存初始化 ${r.inventoryCount} 条`
    if (r.inventorySkipped) msg += `（${r.inventorySkipped}）`
    if (r.skip) msg += `，跳过 ${r.skip} 条（已存在）`
    ElMessage.success(msg)
    showImportDialog.value = false
    importFile.value = null
    importFileList.value = []
    importForm.customerId = null; importForm.statementMonth = ''; importForm.initInventory = false
    fetchList()
  } finally { importLoading.value = false }
}

// ---- 确认 / 删除 ----
const handleConfirm = async (row) => {
  await ElMessageBox.confirm(`确定确认对账单「${row.statementNo}」？`, '确认', { type: 'warning' })
  await confirmStatement(row.id)
  ElMessage.success('对账单已确认')
  fetchList()
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除对账单「${row.statementNo}」？`, '确认', { type: 'warning' })
  await deleteStatement(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

onMounted(() => { fetchList(); loadCustomers() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.expand-area { padding: 12px 20px; background: #f5f7fa; }
.items-summary {
  margin-top: 8px;
  padding: 6px 12px;
  background: #e8f4ff;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
}

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>
