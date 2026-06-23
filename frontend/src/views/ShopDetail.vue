<template>
  <div class="shop-detail" v-if="shop">
    <h2>{{ shop.name }}</h2>
    <p class="addr">📍 {{ shop.area }} {{ shop.address }}</p>
    <div class="meta">
      <span>⭐ {{ (shop.score / 10).toFixed(1) }}</span>
      <span>💰 ¥{{ shop.avgPrice || '-' }}/人</span>
      <span>📦 {{ shop.sold }}</span>
      <span>🕐 {{ shop.openHours || '-' }}</span>
    </div>
    <p>👁️ 今日 UV：{{ uv }}</p>

    <!-- 优惠券 -->
    <div class="section">
      <h3>🎫 优惠券</h3>
      <div v-for="v in vouchers" :key="v.id" class="voucher card">
        <div>
          <b>{{ v.title }}</b>
          <p>¥{{ (v.payValue / 100).toFixed(0) }} 抵 ¥{{ (v.actualValue / 100).toFixed(0) }}</p>
          <p class="sub">{{ v.subTitle }}</p>
        </div>
        <button v-if="v.type === 1" @click="$router.push('/seckill')" class="btn btn-primary btn-sm">⚡ 秒杀</button>
      </div>
    </div>
  </div>
  <p v-else class="empty">加载中...</p>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShopById, getShopUV } from '../api/shop'
import { getVoucherList } from '../api/voucher'

const route = useRoute()
const shop = ref(null)
const vouchers = ref([])
const uv = ref(0)

onMounted(async () => {
  const id = route.params.id
  const [sRes, vRes, uvRes] = await Promise.all([
    getShopById(id), getVoucherList(id), getShopUV(id)
  ])
  if (sRes.data.code === 200) shop.value = sRes.data.data
  if (vRes.data.code === 200) vouchers.value = vRes.data.data || []
  if (uvRes.data.code === 200) uv.value = uvRes.data.data
})
</script>

<style scoped>
.shop-detail h2 { margin-bottom: 6px; }
.addr { color: #888; font-size: 13px; margin-bottom: 10px; }
.meta { display: flex; gap: 14px; font-size: 13px; color: #555; margin-bottom: 16px; }
.section { margin-top: 24px; }
.section h3 { margin-bottom: 12px; }
.voucher { display: flex; justify-content: space-between; align-items: center; }
.voucher .sub { font-size: 12px; color: #888; margin-top: 4px; }
.btn-sm { padding: 6px 16px; font-size: 13px; }
</style>
