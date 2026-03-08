<template>
  <div class="menu-page">
    <!-- Search bar -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="菜单名称">
          <el-input
            v-model="searchForm.menuName"
            placeholder="请输入菜单名称"
            clearable
            style="width: 200px"
            @keyup.enter="fetchMenus"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchMenus">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table card -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>菜单列表</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增菜单</el-button>
        </div>
      </template>

      <el-table 
        v-loading="tableLoading" 
        :data="tableData" 
        stripe 
        border
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="150" />
        <el-table-column prop="menuPath" label="路径" min-width="150" />
        <el-table-column prop="menuIcon" label="图标" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.menuIcon">{{ row.menuIcon }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 1 ? 'primary' : row.menuType === 2 ? 'success' : 'warning'" size="small">
              {{ row.menuType === 1 ? '目录' : row.menuType === 2 ? '菜单' : '按钮' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="245" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button type="success" link :icon="Plus" @click="addChildDialog(row)">添加子菜单</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

    </el-card>

    <!-- Add/Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
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
        <el-form-item label="父级菜单" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="menuOptions"
            placeholder="请选择父级菜单"
            clearable
            filterable
            node-key="id"
            :props="{ value: 'id', label: 'menuName', disabled: 'disabled' }"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="路径" prop="menuPath">
          <el-input v-model="form.menuPath" placeholder="请输入路径，如 /system/user" />
        </el-form-item>
        <el-form-item label="图标" prop="menuIcon">
          <el-input v-model="form.menuIcon" placeholder="请输入图标名" />
        </el-form-item>
        <el-form-item label="类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio :label="1">目录</el-radio>
            <el-radio :label="2">菜单</el-radio>
            <el-radio :label="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
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
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/menu'

const tableLoading = ref(false)
const tableData = ref([])
const menuOptions = ref([]) // 用于父级菜单选择
const submitLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const searchForm = reactive({
  menuName: ''
})

const form = reactive({
  id: null,
  parentId: null,
  menuName: '',
  menuPath: '',
  menuIcon: '',
  menuType: 2,
  sort: 0,
  status: 1
})

const formRules = {
  menuName: [
    { required: true, message: '请输入菜单名称', trigger: 'blur' },
    { min: 2, max: 50, message: '菜单名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  menuPath: [
    { required: true, message: '请输入路径', trigger: 'blur' },
    { min: 1, max: 200, message: '路径长度在 1 到 200 个字符', trigger: 'blur' }
  ],
  menuType: [
    { required: true, message: '请选择菜单类型', trigger: 'change' }
  ]
}

const dialogTitle = computed(() => (form.id ? '编辑菜单' : (form.parentId ? '新增子菜单' : '新增菜单')))

const fetchMenus = async () => {
  tableLoading.value = true
  try {
    const params = {
      menuName: searchForm.menuName || undefined
    }
    const res = await getMenuTree(params)
    tableData.value = res.data || []
    // 为父级菜单选择器准备数据（排除自身及子菜单）
    menuOptions.value = buildMenuOptions(res.data || [])
  } catch (err) {
    ElMessage.error(err.message || '获取菜单列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 构建菜单选项树，用于父级菜单选择
const buildMenuOptions = (menus, level = 0) => {
  let result = []
  menus.forEach(menu => {
    const item = { ...menu }
    item.disabled = false // 可以选择父级菜单
    if (menu.children && menu.children.length > 0) {
      item.children = buildMenuOptions(menu.children, level + 1)
    }
    result.push(item)
  })
  return result
}

const resetSearch = () => {
  searchForm.menuName = ''
  fetchMenus()
}

const openDialog = (row = null) => {
  if (row) {
    // 如果是编辑，加载数据
    Object.assign(form, {
      id: row.id,
      parentId: row.parentId,
      menuName: row.menuName,
      menuPath: row.menuPath,
      menuIcon: row.menuIcon,
      menuType: row.menuType,
      sort: row.sort,
      status: row.status
    })
  } else {
    // 新增顶级菜单
    resetForm()
  }
  dialogVisible.value = true
}

const addChildDialog = (row) => {
  resetForm()
  form.parentId = row.id
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    parentId: null,
    menuName: '',
    menuPath: '',
    menuIcon: '',
    menuType: 2,
    sort: 0,
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
      await updateMenu(form.id, form)
      ElMessage.success('菜单更新成功')
    } else {
      await createMenu(form)
      ElMessage.success('菜单创建成功')
    }
    dialogVisible.value = false
    fetchMenus()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除菜单 "${row.menuName}" 吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchMenus()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchMenus()
})
</script>

<style scoped>
.menu-page {
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

/* 操作列按钮并排 */
:deep(.el-table .cell) { white-space: nowrap; }
</style>