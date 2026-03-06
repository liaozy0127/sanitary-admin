<template>
  <div class="log-page">
    <!-- Search bar -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="操作用户">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入操作用户"
            clearable
            style="width: 200px"
            @keyup.enter="fetchLogs"
          />
        </el-form-item>
        <el-form-item label="操作描述">
          <el-input
            v-model="searchForm.description"
            placeholder="请输入操作描述"
            clearable
            style="width: 200px"
            @keyup.enter="fetchLogs"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchLogs">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table card -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>操作日志</span>
        </div>
      </template>

      <el-table v-loading="tableLoading" :data="tableData" stripe border>
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作用户" min-width="120" />
        <el-table-column prop="description" label="操作描述" min-width="200" />
        <el-table-column prop="ip" label="IP地址" width="140" />
        <el-table-column prop="timeCost" label="耗时(ms)" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
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
          @current-change="fetchLogs"
          @size-change="fetchLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { getLogList, deleteLog } from '@/api/log'

const tableLoading = ref(false)
const tableData = ref([])

const searchForm = reactive({
  username: '',
  description: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const fetchLogs = async () => {
  tableLoading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      description: searchForm.description || undefined
    }
    const res = await getLogList(params)
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (err) {
    ElMessage.error(err.message || '获取日志列表失败')
  } finally {
    tableLoading.value = false
  }
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.description = ''
  pagination.page = 1
  fetchLogs()
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除日志 "${row.description}" 吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteLog(row.id)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.page > 1) {
      pagination.page--
    }
    fetchLogs()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.log-page {
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