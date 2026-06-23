<template>
  <div class="login-page">
    <div class="login-card card">
      <h2>📱 手机号登录</h2>
      <p class="subtitle">city-review 城市点评</p>
      <div class="form-group">
        <label>手机号</label>
        <input v-model="phone" placeholder="请输入手机号" maxlength="11" class="input" />
      </div>
      <div class="form-group">
        <label>验证码</label>
        <div class="code-row">
          <input v-model="code" placeholder="请输入验证码" maxlength="6" class="input code-input" />
          <button @click="getCode" :disabled="countdown > 0" class="btn btn-outline code-btn">
            {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
          </button>
        </div>
      </div>
      <p class="hint">💡 验证码打印在 IDEA 控制台中，请查看日志</p>
      <button @click="doLogin" class="btn btn-primary btn-full">登 录</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { sendCode, login } from '../api/user'

const router = useRouter()
const route = useRoute()
const phone = ref('')
const code = ref('')
const countdown = ref(0)

const getCode = async () => {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) { alert('请输入正确的手机号'); return }
  const res = await sendCode(phone.value)
  if (res.data.code === 200) {
    alert('验证码已发送，请在控制台查看')
    countdown.value = 60
    const timer = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(timer) }, 1000)
  } else {
    alert(res.data.msg)
  }
}

const doLogin = async () => {
  if (!phone.value || !code.value) { alert('请填写完整信息'); return }
  const res = await login({ phone: phone.value, code: code.value })
  if (res.data.code === 200) {
    localStorage.setItem('token', res.data.data)
    router.push(route.query.redirect || '/')
  } else {
    alert(res.data.msg)
  }
}
</script>

<style scoped>
.login-page { display: flex; justify-content: center; align-items: center; min-height: 70vh; }
.login-card { width: 360px; padding: 32px; }
.login-card h2 { text-align: center; margin-bottom: 4px; }
.subtitle { text-align: center; color: #888; font-size: 13px; margin-bottom: 24px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; color: #555; margin-bottom: 6px; }
.input { width: 100%; padding: 10px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; outline: none; }
.code-row { display: flex; gap: 8px; }
.code-input { flex: 1; }
.code-btn { white-space: nowrap; font-size: 12px; padding: 10px 16px; }
.hint { font-size: 12px; color: #f39c12; margin-bottom: 16px; text-align: center; }
.btn-full { width: 100%; padding: 12px; font-size: 16px; }
</style>
