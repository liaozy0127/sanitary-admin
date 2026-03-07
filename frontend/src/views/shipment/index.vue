<template>
  <div class="shipment-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="单号/客户/物料" clearable style="width: 180px" @keyup.enter="fetchList" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 160px" filterable>
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
          <span>发货单列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增发货</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="shipmentNo" label="发货单号" width="160" />
        <el-table-column prop="shipmentDate" label="发货日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="processName" label="工艺" width="100" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right">
          <template #default="{ row }">{{ row.amount ? Number(row.amount).toFixed(2) : '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '作废' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)" :disabled="row.status === 0">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发货日期" prop="shipmentDate">
              <el-date-picker v-model="formData.shipmentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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
            <el-form-item label="物料" prop="materialId">
              <el-select v-model="formData.materialId" placeholder="选择物料" style="width:100%" @change="onMaterialChange" filterable :disabled="!formData.customerId">
                <el-option v-for="m in materialList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="formData.spec" placeholder="自动填入" />
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
            <el-form-item label="数量" prop="quantity">
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
import { getShipmentList, createShipment, updateShipment, deleteShipment } from '@/api/shipment'
import { getCustomerAll } from '@/api/customer'
import { getProcessAll } from '@/api/process'
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

const searchForm = reactive({ keyword: '', customerId: null, dateRange: [] })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const today = new Date().toISOString().split('T')[0]

const formData = reactive({
  shipmentDate: today, customerId: null, customerName: '', materialId: null,
  materialName: '', materialCode: '', spec: '', processId: null, processName: '',
  quantity: 0, unitPrice: 0, amount: '0.00', remark: ''
})

const rules = {
  shipmentDate: [{ required: true, message: '请选择发货日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getShipmentList({
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined, customerId: searchForm.customerId || undefined,
      startDate: searchForm.dateRange?.[0] || undefined, endDate: searchForm.dateRange?.[1] || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
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
  formData.customerName = c?.name || ''; formData.materialId = null; formData.materialName = ''; formData.spec = ''; loadMaterials(id)
}
const onMaterialChange = (id) => {
  const m = materialList.value.find(m => m.id === id)
  if (m) { formData.materialName = m.name; formData.materialCode = m.code || ''; formData.spec = m.spec || ''; if (m.defaultPrice > 0) { formData.unitPrice = Number(m.defaultPrice); calcAmount() } }
}
const onProcessChange = (id) => { const p = processList.value.find(p => p.id === id); formData.processName = p?.name || '' }
const calcAmount = () => { formData.amount = ((Number(formData.quantity) || 0) * (Number(formData.unitPrice) || 0)).toFixed(2) }
const resetSearch = () => { searchForm.keyword = ''; searchForm.customerId = null; searchForm.dateRange = []; pagination.page = 1; fetchList() }
const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑发货单'; editId.value = row.id
    Object.assign(formData, { ...row, shipmentDate: row.shipmentDate, quantity: Number(row.quantity), unitPrice: Number(row.unitPrice), amount: row.amount ? Number(row.amount).toFixed(2) : '0.00' })
    loadMaterials(row.customerId)
  } else { dialogTitle.value = '新增发货单'; editId.value = null; formData.shipmentDate = today }
  dialogVisible.value = true
}
const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, { shipmentDate: today, customerId: null, customerName: '', materialId: null, materialName: '', materialCode: '', spec: '', processId: null, processName: '', quantity: 0, unitPrice: 0, amount: '0.00', remark: '' })
  materialList.value = []
}
const handleSubmit = async () => {
  await formRef.value.validate(); submitLoading.value = true
  try {
    const payload = { ...formData, amount: parseFloat(formData.amount) }
    if (editId.value) { await updateShipment(editId.value, payload); ElMessage.success('更新成功') }
    else { await createShipment(payload); ElMessage.success('新增成功') }
    dialogVisible.value = false; fetchList()
  } finally { submitLoading.value = false }
}
const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定作废发货单「${row.shipmentNo}」？`, '确认', { type: 'warning' })
  await deleteShipment(row.id); ElMessage.success('已作废'); fetchList()
}
onMounted(() => { fetchList(); loadCustomers(); loadProcesses() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
