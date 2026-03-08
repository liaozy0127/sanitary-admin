<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapsed" class="logo-text">卫浴管理系统</span>
        <span v-else class="logo-icon">卫</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <!-- 系统管理 -->
        <el-sub-menu index="sys">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/user">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/role">
            <el-icon><UserFilled /></el-icon>
            <template #title>角色管理</template>
          </el-menu-item>
          <el-menu-item index="/menu">
            <el-icon><Menu /></el-icon>
            <template #title>菜单管理</template>
          </el-menu-item>
        </el-sub-menu>

        <!-- 基础数据 -->
        <el-sub-menu index="base">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>基础数据</span>
          </template>
          <el-menu-item index="/customer">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>客户管理</template>
          </el-menu-item>
          <el-menu-item index="/process">
            <el-icon><Operation /></el-icon>
            <template #title>工艺管理</template>
          </el-menu-item>
          <el-menu-item index="/material">
            <el-icon><Box /></el-icon>
            <template #title>物料管理</template>
          </el-menu-item>
        </el-sub-menu>

        <!-- 生产管理 -->
        <el-sub-menu index="prod">
          <template #title>
            <el-icon><SetUp /></el-icon>
            <span>生产管理</span>
          </template>
          <el-menu-item index="/receipt">
            <el-icon><Download /></el-icon>
            <template #title>收货管理</template>
          </el-menu-item>
          <el-menu-item index="/production">
            <el-icon><Calendar /></el-icon>
            <template #title>排产管理</template>
          </el-menu-item>
          <el-menu-item index="/shipment">
            <el-icon><Upload /></el-icon>
            <template #title>发货管理</template>
          </el-menu-item>
          <el-menu-item index="/rework">
            <el-icon><RefreshRight /></el-icon>
            <template #title>返工管理</template>
          </el-menu-item>
        </el-sub-menu>

        <!-- 财务管理 -->
        <el-sub-menu index="finance">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>财务管理</span>
          </template>
          <el-menu-item index="/payment">
            <el-icon><Wallet /></el-icon>
            <template #title>收款记录</template>
          </el-menu-item>
          <el-menu-item index="/statement">
            <el-icon><Document /></el-icon>
            <template #title>对账单</template>
          </el-menu-item>
        </el-sub-menu>

        <!-- 库存报表 -->
        <el-sub-menu index="stock">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>库存报表</span>
          </template>
          <el-menu-item index="/inventory">
            <el-icon><Box /></el-icon>
            <template #title>库存查询</template>
          </el-menu-item>
          <el-menu-item index="/report">
            <el-icon><TrendCharts /></el-icon>
            <template #title>月度报表</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- Header -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapsed = !isCollapsed">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" icon="UserFilled" />
              <span class="username">{{ userStore.userInfo?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  User, Fold, Expand, ArrowDown, OfficeBuilding, UserFilled, Menu,
  Setting, Files, Operation, Box, SetUp, Download, Upload, RefreshRight,
  Money, Wallet, Document, DataAnalysis, TrendCharts, Calendar
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)
const activeMenu = computed(() => route.path)

const handleCommand = async (command) => {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).catch(() => null)

    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  overflow: hidden;
  position: relative;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 自定义滚动条样式，与侧边栏背景融合 */
.layout-aside::-webkit-scrollbar {
  width: 4px;
}
.layout-aside::-webkit-scrollbar-thumb {
  background: #4a6080;
  border-radius: 2px;
}
.layout-aside::-webkit-scrollbar-track {
  background: transparent;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #263445;
  overflow: hidden;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.logo-icon {
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}

.layout-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.collapse-btn:hover {
  color: #409EFF;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #606266;
}

.username {
  font-size: 14px;
}

.layout-main {
  background-color: #f0f2f5;
  overflow: auto;
  padding: 20px;
  height: calc(100vh - 60px);
  box-sizing: border-box;
}
</style>
