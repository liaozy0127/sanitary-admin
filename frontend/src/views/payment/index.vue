<template>
  <div class="payment-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部现金客户" clearable style="width: 180px" @change="fetchList" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="-"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
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
          <span>收款记录</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增收款</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="paymentNo" label="收款单号" width="160" />
        <el-table-column prop="paymentDate" label="收款日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="amount" label="收款金额" width="120" align="right">
          <template #default="{ row }">
            <span style="font-weight: bold; color: #409EFF;">{{ Number(row.amount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="收款方式" width="120" />
        <el-table-column prop="referenceNo" label="参考单号" width="140" />
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="收款日期" prop="paymentDate">
              <el-date-picker v-model="formData.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="formData.customerId" placeholder="选择现金客户" style="width:100%" @change="onCustomerChange" filterable>
                <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收款金额" prop="amount">
              <el-input-number v-model="formData.amount" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收款方式">
              <el-select v-model="formData.paymentMethod" style="width:100%">
                <el-option label="现金" value="现金" />
                <el-option label="银行转账" value="银行转账" />
                <el-option label="微信" value="微信" />
                <el-option label="支付宝" value="支付宝" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考单号">
              <el-input v-model="formData.referenceNo" placeholder="参考单号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
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
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getPaymentList, createPayment, updatePayment, deletePayment } from '@/api/payment'
import { getCustomerAll } from '@/api/customer'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增收款')
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])

const searchForm = reactive({ customerId: null, dateRange: [] })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const today = new Date().toISOString().split('T')[0]

const formData = reactive({
  paymentDate: today, customerId: null, customerName: '', amount: 0,
  paymentMethod: '银行转账', referenceNo: '', remark: ''
})

const rules = {
  paymentDate: [{ required: true, message: '请选择收款日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  amount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getPaymentList({ page: pagination.page, size: pagination.size, customerId: searchForm.customerId || undefined, startDate: searchForm.dateRange?.[0] || undefined, endDate: searchForm.dateRange?.[1] || undefined })
    tableData.value = res.data.records; pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadCustomers = async () => {
  // Only cash customers
  const res = await getCustomerAll({ customerType: '现金' })
  customerList.value = res.data
}

const onCustomerChange = (id) => {
  const c = customerList.value.find(c => c.id === id)
  formData.customerName = c?.name || ''
}

const resetSearch = () => { searchForm.customerId = null; searchForm.dateRange = []; pagination.page = 1; fetchList() }

const openDialog = (row) => {
  resetForm()
  if (row) { dialogTitle.value = '编辑收款'; editId.value = row.id; Object.assign(formData, { ...row, amount: Number(row.amount) }) }
  else { dialogTitle.value = '新增收款'; editId.value = null; formData.paymentDate = today }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, { paymentDate: today, customerId: null, customerName: '', amount: 0, paymentMethod: '银行转账', referenceNo: '', remark: '' })
}

const handleSubmit = async () => {
  await formRef.value.validate(); submitLoading.value = true
  try {
    if (editId.value) { await updatePayment(editId.value, formData); ElMessage.success('更新成功') }
    else { await createPayment(formData); ElMessage.success('新增成功') }
    dialogVisible.value = false; fetchList()
  } finally { submitLoading.value = false }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除收款记录「${row.paymentNo}」？`, '确认', { type: 'warning' })
  await deletePayment(row.id); ElMessage.success('删除成功'); fetchList()
}

onMounted(() => { fetchList(); loadCustomers() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
