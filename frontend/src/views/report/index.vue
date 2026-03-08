<template>
  <div class="report-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="年份">
          <el-input-number v-model="searchForm.year" :min="2020" :max="2099" :step="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="月份">
          <el-select v-model="searchForm.month" style="width: 100px">
            <el-option v-for="m in 12" :key="m" :label="`${m}月`" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchReport">查询</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>月度汇总报表 — {{ searchForm.year }}年{{ searchForm.month }}月</span>
          <el-tag type="info">数据来源：收货单 + 发货单</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" show-summary :summary-method="getSummaries">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerName" label="客户名称" min-width="160" />
        <el-table-column prop="receiptQty" label="收货数量" width="120" align="right">
          <template #default="{ row }">{{ Number(row.receiptQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="receiptAmount" label="收货金额" width="130" align="right">
          <template #default="{ row }">
            <span style="color: #409EFF;">{{ Number(row.receiptAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="shipmentQty" label="发货数量" width="120" align="right">
          <template #default="{ row }">{{ Number(row.shipmentQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="shipmentAmount" label="发货金额" width="130" align="right">
          <template #default="{ row }">
            <span style="color: #67c23a;">{{ Number(row.shipmentAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="tableData.length === 0 && !loading" class="empty-tip">
        暂无数据，该月份没有收发货记录
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getMonthlyReport } from '@/api/report'

const loading = ref(false)
const tableData = ref([])

const now = new Date()
const searchForm = reactive({ year: now.getFullYear(), month: now.getMonth() + 1 })

const fetchReport = async () => {
  loading.value = true
  try {
    const res = await getMonthlyReport({ year: searchForm.year, month: searchForm.month })
    tableData.value = res.data
  } finally { loading.value = false }
}

const resetSearch = () => {
  searchForm.year = now.getFullYear()
  searchForm.month = now.getMonth() + 1
  fetchReport()
}

const getSummaries = ({ columns, data }) => {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    if (idx === 1) { sums[idx] = ''; return }
    const values = data.map(item => Number(item[col.property]) || 0)
    sums[idx] = values.reduce((a, b) => a + b, 0).toFixed(2)
  })
  return sums
}

onMounted(fetchReport)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.empty-tip { text-align: center; padding: 40px; color: #909399; font-size: 14px; }

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>
