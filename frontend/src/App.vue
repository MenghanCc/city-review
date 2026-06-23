<template>
  <div id="city-review-app">
    <nav class="navbar" v-if="showNav">
      <div class="nav-container">
        <router-link to="/" class="nav-brand">🏙️ 城市点评</router-link>
        <div class="nav-links">
          <router-link to="/">首页</router-link>
          <router-link to="/blog">探店笔记</router-link>
          <router-link to="/voucher">优惠券</router-link>
          <router-link v-if="user" to="/user">{{ user.nickName || '个人中心' }}</router-link>
          <router-link v-else to="/login">登录</router-link>
        </div>
      </div>
    </nav>
    <main class="main-content">
      <router-view :key="$route.fullPath" />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getMe } from './api/user'

const route = useRoute()
const user = ref(null)

// 不显示导航栏的页面
const hideNavRoutes = ['/login']
const showNav = computed(() => !hideNavRoutes.includes(route.path))

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (token) {
    try {
      const res = await getMe()
      if (res.data.code === 200) user.value = res.data.data
    } catch (e) { /* ignore */ }
  }
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', sans-serif; background: #f5f5f5; color: #333; }

.navbar { background: #fff; border-bottom: 1px solid #eee; position: sticky; top: 0; z-index: 100; }
.nav-container { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; height: 56px; }
.nav-brand { font-size: 20px; font-weight: 700; color: #e74c3c; text-decoration: none; }
.nav-links { display: flex; gap: 20px; }
.nav-links a { color: #555; text-decoration: none; font-size: 14px; transition: color .2s; }
.nav-links a:hover, .nav-links a.router-link-active { color: #e74c3c; }

.main-content { max-width: 1200px; margin: 0 auto; padding: 16px; min-height: calc(100vh - 56px); }

.btn { display: inline-block; padding: 10px 24px; border: none; border-radius: 6px; font-size: 14px; cursor: pointer; transition: .2s; }
.btn-primary { background: #e74c3c; color: #fff; }
.btn-primary:hover { background: #c0392b; }
.btn-outline { background: #fff; color: #e74c3c; border: 1px solid #e74c3c; }

.card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
</style>
