<template>
  <div class="price-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable filterable style="width: 160px" @change="fetchList">
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="searchForm.materialKeyword" placeholder="物料名称/代码" clearable style="width: 160px" @keyup.enter="fetchList" />
        </el-form-item>
        <el-form-item label="工艺">
          <el-select v-model="searchForm.processId" placeholder="全部工艺" clearable filterable style="width: 140px" @change="fetchList">
            <el-option v-for="p in processList" :key="p.id" :label="p.name" :value="p.id" />
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
          <span>工艺价格列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增价格</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 230px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料代码" width="120" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格型号" width="120" show-overflow-tooltip />
        <el-table-column prop="processName" label="工艺" width="100" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">
            {{ Number(row.unitPrice).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center" fixed="right">
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="客户" prop="customerId">
          <el-select v-model="formData.customerId" placeholder="选择客户" style="width:100%" filterable @change="onCustomerChange">
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="formData.materialId" placeholder="输入物料名称搜索" style="width:100%"
            filterable remote :remote-method="searchMaterial" :loading="matLoading"
            @change="onMaterialChange" :disabled="!formData.customerId">
            <el-option v-for="m in matOptions" :key="m.id"
              :label="m.spec ? m.name + '（' + m.spec + '）' : m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工艺" prop="processId">
          <el-select v-model="formData.processId" placeholder="选择工艺" style="width:100%" filterable @change="onProcessChange">
            <el-option v-for="p in processList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number v-model="formData.unitPrice" :min="0" :precision="2" style="width:100%" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" placeholder="备注" :maxlength="200" />
        </el-form-item>
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
import { getPriceList, createPrice, updatePrice, deletePrice } from '@/api/price'
import { getCustomerAll } from '@/api/customer'
import { getProcessAll } from '@/api/process'
import request from '@/utils/request'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增价格')
const formRef = ref(null)
const editId = ref(null)
const customerList = ref([])
const processList = ref([])
const matOptions = ref([])
const matLoading = ref(false)

const searchForm = reactive({ customerId: null, materialKeyword: '', processId: null })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const formData = reactive({
  customerId: null, customerName: '',
  materialId: null, materialName: '', materialCode: '', spec: '',
  processId: null, processName: '',
  unitPrice: 0, remark: ''
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  processId: [{ required: true, message: '请选择工艺', trigger: 'change' }],
  unitPrice: [{ required: true, message: '请填写单价', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getPriceList({
      page: pagination.page, size: pagination.size,
      customerId: searchForm.customerId || undefined,
      materialKeyword: searchForm.materialKeyword || undefined,
      processId: searchForm.processId || undefined
    })
    const data = res.data || res
    tableData.value = data.records || []
    pagination.total = data.total || 0
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
}

const searchMaterial = async (query) => {
  if (!formData.customerId) return
  matLoading.value = true
  try {
    const res = await request.get('/materials/search', {
      params: { keyword: query ? query.trim() : '', customerId: formData.customerId }
    })
    matOptions.value = Array.isArray(res) ? res : (res.data || [])
  } finally {
    matLoading.value = false
  }
}

const onCustomerChange = async (id) => {
  const customer = customerList.value.find(c => c.id === id)
  formData.customerName = customer?.name || ''
  formData.materialId = null
  formData.materialName = ''
  formData.materialCode = ''
  formData.spec = ''
  matOptions.value = []
  if (id) {
    await searchMaterial('')
  }
}

const onMaterialChange = (id) => {
  const m = matOptions.value.find(m => m.id === id)
  if (m) {
    formData.materialName = m.name
    formData.materialCode = m.code || ''
    formData.spec = m.spec || ''
  }
}

const onProcessChange = (id) => {
  const p = processList.value.find(p => p.id === id)
  formData.processName = p?.name || ''
}

const resetSearch = () => {
  searchForm.customerId = null
  searchForm.materialKeyword = ''
  searchForm.processId = null
  pagination.page = 1
  fetchList()
}

const openDialog = async (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑价格'
    editId.value = row.id
    Object.assign(formData, {
      customerId: row.customerId,
      customerName: row.customerName,
      materialId: row.materialId,
      materialName: row.materialName,
      materialCode: row.materialCode,
      spec: row.spec,
      processId: row.processId,
      processName: row.processName,
      unitPrice: Number(row.unitPrice),
      remark: row.remark || ''
    })
    // 预加载该客户的物料选项（含当前物料）
    if (row.customerId) {
      matOptions.value = [{ id: row.materialId, name: row.materialName, code: row.materialCode, spec: row.spec }]
    }
  } else {
    dialogTitle.value = '新增价格'
    editId.value = null
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    customerId: null, customerName: '',
    materialId: null, materialName: '', materialCode: '', spec: '',
    processId: null, processName: '',
    unitPrice: 0, remark: ''
  })
  matOptions.value = []
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editId.value) {
      await updatePrice(editId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createPrice(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除「${row.customerName} - ${row.materialName} - ${row.processName}」的价格记录？`, '确认', { type: 'warning' })
  await deletePrice(row.id)
  ElMessage.success('已删除')
  fetchList()
}

onMounted(() => {
  fetchList()
  loadCustomers()
  loadProcesses()
})
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
