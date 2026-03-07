<template>
  <div class="customer-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="客户名称/代码"
            clearable
            style="width: 200px"
            @keyup.enter="fetchList"
          />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="searchForm.customerType" placeholder="全部" clearable style="width: 120px">
            <el-option label="现金" value="现金" />
            <el-option label="月结" value="月结" />
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
          <span>客户列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增客户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="calc(100vh - 230px)">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="customerCode" label="客户代码" width="100" />
        <el-table-column prop="customerName" label="客户名称" min-width="150" />
        <el-table-column prop="customerType" label="客户类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.customerType === '现金' ? 'success' : 'warning'" size="small">
              {{ row.customerType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="90" />
        <el-table-column prop="contactPhone" label="联系电话" width="120" />
        <el-table-column prop="salesperson" label="业务员" width="80" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="toggleStatus(row)"
              active-text="启用"
              inactive-text="停用"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
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
      width="800px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户代码" prop="customerCode">
              <el-input v-model="formData.customerCode" placeholder="请输入客户代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input v-model="formData.customerName" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户类型" prop="customerType">
              <el-select v-model="formData.customerType" style="width:100%">
                <el-option label="现金" value="现金" />
                <el-option label="月结" value="月结" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域名称">
              <el-input v-model="formData.areaName" placeholder="请输入区域名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属行业">
              <el-input v-model="formData.industry" placeholder="请输入所属行业" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务员">
              <el-input v-model="formData.salesperson" placeholder="请输入业务员" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="formData.address" placeholder="请输入地址" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 联系信息 -->
        <el-divider content-position="left">联系信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="formData.contactPerson" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电子邮箱">
              <el-input v-model="formData.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 财务信息 -->
        <el-divider content-position="left">财务信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开户银行">
              <el-input v-model="formData.bankName" placeholder="请输入开户银行" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行账号">
              <el-input v-model="formData.bankAccount" placeholder="请输入银行账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税号">
              <el-input v-model="formData.taxNo" placeholder="请输入税号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="财务联系人">
              <el-input v-model="formData.financeContact" placeholder="请输入财务联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="财务联系电话">
              <el-input v-model="formData.financePhone" placeholder="请输入财务联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调价率(%)">
              <el-input-number v-model="formData.priceAdjustRate" :precision="2" :step="0.01" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发货预警天数">
              <el-input-number v-model="formData.shipWarningDays" :min="0" style="width:100%" />
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
import { getCustomerList, createCustomer, updateCustomer, deleteCustomer, updateCustomerStatus } from '@/api/customer'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增客户')
const formRef = ref(null)
const editId = ref(null)

const searchForm = reactive({ keyword: '', customerType: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })

const formData = reactive({
  customerCode: '', customerName: '', areaName: '', customerType: '现金',
  industry: '', address: '', contactPerson: '', contactPhone: '', email: '',
  salesperson: '', bankName: '', bankAccount: '', taxNo: '', financeContact: '',
  financePhone: '', priceAdjustRate: 0, shipWarningDays: 0, remark: '', status: 1
})

const rules = {
  customerCode: [{ required: true, message: '请输入客户代码', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getCustomerList({
      page: pagination.page, size: pagination.size,
      keyword: searchForm.keyword || undefined,
      customerType: searchForm.customerType || undefined
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.customerType = ''
  pagination.page = 1
  fetchList()
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑客户'
    editId.value = row.id
    Object.assign(formData, row)
  } else {
    dialogTitle.value = '新增客户'
    editId.value = null
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    customerCode: '', customerName: '', areaName: '', customerType: '现金',
    industry: '', address: '', contactPerson: '', contactPhone: '', email: '',
    salesperson: '', bankName: '', bankAccount: '', taxNo: '', financeContact: '',
    financePhone: '', priceAdjustRate: 0, shipWarningDays: 0, remark: '', status: 1
  })
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editId.value) {
      await updateCustomer(editId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createCustomer(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除客户「${row.customerName}」？`, '确认', { type: 'warning' })
  await deleteCustomer(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  await updateCustomerStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用')
}

onMounted(fetchList)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }


</style>
