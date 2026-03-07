<template>
  <div class="inventory-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 180px" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="searchForm.keyword" placeholder="物料名称/代码" clearable style="width: 180px" @keyup.enter="fetchList" />
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
          <span>库存查询（实时计算）</span>
          <el-tag type="info">库存 = 收货总量 - 发货总量</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="materialCode" label="物料代码" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="receiptQty" label="收货总量" width="100" align="right">
          <template #default="{ row }">{{ Number(row.receiptQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="shipmentQty" label="发货总量" width="100" align="right">
          <template #default="{ row }">{{ Number(row.shipmentQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="currentStock" label="当前库存" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.currentStock) < 0 ? '#f56c6c' : Number(row.currentStock) === 0 ? '#909399' : '#67c23a', fontWeight: 'bold' }">
              {{ Number(row.currentStock || 0).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="lastPrice" label="最后单价" width="110" align="right">
          <template #default="{ row }">{{ Number(row.lastPrice || 0).toFixed(4) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100, 200]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList" @current-change="fetchList" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getInventoryList } from '@/api/inventory'
import { getCustomerAll } from '@/api/customer'

const loading = ref(false)
const tableData = ref([])
const customerList = ref([])

const searchForm = reactive({ customerId: null, keyword: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getInventoryList({ page: pagination.page, size: pagination.size, customerId: searchForm.customerId || undefined, keyword: searchForm.keyword || undefined })
    tableData.value = res.data.records; pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadCustomers = async () => { const res = await getCustomerAll(); customerList.value = res.data }
const resetSearch = () => { searchForm.customerId = null; searchForm.keyword = ''; pagination.page = 1; fetchList() }

onMounted(() => { fetchList(); loadCustomers() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
