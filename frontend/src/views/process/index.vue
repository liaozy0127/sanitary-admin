<template>
  <div class="process-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="工艺名称/代码"
            clearable
            style="width: 200px"
            @keyup.enter="fetchList"
          />
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
          <span>工艺列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增工艺</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 230px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="processCode" label="工艺代码" width="100" />
        <el-table-column prop="processName" label="工艺名称" min-width="120" />
        <el-table-column prop="processCategory" label="工艺类别" width="100" />
        <el-table-column prop="processNature" label="工艺性质" width="100" />
        <el-table-column prop="priorityNo" label="优先编号" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" style="cursor:pointer" @click="toggleStatus(row)">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
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
            <el-form-item label="工艺代码" prop="processCode">
              <el-input v-model="formData.processCode" placeholder="请输入工艺代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺名称" prop="processName">
              <el-input v-model="formData.processName" placeholder="请输入工艺名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺类别">
              <el-input v-model="formData.processCategory" placeholder="请输入工艺类别" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺性质">
              <el-input v-model="formData.processNature" placeholder="请输入工艺性质" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厚度要求">
              <el-input v-model="formData.thicknessReq" placeholder="请输入厚度要求" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先编号">
              <el-input-number v-model="formData.priorityNo" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缺省报价">
              <el-switch v-model="formData.defaultQuote" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
import { getProcessList, createProcess, updateProcess, deleteProcess, updateProcessStatus } from '@/api/process'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增工艺')
const formRef = ref(null)
const editId = ref(null)

const searchForm = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const formData = reactive({
  processCode: '', processName: '', processCategory: '', processNature: '',
  thicknessReq: '', defaultQuote: 0, priorityNo: 0, remark: '', status: 1
})

const rules = {
  processCode: [{ required: true, message: '请输入工艺代码', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入工艺名称', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getProcessList({
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  pagination.page = 1
  fetchList()
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑工艺'
    editId.value = row.id
    Object.assign(formData, row)
  } else {
    dialogTitle.value = '新增工艺'
    editId.value = null
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    processCode: '', processName: '', processCategory: '', processNature: '',
    thicknessReq: '', defaultQuote: 0, priorityNo: 0, remark: '', status: 1
  })
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editId.value) {
      await updateProcess(editId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createProcess(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除工艺「${row.processName}」？`, '确认', { type: 'warning' })
  await deleteProcess(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  await updateProcessStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

onMounted(fetchList)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.table-scroll-wrap { overflow-x: auto; }



/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>
