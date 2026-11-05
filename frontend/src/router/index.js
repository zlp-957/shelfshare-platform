import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/',
    redirect: '/reader'
  },
  // 读者端
  {
    path: '/reader',
    component: () => import('../components/ReaderLayout.vue'),
    children: [
      { path: '', name: 'ReaderHome', component: () => import('../views/reader/Home.vue') },
      { path: 'book/:id', name: 'BookDetail', component: () => import('../views/reader/BookDetail.vue') },
      { path: 'borrows', name: 'MyBorrows', component: () => import('../views/reader/MyBorrows.vue') },
      { path: 'cart', name: 'Cart', component: () => import('../views/reader/Cart.vue') },
      { path: 'reserves', name: 'MyReserves', component: () => import('../views/reader/MyReserves.vue') },
      { path: 'purchases', name: 'MyPurchases', component: () => import('../views/reader/MyPurchases.vue') },
      { path: 'messages', name: 'ReaderMessages', component: () => import('../views/reader/Messages.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/common/Profile.vue') }
    ]
  },
  // 支付结果页（支付宝同步跳转回来，不在布局内）
  {
    path: '/pay/result',
    name: 'PayResult',
    component: () => import('../views/reader/PayResult.vue')
  },
  // 管理员端
  {
    path: '/admin',
    component: () => import('../components/AdminLayout.vue'),
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'checkout', name: 'AdminCheckout', component: () => import('../views/admin/Checkout.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('../views/admin/Users.vue') },
      { path: 'password-resets', name: 'AdminPasswordResets', component: () => import('../views/admin/PasswordResets.vue') },
      { path: 'types', name: 'AdminTypes', component: () => import('../views/admin/Types.vue') },
      { path: 'books', name: 'AdminBooks', component: () => import('../views/admin/Books.vue') },
      { path: 'borrows', name: 'AdminBorrows', component: () => import('../views/admin/Borrows.vue') },
      { path: 'reserves', name: 'AdminReserves', component: () => import('../views/admin/Reserves.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('../views/admin/Orders.vue') },
      { path: 'notices', name: 'AdminNotices', component: () => import('../views/admin/Notices.vue') },
      { path: 'reconcile', name: 'AdminReconcile', component: () => import('../views/admin/Reconcile.vue') },
      { path: 'operation-logs', name: 'AdminOperationLogs', component: () => import('../views/admin/OperationLogs.vue') },
      { path: 'profile', name: 'AdminProfile', component: () => import('../views/common/Profile.vue') }
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

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')

  if (to.path === '/login' || to.path === '/register') {
    // 已登录则跳转
    if (token && userInfo) {
      next(userInfo.role === 1 ? '/admin' : '/reader')
    } else {
      next()
    }
  } else {
    if (!token) {
      next('/login')
    } else if (to.path.startsWith('/admin') && userInfo?.role !== 1) {
      // 非管理员访问后台
      next('/reader')
    } else if (to.path === '/reader/profile' && userInfo?.role === 1) {
      // 管理员的个人中心在管理端，直接手输地址也给他送过去，避免两个入口各改一半
      next('/admin/profile')
    } else {
      next()
    }
  }
})

export default router
