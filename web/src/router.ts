import { createRouter, createWebHistory } from 'vue-router'
import { loadSession, session } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, shell: 'none', portal: 'path' }
    },
    {
      path: '/',
      name: 'pending',
      component: () => import('@/views/PendingView.vue'),
      meta: { shell: 'path', role: 'PATH_ADMIN' }
    },
    {
      path: '/lookup',
      name: 'lookup',
      component: () => import('@/views/LookupOrderView.vue'),
      meta: { shell: 'path', role: 'PATH_ADMIN' }
    },
    {
      path: '/postmen',
      name: 'postmen',
      component: () => import('@/views/PostmenView.vue'),
      meta: { shell: 'path', role: 'PATH_ADMIN' }
    },
    {
      path: '/user',
      name: 'user-home',
      component: () => import('@/views/UserHomeView.vue'),
      meta: { public: true, shell: 'user' }
    },
    {
      path: '/user/orders/:orderNo',
      name: 'user-order',
      component: () => import('@/views/UserOrderView.vue'),
      meta: { public: true, shell: 'user' }
    },
    {
      path: '/station/login',
      name: 'station-login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, shell: 'none', portal: 'station' }
    },
    {
      path: '/station',
      name: 'station-inbox',
      component: () => import('@/views/StationInboxView.vue'),
      meta: { shell: 'station', role: 'STATION_ADMIN' }
    },
    {
      path: '/courier/login',
      name: 'courier-login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, shell: 'none', portal: 'courier' }
    },
    {
      path: '/courier',
      name: 'courier-tasks',
      component: () => import('@/views/CourierTasksView.vue'),
      meta: { shell: 'courier', role: 'POSTMAN' }
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

const homeOf = {
  path: 'pending',
  station: 'station-inbox',
  courier: 'courier-tasks'
} as const

const loginOf = {
  PATH_ADMIN: 'login',
  STATION_ADMIN: 'station-login',
  POSTMAN: 'courier-login'
} as const

router.beforeEach(async (to) => {
  await loadSession()
  const portal = to.meta.portal
  if (to.meta.public) {
    if (portal && session.user) {
      const expected = portal === 'courier' ? 'POSTMAN' : portal === 'station' ? 'STATION_ADMIN' : 'PATH_ADMIN'
      if (session.user.role === expected) {
        const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : ''
        return redirect || { name: homeOf[portal] }
      }
    }
    return true
  }
  const role = to.meta.role
  if (!session.user || (role && session.user.role !== role)) {
    return { name: role ? loginOf[role] : 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
