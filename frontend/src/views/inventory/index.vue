<template>
  <div class="inventory-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部客户" clearable style="width: 180px" filterable>
            <el-option v-for="c in customerList" :key="c.id" :label="c.customerName" :value="c.id" />
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

    <!-- 库存列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>库存查询</span>
          <div>
            <el-tag type="success" style="margin-right:8px">持久化库存，收发货实时更新</el-tag>
            <el-button size="small" :icon="Document" @click="showLog = !showLog">
              {{ showLog ? '隐藏流水' : '查看流水' }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 300px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="materialCode" label="物料代码" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="processName" label="工艺" width="100" />
        <el-table-column prop="quantity" label="当前库存" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.quantity) < 0 ? '#f56c6c' : Number(row.quantity) === 0 ? '#909399' : '#67c23a', fontWeight: 'bold' }">
              {{ Number(row.quantity || 0).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="lastReceiveTime" label="最后收货" width="160" />
        <el-table-column prop="lastShipTime" label="最后发货" width="160" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100, 200]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList" @current-change="fetchList" />
      </div>
    </el-card>

    <!-- 库存流水 -->
    <el-card v-if="showLog" class="table-card" style="margin-top:16px">
      <template #header>
        <div class="table-header">
          <span>库存变动流水</span>
          <div>
            <el-select v-model="logFilter.changeType" placeholder="全部类型" clearable style="width:120px;margin-right:8px" @change="fetchLog">
              <el-option label="收货入库" :value="1" />
              <el-option label="发货出库" :value="2" />
              <el-option label="返工入库" :value="3" />
            </el-select>
            <el-button type="primary" size="small" :icon="Search" @click="fetchLog">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="logLoading" :data="logData" stripe border style="width: 100%" max-height="400px">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerName" label="客户" width="130" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="processName" label="工艺" width="100" />
        <el-table-column prop="changeType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.changeType === 1 ? 'success' : row.changeType === 2 ? 'danger' : 'warning'" size="small">
              {{ row.changeType === 1 ? '收货入库' : row.changeType === 2 ? '发货出库' : '返工入库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeQty" label="变动数量" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.changeQty) >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ Number(row.changeQty) >= 0 ? '+' : '' }}{{ Number(row.changeQty || 0).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="beforeQty" label="变动前" width="90" align="right">
          <template #default="{ row }">{{ Number(row.beforeQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="afterQty" label="变动后" width="90" align="right">
          <template #default="{ row }">{{ Number(row.afterQty || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="关联单号" width="160" />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="logPagination.page" v-model:page-size="logPagination.size"
          :page-sizes="[20, 50, 100]" :total="logPagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchLog" @current-change="fetchLog" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Document } from '@element-plus/icons-vue'
import { getInventoryList, getInventoryLog } from '@/api/inventory'
import { getCustomerAll } from '@/api/customer'

const loading = ref(false)
const tableData = ref([])
const customerList = ref([])
const showLog = ref(false)
const logLoading = ref(false)
const logData = ref([])

const searchForm = reactive({ customerId: null, keyword: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const logFilter = reactive({ changeType: null })
const logPagination = reactive({ page: 1, size: 20, total: 0 })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getInventoryList({
      page: pagination.page,
      size: pagination.size,
      customerId: searchForm.customerId || undefined,
      keyword: searchForm.keyword || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchLog = async () => {
  logLoading.value = true
  try {
    const res = await getInventoryLog({
      page: logPagination.page,
      size: logPagination.size,
      changeType: logFilter.changeType || undefined
    })
    logData.value = res.data.records
    logPagination.total = res.data.total
  } finally {
    logLoading.value = false
  }
}

const loadCustomers = async () => {
  const res = await getCustomerAll()
  customerList.value = res.data || []
}

const resetSearch = () => {
  searchForm.customerId = null
  searchForm.keyword = ''
  pagination.page = 1
  fetchList()
}

onMounted(() => {
  fetchList()
  loadCustomers()
})
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
