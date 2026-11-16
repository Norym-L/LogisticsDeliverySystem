import { createRouter, createWebHistory } from 'vue-router'
import { loadSession, session } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      name: 'pending',
      component: () => import('@/views/PendingView.vue')
    },
    {
      path: '/postmen',
      name: 'postmen',
      component: () => import('@/views/PostmenView.vue')
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  await loadSession()
  if (to.meta.public) {
    return session.user && to.name === 'login' ? { name: 'pending' } : true
  }
  if (!session.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
