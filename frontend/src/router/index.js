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
        path: 'dept',
        name: 'Dept',
        component: () => import('@/views/dept/DeptList.vue'),
        meta: { title: '部门管理', requiresAuth: true }
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
        path: 'log',
        name: 'Log',
        component: () => import('@/views/log/LogList.vue'),
        meta: { title: '操作日志', requiresAuth: true }
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
