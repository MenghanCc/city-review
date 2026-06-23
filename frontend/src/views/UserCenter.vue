<template>
  <div class="user-center">
    <div class="card user-card">
      <h2>👤 {{ user?.nickName || '用户' }}</h2>
      <button @click="doLogout" class="btn btn-outline">退出登录</button>
    </div>

    <!-- 签到 -->
    <div class="card sign-card">
      <h3>📅 签到</h3>
      <p>本月已签到 <b>{{ calendar.totalSignDays || 0 }}</b> 天 | 连续签到 <b>{{ signDays }}</b> 天</p>
      <div class="calendar-grid">
        <span v-for="(d, i) in calendar.calendar || []" :key="i" :class="['day', { signed: d === 1, today: (i + 1) === calendar.today }]">
          {{ i + 1 }}
        </span>
      </div>
      <button @click="doSign" class="btn btn-primary" style="margin-top:12px">签到</button>
    </div>

    <!-- 快捷入口 -->
    <div class="card">
      <h3>快速入口</h3>
      <div class="quick-links">
        <router-link to="/blog" class="quick-item">📝 我的笔记</router-link>
        <router-link to="/follow" class="quick-item">👥 好友管理</router-link>
        <router-link to="/seckill" class="quick-item">⚡ 秒杀活动</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMe } from '../api/user'
import { sign, signCount, signCalendar } from '../api/sign'

const router = useRouter()
const user = ref(null)
const signDays = ref(0)
const calendar = ref({})

onMounted(async () => {
  const res = await getMe()
  if (res.data.code === 200) user.value = res.data.data
  else { router.push('/login'); return }
  loadSign()
})

const loadSign = async () => {
  const [cRes, calRes] = await Promise.all([signCount(), signCalendar()])
  if (cRes.data.code === 200) signDays.value = cRes.data.data
  if (calRes.data.code === 200) calendar.value = calRes.data.data || {}
}

const doSign = async () => {
  const res = await sign()
  if (res.data.code === 200) { alert(res.data.data); loadSign() }
  else alert(res.data.msg)
}

const doLogout = () => { localStorage.removeItem('token'); router.push('/login') }
</script>

<style scoped>
.user-card { display: flex; justify-content: space-between; align-items: center; }
.sign-card h3 { margin-bottom: 8px; }
.calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; margin-top: 12px; }
.day { text-align: center; padding: 6px 0; border-radius: 4px; background: #f0f0f0; font-size: 12px; }
.day.signed { background: #e74c3c; color: #fff; }
.day.today { border: 2px solid #e74c3c; }
.quick-links { display: flex; gap: 16px; margin-top: 8px; }
.quick-item { padding: 10px 20px; background: #f5f5f5; border-radius: 8px; text-decoration: none; color: #333; font-size: 14px; }
.quick-item:hover { background: #fee; color: #e74c3c; }
</style>
