<template>
  <div class="statement-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部月结客户" clearable style="width: 180px" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="月份">
          <el-date-picker v-model="searchForm.statementMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>对账单列表</span>
          <el-button type="primary" :icon="Plus" @click="showGenerateDialog = true">生成对账单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="statementNo" label="对账单号" width="160" />
        <el-table-column prop="statementMonth" label="对账月份" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="receiptQty" label="收货数量" width="100" align="right" />
        <el-table-column prop="receiptAmount" label="收货金额" width="110" align="right">
          <template #default="{ row }">{{ Number(row.receiptAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="shipmentQty" label="发货数量" width="100" align="right" />
        <el-table-column prop="shipmentAmount" label="发货金额" width="110" align="right">
          <template #default="{ row }">{{ Number(row.shipmentAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '已确认' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleConfirm(row)" :disabled="row.status === '已确认'">确认</el-button>
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
    <el-dialog v-model="showGenerateDialog" title="生成对账单" width="500px">
      <el-form :model="generateForm" label-width="90px">
        <el-form-item label="月结客户" required>
          <el-select v-model="generateForm.customerId" placeholder="选择客户" style="width:100%" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对账月份" required>
          <el-date-picker v-model="generateForm.statementMonth" type="month" value-format="YYYY-MM" style="width:100%" placeholder="选择月份" />
        </el-form-item>
        <el-alert type="info" :closable="false" style="margin-top: 8px">
          <span>将自动汇总选定客户在该月份的收发货数据，生成或更新对账单。</span>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generateLoading" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { getStatementList, generateStatement, confirmStatement, deleteStatement } from '@/api/statement'
import { getCustomerAll } from '@/api/customer'

const loading = ref(false)
const generateLoading = ref(false)
const tableData = ref([])
const showGenerateDialog = ref(false)
const customerList = ref([])

const searchForm = reactive({ customerId: null, statementMonth: '' })
const generateForm = reactive({ customerId: null, statementMonth: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getStatementList({ page: pagination.page, size: pagination.size, customerId: searchForm.customerId || undefined, statementMonth: searchForm.statementMonth || undefined })
    tableData.value = res.data.records; pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadCustomers = async () => {
  // Only monthly customers
  const res = await getCustomerAll({ customerType: '月结' })
  customerList.value = res.data
}

const resetSearch = () => { searchForm.customerId = null; searchForm.statementMonth = ''; pagination.page = 1; fetchList() }

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

const handleConfirm = async (row) => {
  await ElMessageBox.confirm(`确定确认对账单「${row.statementNo}」？`, '确认', { type: 'warning' })
  await confirmStatement(row.id)
  ElMessage.success('对账单已确认')
  fetchList()
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除对账单「${row.statementNo}」？`, '确认', { type: 'warning' })
  await deleteStatement(row.id); ElMessage.success('删除成功'); fetchList()
}

onMounted(() => { fetchList(); loadCustomers() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
