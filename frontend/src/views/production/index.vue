<template>
  <div class="production-page">
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
        <el-form-item label="状态">
          <el-select v-model="searchForm.prodStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="待生产" value="待生产" />
            <el-option label="生产中" value="生产中" />
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
          <span>排产单列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增排产</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productionNo" label="排产单号" width="160" />
        <el-table-column prop="productionDate" label="排产日期" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column prop="processName" label="工艺" width="100" />
        <el-table-column prop="plannedQty" label="计划数量" width="100" align="right" />
        <el-table-column prop="actualQty" label="实际数量" width="100" align="right" />
        <el-table-column prop="prodStatus" label="生产状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.prodStatus)" size="small">{{ row.prodStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-select size="small" :model-value="row.prodStatus" style="width: 90px; margin-left: 4px"
              @change="(val) => updateStatus(row, val)">
              <el-option label="待生产" value="待生产" />
              <el-option label="生产中" value="生产中" />
              <el-option label="已完成" value="已完成" />
            </el-select>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排产日期" prop="productionDate">
              <el-date-picker v-model="formData.productionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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
            <el-form-item label="物料" prop="materialName">
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
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="formData.plannedQty" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际数量">
              <el-input-number v-model="formData.actualQty" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价">
              <el-input-number v-model="formData.unitPrice" :min="0" :precision="4" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产状态">
              <el-select v-model="formData.prodStatus" style="width:100%">
                <el-option label="待生产" value="待生产" />
                <el-option label="生产中" value="生产中" />
                <el-option label="已完成" value="已完成" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注" />
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
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { getProductionList, createProduction, updateProduction, deleteProduction, updateProductionStatus } from '@/api/production'
import { getCustomerAll } from '@/api/customer'
import { getProcessAll } from '@/api/process'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增排产单')
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])
const materialList = ref([])
const processList = ref([])

const searchForm = reactive({ keyword: '', customerId: null, prodStatus: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const today = new Date().toISOString().split('T')[0]

const formData = reactive({
  productionDate: today, customerId: null, customerName: '', materialId: null,
  materialName: '', materialCode: '', spec: '', processId: null, processName: '',
  plannedQty: 0, actualQty: 0, unitPrice: 0, amount: 0, prodStatus: '待生产', remark: ''
})

const rules = {
  productionDate: [{ required: true, message: '请选择排产日期', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}

const statusType = (s) => {
  if (s === '待生产') return ''
  if (s === '生产中') return 'primary'
  if (s === '已完成') return 'success'
  return ''
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined,
      customerId: searchForm.customerId || undefined,
      prodStatus: searchForm.prodStatus || undefined
    }
    const res = await getProductionList(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
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
  processList.value = res.data
}

const loadMaterials = async (customerId) => {
  if (!customerId) { materialList.value = []; return }
  const res = await request.get('/materials/search', { params: { customerId } })
  materialList.value = res.data
}

const onCustomerChange = (id) => {
  const customer = customerList.value.find(c => c.id === id)
  formData.customerName = customer?.name || ''
  formData.materialId = null
  loadMaterials(id)
}

const onMaterialChange = (id) => {
  const material = materialList.value.find(m => m.id === id)
  if (material) {
    formData.materialName = material.name
    formData.materialCode = material.code || ''
    formData.spec = material.spec || ''
    if (material.defaultPrice > 0) formData.unitPrice = Number(material.defaultPrice)
  }
}

const onProcessChange = (id) => {
  const process = processList.value.find(p => p.id === id)
  formData.processName = process?.name || ''
}

const resetSearch = () => {
  searchForm.keyword = ''; searchForm.customerId = null; searchForm.prodStatus = ''
  pagination.page = 1; fetchList()
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑排产单'; editId.value = row.id
    Object.assign(formData, { ...row, plannedQty: Number(row.plannedQty), actualQty: Number(row.actualQty || 0), unitPrice: Number(row.unitPrice || 0) })
    loadMaterials(row.customerId)
  } else {
    dialogTitle.value = '新增排产单'; editId.value = null; formData.productionDate = today
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, { productionDate: today, customerId: null, customerName: '', materialId: null, materialName: '', materialCode: '', spec: '', processId: null, processName: '', plannedQty: 0, actualQty: 0, unitPrice: 0, amount: 0, prodStatus: '待生产', remark: '' })
  materialList.value = []
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editId.value) {
      await updateProduction(editId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createProduction(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchList()
  } finally {
    submitLoading.value = false
  }
}

const updateStatus = async (row, val) => {
  await updateProductionStatus(row.id, val)
  row.prodStatus = val
  ElMessage.success('状态已更新')
}

onMounted(() => { fetchList(); loadCustomers(); loadProcesses() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
