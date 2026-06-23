import { createRouter, createWebHashHistory } from 'vue-router'

// 路由懒加载
const Home = () => import('../views/Home.vue')
const Login = () => import('../views/Login.vue')
const ShopDetail = () => import('../views/ShopDetail.vue')
const BlogList = () => import('../views/BlogList.vue')
const BlogDetail = () => import('../views/BlogDetail.vue')
const VoucherList = () => import('../views/VoucherList.vue')
const Seckill = () => import('../views/Seckill.vue')
const UserCenter = () => import('../views/UserCenter.vue')
const Follow = () => import('../views/Follow.vue')

const routes = [
  { path: '/', name: 'Home', component: Home, meta: { title: '首页' } },
  { path: '/login', name: 'Login', component: Login, meta: { title: '登录' } },
  { path: '/shop/:id', name: 'ShopDetail', component: ShopDetail, meta: { title: '商户详情' } },
  { path: '/blog', name: 'BlogList', component: BlogList, meta: { title: '探店笔记' } },
  { path: '/blog/:id', name: 'BlogDetail', component: BlogDetail, meta: { title: '笔记详情' } },
  { path: '/voucher', name: 'VoucherList', component: VoucherList, meta: { title: '优惠券' } },
  { path: '/seckill', name: 'Seckill', component: Seckill, meta: { title: '秒杀活动' } },
  { path: '/user', name: 'UserCenter', component: UserCenter, meta: { title: '个人中心' } },
  { path: '/follow', name: 'Follow', component: Follow, meta: { title: '好友关注' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() { return { top: 0 } }
})

router.beforeEach((to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '城市点评'
  // 需要登录的页面
  const needAuth = ['UserCenter', 'Seckill', 'Follow']
  if (needAuth.includes(to.name) && !localStorage.getItem('token')) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
