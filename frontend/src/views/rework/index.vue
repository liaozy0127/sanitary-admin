<template>
  <div class="rework-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="单号/客户/物料" clearable style="width: 180px" @keyup.enter="fetchList" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 160px" @change="fetchList" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.reworkStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完成" value="已完成" />
          </el-select>
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
          <span>返工单列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增返工</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="reworkNo" label="返工单号" width="160" />
        <el-table-column prop="reworkDate" label="返工日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right">
          <template #default="{ row }">{{ row.amount ? Number(row.amount).toFixed(2) : '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="reworkStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.reworkStatus)" size="small">{{ row.reworkStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reworkReason" label="返工原因" min-width="120" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="返工日期" prop="reworkDate">
              <el-date-picker v-model="formData.reworkDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="formData.customerId" placeholder="选择客户" style="width:100%" @change="onCustomerChange" filterable>
                <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料">
              <el-select v-model="formData.materialId" placeholder="选择物料" style="width:100%" @change="onMaterialChange" filterable :disabled="!formData.customerId" clearable>
                <el-option v-for="m in materialList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="formData.materialName" placeholder="物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="formData.spec" placeholder="规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺">
              <el-select v-model="formData.processId" placeholder="选择工艺" style="width:100%" clearable filterable @change="onProcessChange">
                <el-option v-for="p in processList" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="返工数量" prop="quantity">
              <el-input-number v-model="formData.quantity" :min="0" :precision="2" style="width:100%" @change="calcAmount" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价">
              <el-input-number v-model="formData.unitPrice" :min="0" :precision="4" style="width:100%" @change="calcAmount" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额">
              <el-input v-model="formData.amount" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="formData.reworkStatus" style="width:100%">
                <el-option label="待处理" value="待处理" />
                <el-option label="处理中" value="处理中" />
                <el-option label="已完成" value="已完成" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="返工原因">
              <el-input v-model="formData.reworkReason" type="textarea" :rows="2" placeholder="请输入返工原因" />
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
import { getReworkList, createRework, updateRework, deleteRework } from '@/api/rework'
import { getCustomerAll } from '@/api/customer'
import { getProcessAll } from '@/api/process'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增返工单')
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])
const materialList = ref([])
const processList = ref([])

const searchForm = reactive({ keyword: '', customerId: null, reworkStatus: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const today = new Date().toISOString().split('T')[0]

const formData = reactive({
  reworkDate: today, customerId: null, customerName: '', materialId: null,
  materialName: '', materialCode: '', spec: '', processId: null, processName: '',
  quantity: 0, unitPrice: 0, amount: '0.00', reworkReason: '', reworkStatus: '待处理'
})

const rules = {
  reworkDate: [{ required: true, message: '请选择返工日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

const statusType = (s) => ({ '待处理': '', '处理中': 'primary', '已完成': 'success' }[s] || '')

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getReworkList({ page: pagination.page, size: pagination.size, keyword: searchForm.keyword || undefined, customerId: searchForm.customerId || undefined, reworkStatus: searchForm.reworkStatus || undefined })
    tableData.value = res.data.records; pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadCustomers = async () => { const res = await getCustomerAll(); customerList.value = res.data }
const loadProcesses = async () => { const res = await getProcessAll(); processList.value = res.data }
const loadMaterials = async (customerId) => {
  if (!customerId) { materialList.value = []; return }
  const res = await request.get('/materials/search', { params: { customerId } })
  materialList.value = res.data
}
const onCustomerChange = (id) => {
  const c = customerList.value.find(c => c.id === id)
  formData.customerName = c?.name || ''; formData.materialId = null; loadMaterials(id)
}
const onMaterialChange = (id) => {
  const m = materialList.value.find(m => m.id === id)
  if (m) { formData.materialName = m.name; formData.spec = m.spec || '' }
}
const onProcessChange = (id) => { const p = processList.value.find(p => p.id === id); formData.processName = p?.name || '' }
const calcAmount = () => { formData.amount = ((Number(formData.quantity) || 0) * (Number(formData.unitPrice) || 0)).toFixed(2) }
const resetSearch = () => { searchForm.keyword = ''; searchForm.customerId = null; searchForm.reworkStatus = ''; pagination.page = 1; fetchList() }
const openDialog = (row) => {
  resetForm()
  if (row) { dialogTitle.value = '编辑返工单'; editId.value = row.id; Object.assign(formData, { ...row, quantity: Number(row.quantity), unitPrice: Number(row.unitPrice || 0), amount: row.amount ? Number(row.amount).toFixed(2) : '0.00' }); loadMaterials(row.customerId) }
  else { dialogTitle.value = '新增返工单'; editId.value = null; formData.reworkDate = today }
  dialogVisible.value = true
}
const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, { reworkDate: today, customerId: null, customerName: '', materialId: null, materialName: '', materialCode: '', spec: '', processId: null, processName: '', quantity: 0, unitPrice: 0, amount: '0.00', reworkReason: '', reworkStatus: '待处理' })
  materialList.value = []
}
const handleSubmit = async () => {
  await formRef.value.validate(); submitLoading.value = true
  try {
    const payload = { ...formData, amount: parseFloat(formData.amount) }
    if (editId.value) { await updateRework(editId.value, payload); ElMessage.success('更新成功') }
    else { await createRework(payload); ElMessage.success('新增成功') }
    dialogVisible.value = false; fetchList()
  } finally { submitLoading.value = false }
}
const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除返工单「${row.reworkNo}」？`, '确认', { type: 'warning' })
  await deleteRework(row.id); ElMessage.success('删除成功'); fetchList()
}
onMounted(() => { fetchList(); loadCustomers(); loadProcesses() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
