import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    meta: { requiresAuth: true },
    redirect: '/user',
    children: [
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', requiresAuth: true }
      },
      {
        path: 'role',
        name: 'Role',
        component: () => import('@/views/role/RoleList.vue'),
        meta: { title: '角色管理', requiresAuth: true }
      },
      {
        path: 'menu',
        name: 'Menu',
        component: () => import('@/views/menu/MenuList.vue'),
        meta: { title: '菜单管理', requiresAuth: true }
      },
      {
        path: 'customer',
        name: 'Customer',
        component: () => import('@/views/customer/index.vue'),
        meta: { title: '客户管理', requiresAuth: true }
      },
      {
        path: 'process',
        name: 'Process',
        component: () => import('@/views/process/index.vue'),
        meta: { title: '工艺管理', requiresAuth: true }
      },
      {
        path: 'material',
        name: 'Material',
        component: () => import('@/views/material/index.vue'),
        meta: { title: '物料管理', requiresAuth: true }
      },
      // Phase 2: 生产管理
      {
        path: 'receipt',
        name: 'Receipt',
        component: () => import('@/views/receipt/index.vue'),
        meta: { title: '收货管理', requiresAuth: true }
      },
      {
        path: 'production',
        name: 'Production',
        component: () => import('@/views/production/index.vue'),
        meta: { title: '排产管理', requiresAuth: true }
      },
      {
        path: 'shipment',
        name: 'Shipment',
        component: () => import('@/views/shipment/index.vue'),
        meta: { title: '发货管理', requiresAuth: true }
      },
      {
        path: 'rework',
        name: 'Rework',
        component: () => import('@/views/rework/index.vue'),
        meta: { title: '返工管理', requiresAuth: true }
      },
      // Phase 3: 财务管理
      {
        path: 'payment',
        name: 'Payment',
        component: () => import('@/views/payment/index.vue'),
        meta: { title: '收款记录', requiresAuth: true }
      },
      {
        path: 'statement',
        name: 'Statement',
        component: () => import('@/views/statement/index.vue'),
        meta: { title: '对账单', requiresAuth: true }
      },
      // Phase 4: 库存报表
      {
        path: 'inventory',
        name: 'Inventory',
        component: () => import('@/views/inventory/index.vue'),
        meta: { title: '库存查询', requiresAuth: true }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '月度报表', requiresAuth: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard: redirect to login if not authenticated
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !userStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && userStore.token) {
    next('/')
  } else {
    next()
  }
})

export default router
