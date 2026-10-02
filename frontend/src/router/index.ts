import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { flattenNavigation } from './menu'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/status/401',
    name: 'unauthorized',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '401' },
    meta: { public: true, title: '登录已失效' },
  },
  {
    path: '/status/403',
    name: 'forbidden',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '403' },
    meta: { title: '无权访问' },
  },
  {
    path: '/',
    name: 'shell',
    component: () => import('@/layouts/AppShell.vue'),
    children: [
      {
        path: '',
        name: 'workspace',
        component: () => import('@/views/WorkspaceView.vue'),
        meta: { title: '工作台', eyebrow: '总览' },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/WorkspaceView.vue'),
        meta: { title: '工作台', eyebrow: '总览', permission: 'dashboard:view' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心', eyebrow: '账户' },
      },
      {
        path: 'system',
        redirect: '/system/users',
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { title: '用户管理', eyebrow: '系统管理', permission: 'system:user:view' },
      },
      {
        path: 'system/roles',
        name: 'roles',
        component: () => import('@/views/RolesView.vue'),
        meta: { title: '角色管理', eyebrow: '系统管理', permission: 'system:role:view' },
      },
      {
        path: 'system/permissions',
        name: 'permissions',
        component: () => import('@/views/MenusView.vue'),
        meta: { title: '菜单权限', eyebrow: '系统管理', permission: 'system:permission:view' },
      },
      {
        path: 'system/departments',
        name: 'departments',
        component: () => import('@/views/DepartmentsView.vue'),
        meta: { title: '部门管理', eyebrow: '系统管理', permission: 'system:department:view' },
      },
      {
        path: 'files',
        name: 'files',
        component: () => import('@/views/FilesView.vue'),
        meta: { title: '文件管理', eyebrow: '内容管理', permission: 'file:view' },
      },
      {
        path: 'audit/logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogsView.vue'),
        meta: { title: '操作日志', eyebrow: '安全审计', permission: 'audit:log:view' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '404' },
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

const dynamicRouteNames = new Set<string>()

function registerDynamicRoutes() {
  const auth = useAuthStore()
  for (const item of flattenNavigation(auth.navigation)) {
    if (!item.path || item.path === '/' || router.resolve(item.path).name !== 'not-found') continue

    const name = `menu-${item.id}`
    if (dynamicRouteNames.has(name)) continue
    router.addRoute('shell', {
      path: item.path,
      name,
      component: () => import('@/views/ModuleView.vue'),
      props: { title: item.label },
      meta: {
        title: item.label,
        eyebrow: '功能模块',
        permission: item.permission,
      },
    })
    dynamicRouteNames.add(name)
  }
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const authenticated = await auth.restore()

  if (to.meta.public) {
    if (to.name === 'login' && authenticated) {
      return typeof to.query.redirect === 'string' ? to.query.redirect : '/'
    }
    return true
  }

  if (!authenticated) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  registerDynamicRoutes()
  const rematched = router.resolve(to.fullPath)
  if (to.name === 'not-found' && rematched.name !== 'not-found') return to.fullPath

  const required = to.meta.permission
  if (typeof required === 'string' && !auth.hasPermission(required)) {
    return { name: 'forbidden' }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '管理平台'
  document.title = `${title} · 大雁管理平台`
})

export default router
