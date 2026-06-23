<template>
  <div class="voucher-list">
    <h2>🎫 优惠券中心</h2>
    <div class="section">
      <h3>秒杀券 <button @click="$router.push('/seckill')" class="btn btn-primary btn-sm">进入秒杀</button></h3>
      <div v-for="v in seckillVouchers" :key="v.id" class="card voucher">
        <div>
          <b>{{ v.title }}</b>
          <p>¥{{ (v.payValue / 100).toFixed(0) }} 抵 ¥{{ (v.actualValue / 100).toFixed(0) }}</p>
          <p class="sub">库存: {{ v.stock }} | {{ v.beginTime }}</p>
        </div>
      </div>
    </div>
    <div class="section">
      <h3>普通券</h3>
      <div v-for="v in normalVouchers" :key="v.id" class="card voucher">
        <div>
          <b>{{ v.title }}</b>
          <p>¥{{ (v.payValue / 100).toFixed(0) }} 抵 ¥{{ (v.actualValue / 100).toFixed(0) }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getVoucherList } from '../api/voucher'

const vouchers = ref([])
const seckillVouchers = computed(() => vouchers.value.filter(v => v.type === 1))
const normalVouchers = computed(() => vouchers.value.filter(v => v.type === 0))

onMounted(async () => {
  const res = await getVoucherList(1)
  if (res.data.code === 200) vouchers.value = res.data.data || []
})
</script>

<style scoped>
.section { margin-bottom: 24px; }
.section h3 { margin-bottom: 12px; display: flex; align-items: center; gap: 12px; }
.voucher .sub { font-size: 12px; color: #888; margin-top: 4px; }
.btn-sm { padding: 6px 14px; font-size: 12px; }
</style>
