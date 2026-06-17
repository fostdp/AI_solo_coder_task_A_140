import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/3d-view',
    name: '3d-view',
    component: () => import('@/views/Ship3DView.vue')
  },
  {
    path: '/stability',
    name: 'stability',
    component: () => import('@/views/StabilityAnalysis.vue')
  },
  {
    path: '/loading',
    name: 'loading',
    component: () => import('@/views/LoadingOptimization.vue')
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('@/views/HistoryData.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
