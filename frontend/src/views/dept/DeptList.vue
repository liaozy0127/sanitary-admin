<template>
  <div class="dept-page">
    <!-- Search bar -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="部门名称">
          <el-input
            v-model="searchForm.deptName"
            placeholder="请输入部门名称"
            clearable
            style="width: 200px"
            @keyup.enter="fetchDepts"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchDepts">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table card -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>部门列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增部门</el-button>
        </div>
      </template>

      <el-table v-loading="tableLoading" :data="tableData" stripe border>
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="deptName" label="部门名称" min-width="120" />
        <el-table-column prop="deptCode" label="部门编码" min-width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchDepts"
          @size-change="fetchDepts"
        />
      </div>
    </el-card>

    <!-- Add/Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        size="default"
      >
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="form.deptCode" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getDeptList, createDept, updateDept, deleteDept } from '@/api/dept'

const tableLoading = ref(false)
const tableData = ref([])
const submitLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const searchForm = reactive({
  deptName: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const form = reactive({
  id: null,
  deptName: '',
  deptCode: '',
  status: 1
})

const formRules = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { min: 2, max: 50, message: '部门名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  deptCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { min: 2, max: 20, message: '部门编码长度在 2 到 20 个字符', trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => (form.id ? '编辑部门' : '新增部门'))

const fetchDepts = async () => {
  tableLoading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      deptName: searchForm.deptName || undefined
    }
    const res = await getDeptList(params)
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (err) {
    ElMessage.error(err.message || '获取部门列表失败')
  } finally {
    tableLoading.value = false
  }
}

const resetSearch = () => {
  searchForm.deptName = ''
  pagination.page = 1
  fetchDepts()
}

const openDialog = (row = null) => {
  if (row) {
    Object.assign(form, {
      id: row.id,
      deptName: row.deptName,
      deptCode: row.deptCode,
      status: row.status
    })
  }
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    deptName: '',
    deptCode: '',
    status: 1
  })
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await updateDept(form.id, form)
      ElMessage.success('部门更新成功')
    } else {
      await createDept(form)
      ElMessage.success('部门创建成功')
    }
    dialogVisible.value = false
    fetchDepts()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除部门 "${row.deptName}" 吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.page > 1) {
      pagination.page--
    }
    fetchDepts()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchDepts()
})
</script>

<style scoped>
.dept-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>