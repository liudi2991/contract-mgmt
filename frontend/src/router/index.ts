import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/change-password',
    component: () => import('@/views/login/ChangePassword.vue'),
    meta: { requiresAuth: true, skipChangePwdGuard: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'contracts',
        name: 'ContractList',
        component: () => import('@/views/contract/List.vue'),
        meta: { title: '合同管理' },
      },
      {
        path: 'contracts/new',
        name: 'ContractNew',
        component: () => import('@/views/contract/Form.vue'),
        meta: { title: '新建合同' },
      },
      {
        path: 'contracts/:id/edit',
        name: 'ContractEdit',
        component: () => import('@/views/contract/Form.vue'),
        meta: { title: '编辑合同' },
      },
      {
        path: 'contracts/:id',
        name: 'ContractDetail',
        component: () => import('@/views/contract/Detail.vue'),
        meta: { title: '合同详情' },
      },
      {
        path: 'contracts/:id/payment-plan',
        name: 'PaymentPlanEditor',
        component: () => import('@/views/payment/PlanEditor.vue'),
        meta: { title: '回款计划' },
      },
      {
        path: 'payments',
        name: 'PaymentList',
        component: () => import('@/views/payment/List.vue'),
        meta: { title: '回款管理' },
      },
      {
        path: 'payments/new',
        name: 'PaymentNew',
        component: () => import('@/views/payment/Register.vue'),
        meta: { title: '登记回款' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isLoggedIn) return '/login'
  if (auth.mustChangePwd && !to.meta.skipChangePwdGuard) return '/change-password'
  return true
})

export default router
