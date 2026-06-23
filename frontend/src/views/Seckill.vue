<template>
  <div class="seckill-page">
    <h2>⚡ 限时秒杀</h2>
    <div class="seckill-list">
      <div v-for="v in vouchers" :key="v.id" class="card seckill-card">
        <div class="v-info">
          <h3>{{ v.title }}</h3>
          <p class="v-price">💰 <b>¥{{ (v.payValue / 100).toFixed(0) }}</b> 抵 <b>¥{{ (v.actualValue / 100).toFixed(0) }}</b></p>
          <p class="v-stock">库存：{{ v.stock }} | {{ v.beginTime }} ~ {{ v.endTime }}</p>
        </div>
        <button @click="doSeckill(v.id)" class="btn btn-primary" :disabled="seckilling.has(v.id)">
          {{ seckilling.has(v.id) ? '抢购中...' : '立即抢购' }}
        </button>
      </div>
    </div>
    <p v-if="msg" class="result-msg">{{ msg }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getVoucherList } from '../api/voucher'
import { seckillVoucher, querySeckillResult } from '../api/voucher'

const vouchers = ref([])
const msg = ref('')
const seckilling = ref(new Set())

onMounted(async () => {
  // 加载店铺1的秒杀券（示例）
  const res = await getVoucherList(1)
  if (res.data.code === 200) vouchers.value = (res.data.data || []).filter(v => v.type === 1)
})

const doSeckill = async (id) => {
  seckilling.value.add(id)
  try {
    const res = await seckillVoucher(id)
    if (res.data.code === 200) {
      const orderId = res.data.data
      if (orderId === 0) {
        msg.value = '⏳ 排队中，请稍候...'
        pollResult(id)
      } else {
        msg.value = `✅ 秒杀成功！订单号：${orderId}`
      }
    } else {
      msg.value = `❌ ${res.data.msg}`
    }
  } finally {
    seckilling.value.delete(id)
  }
}

const pollResult = async (voucherId) => {
  let attempts = 0
  const timer = setInterval(async () => {
    const res = await querySeckillResult(voucherId)
    if (res.data.code === 200 && res.data.data > 0) {
      msg.value = `✅ 下单成功！订单号：${res.data.data}`
      clearInterval(timer)
    }
    if (++attempts >= 30) { clearInterval(timer); msg.value = '⏰ 超时，请刷新查看' }
  }, 1000)
}
</script>

<style scoped>
.seckill-page h2 { margin-bottom: 16px; }
.seckill-card { display: flex; justify-content: space-between; align-items: center; }
.v-price b { color: #e74c3c; }
.v-stock { font-size: 12px; color: #888; margin-top: 6px; }
.result-msg { margin-top: 16px; padding: 12px; background: #fffbe6; border-radius: 6px; text-align: center; font-size: 14px; }
</style>
