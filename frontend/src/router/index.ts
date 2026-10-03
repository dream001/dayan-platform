import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { flattenNavigation } from './menu'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, titleKey: 'login.title' },
  },
  {
    path: '/status/401',
    name: 'unauthorized',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '401' },
    meta: { public: true, titleKey: 'status.401.title' },
  },
  {
    path: '/status/403',
    name: 'forbidden',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '403' },
    meta: { titleKey: 'status.403.title' },
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
        meta: { titleKey: 'workspace.title', eyebrowKey: 'shell.eyebrows.overview' },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/WorkspaceView.vue'),
        meta: {
          titleKey: 'workspace.title',
          eyebrowKey: 'shell.eyebrows.overview',
          permission: 'dashboard:view',
        },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { titleKey: 'profile.title', eyebrowKey: 'shell.eyebrows.account' },
      },
      {
        path: 'data/manage',
        name: 'dataset',
        component: () => import('@/views/DatasetView.vue'),
        meta: {
          titleKey: 'dataset.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:manage:view',
        },
      },
      {
        path: 'data/upload',
        name: 'data-upload',
        component: () => import('@/views/DataUploadView.vue'),
        meta: {
          titleKey: 'menu.data:upload:view',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:upload:view',
        },
      },
      {
        path: 'data/quality-check',
        alias: ['/qc/rules', '/qc/logs'],
        name: 'quality-control',
        component: () => import('@/views/QualityControlView.vue'),
        meta: {
          titleKey: 'qualityControl.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:qc:view',
        },
      },
      {
        path: 'data/annotate-tasks',
        alias: '/tasks',
        name: 'annotation-tasks',
        component: () => import('@/views/AnnotationTasksView.vue'),
        meta: {
          titleKey: 'annotationTasks.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:annotate:task:view',
        },
      },
      {
        path: 'data/dictionary',
        name: 'dictionaries',
        component: () => import('@/views/DictionariesView.vue'),
        meta: {
          titleKey: 'dictionaries.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:dict:view',
        },
      },
      {
        path: 'data/annotate-tasks/new',
        alias: '/tasks/new',
        name: 'annotation-task-create',
        component: () => import('@/views/AnnotationTaskCreateView.vue'),
        meta: {
          titleKey: 'annotationTasks.create',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:annotate:task:manage',
        },
      },
      {
        path: 'data/annotate-tasks/:id',
        alias: '/tasks/:id',
        name: 'annotation-task-detail',
        component: () => import('@/views/AnnotationTaskDetailView.vue'),
        meta: {
          titleKey: 'annotationTasks.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:annotate:task:view',
        },
      },
      {
        path: 'data/annotate-tasks/:id/:tab',
        alias: '/tasks/:id/:tab',
        name: 'annotation-task-detail-tab',
        component: () => import('@/views/AnnotationTaskDetailView.vue'),
        meta: {
          titleKey: 'annotationTasks.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:annotate:task:view',
        },
      },
      {
        path: 'data/collect-tasks',
        name: 'collection-tasks',
        component: () => import('@/views/CollectionTasksView.vue'),
        meta: {
          titleKey: 'collections.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:collect:task:view',
        },
      },
      {
        path: 'data/collect-tasks/:id',
        name: 'collection-task-detail',
        component: () => import('@/views/CollectionTasksView.vue'),
        meta: {
          titleKey: 'collections.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:collect:task:view',
        },
      },
      {
        path: 'data/charts',
        alias: '/charts',
        redirect: (to) => ({
          path: '/data/charts/subtree',
          query: to.query,
        }),
      },
      {
        path: 'data/charts/:chart',
        alias: '/charts/:chart',
        name: 'analysis-charts',
        component: () => import('@/views/ChartsView.vue'),
        meta: {
          titleKey: 'charts.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:chart:view',
        },
      },
      {
        path: 'data/export',
        name: 'data-export',
        component: () => import('@/views/DataExportView.vue'),
        meta: {
          titleKey: 'dataExport.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'data:export:view',
        },
      },
      {
        path: 'system',
        redirect: '/system/users',
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: {
          titleKey: 'users.title',
          eyebrowKey: 'shell.eyebrows.system',
          permission: 'system:user:view',
        },
      },
      {
        path: 'system/roles',
        name: 'roles',
        component: () => import('@/views/RolesView.vue'),
        meta: {
          titleKey: 'roles.title',
          eyebrowKey: 'shell.eyebrows.system',
          permission: 'system:role:view',
        },
      },
      {
        path: 'system/permissions',
        name: 'permissions',
        component: () => import('@/views/MenusView.vue'),
        meta: {
          titleKey: 'menus.title',
          eyebrowKey: 'shell.eyebrows.system',
          permission: 'system:permission:view',
        },
      },
      {
        path: 'system/departments',
        name: 'departments',
        component: () => import('@/views/DepartmentsView.vue'),
        meta: {
          titleKey: 'departments.title',
          eyebrowKey: 'shell.eyebrows.system',
          permission: 'system:department:view',
        },
      },
      {
        path: 'files',
        name: 'files',
        component: () => import('@/views/FilesView.vue'),
        meta: {
          titleKey: 'files.title',
          eyebrowKey: 'shell.eyebrows.content',
          permission: 'file:view',
        },
      },
      {
        path: 'audit/logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogsView.vue'),
        meta: {
          titleKey: 'audit.title',
          eyebrowKey: 'shell.eyebrows.security',
          permission: 'audit:log:view',
        },
      },
      {
        path: 'basic/projects',
        name: 'projects',
        component: () => import('@/views/ProjectsView.vue'),
        meta: {
          titleKey: 'projects.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'basic:project:view',
        },
      },
      {
        path: 'basic/robots',
        name: 'robots',
        component: () => import('@/views/RobotsView.vue'),
        meta: {
          titleKey: 'robots.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'basic:robot:view',
        },
      },
      {
        path: 'basic/storage',
        name: 'storage-management',
        component: () => import('@/views/StorageManagementView.vue'),
        meta: {
          titleKey: 'storages.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'basic:storage:view',
        },
      },
      {
        path: 'basic/models',
        name: 'ai-models',
        component: () => import('@/views/AiModelsView.vue'),
        meta: {
          titleKey: 'aiModels.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'basic:model:view',
        },
      },
      {
        path: 'basic/agents',
        name: 'ai-agents',
        component: () => import('@/views/AiAgentsView.vue'),
        meta: {
          titleKey: 'aiAgents.title',
          eyebrowKey: 'shell.eyebrows.module',
          permission: 'basic:agent:view',
        },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/StatusView.vue'),
    props: { status: '404' },
    meta: { titleKey: 'status.404.title' },
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
      props: { title: item.label, code: item.code },
      meta: {
        titleKey: item.code ? `menu.${item.code}` : undefined,
        title: item.code ? undefined : item.label,
        eyebrowKey: 'shell.eyebrows.module',
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
  const title = typeof to.meta.titleKey === 'string'
    ? i18n.global.t(to.meta.titleKey)
    : i18n.global.t('app.platform')
  document.title = `${title} · ${i18n.global.t('app.name')}`
})

export default router
