<template>
  <div class="material-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="物料名称/代码"
            clearable
            style="width: 200px"
            @keyup.enter="fetchList"
          />
        </el-form-item>
        <el-form-item label="所属客户">
          <el-select
            v-model="searchForm.customerId"
            placeholder="全部客户"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
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
          <span>物料列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增物料</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 230px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialCode" label="物料代码" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="spec" label="规格型号" width="130" />
        <el-table-column prop="customerName" label="所属客户" width="120" />
        <el-table-column prop="defaultPrice" label="默认单价" width="90" align="right">
          <template #default="{ row }">
            {{ row.defaultPrice ? '¥' + Number(row.defaultPrice).toFixed(4) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" style="cursor:pointer" @click="toggleStatus(row)">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料代码" prop="materialCode">
              <el-input v-model="formData.materialCode" placeholder="请输入物料代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="formData.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="规格型号">
              <el-input v-model="formData.spec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属客户">
              <el-select
                v-model="formData.customerId"
                placeholder="请选择客户"
                clearable
                filterable
                style="width:100%"
                @change="onCustomerChange"
              >
                <el-option
                  v-for="c in customerOptions"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计量单位">
              <el-input v-model="formData.unit" placeholder="如：个、米、套" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认单价">
              <el-input-number v-model="formData.defaultPrice" :precision="4" :step="0.01" :min="0" style="width:100%" />
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
import { getMaterialList, createMaterial, updateMaterial, deleteMaterial, updateMaterialStatus } from '@/api/material'
import { getCustomerAll } from '@/api/customer'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增物料')
const formRef = ref(null)
const editId = ref(null)
const customerOptions = ref([])

const searchForm = reactive({ keyword: '', customerId: null })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const formData = reactive({
  materialCode: '', materialName: '', spec: '',
  customerId: null, customerName: '', defaultPrice: 0, unit: '个', status: 1
})

const rules = {
  materialCode: [{ required: true, message: '请输入物料代码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMaterialList({
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined,
      customerId: searchForm.customerId || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.customerId = null
  pagination.page = 1
  fetchList()
}

const onCustomerChange = (val) => {
  const c = customerOptions.value.find(x => x.id === val)
  formData.customerName = c ? c.name : ''
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑物料'
    editId.value = row.id
    Object.assign(formData, row)
  } else {
    dialogTitle.value = '新增物料'
    editId.value = null
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    materialCode: '', materialName: '', spec: '',
    customerId: null, customerName: '', defaultPrice: 0, unit: '个', status: 1
  })
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editId.value) {
      await updateMaterial(editId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createMaterial(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除物料「${row.materialName}」？`, '确认', { type: 'warning' })
  await deleteMaterial(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  await updateMaterialStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用')
}

onMounted(async () => {
  const res = await getCustomerAll()
  customerOptions.value = res.data || []
  fetchList()
})
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.table-scroll-wrap { overflow-x: auto; }



/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>
