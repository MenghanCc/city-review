<template>
  <div class="home">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <input v-model="keyword" @keyup.enter="searchShop" placeholder="搜索商户名称..." class="search-input" />
      <button @click="searchShop" class="btn btn-primary">搜索</button>
    </div>

    <!-- 商户类型导航 -->
    <div class="type-nav card">
      <span v-for="t in types" :key="t.id" :class="['type-item', { active: activeType === t.id }]" @click="selectType(t.id)">
        {{ t.name }}
      </span>
    </div>

    <!-- 附近商户按钮 -->
    <div class="toolbar">
      <button @click="getLocation" class="btn btn-outline">📍 附近商户</button>
    </div>

    <!-- 商户列表 -->
    <div class="shop-list">
      <div v-for="shop in shops" :key="shop.id" class="shop-card card" @click="goShop(shop.id)">
        <div class="shop-info">
          <h3>{{ shop.name }}</h3>
          <p class="shop-addr">📍 {{ shop.area || '' }} {{ shop.address }}</p>
          <p class="shop-meta">
            <span>⭐ {{ (shop.score / 10).toFixed(1) }}</span>
            <span>💰 ¥{{ shop.avgPrice || '-' }}/人</span>
            <span>📦 已售 {{ shop.sold }}</span>
            <span v-if="shop.distance">📏 {{ shop.distance }}m</span>
          </p>
        </div>
      </div>
      <p v-if="shops.length === 0" class="empty">暂无数据</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getShopByType, searchShop, getNearbyShops, getShopTypeList } from '../api/shop'

const router = useRouter()
const keyword = ref('')
const types = ref([])
const shops = ref([])
const activeType = ref(1)

onMounted(async () => {
  const res = await getShopTypeList()
  if (res.data.code === 200) types.value = res.data.data || []
  loadShops(1)
})

const loadShops = async (typeId) => {
  const res = await getShopByType(typeId)
  if (res.data.code === 200) shops.value = res.data.data || []
}

const selectType = (typeId) => {
  activeType.value = typeId
  loadShops(typeId)
}

const searchShopAction = async () => {
  if (!keyword.value.trim()) { loadShops(activeType.value); return }
  const res = await searchShop(keyword.value)
  if (res.data.code === 200) shops.value = res.data.data || []
}

const getLocation = () => {
  if (!navigator.geolocation) { alert('浏览器不支持定位'); return }
  navigator.geolocation.getCurrentPosition(async pos => {
    const res = await getNearbyShops(pos.coords.longitude, pos.coords.latitude)
    if (res.data.code === 200) shops.value = res.data.data || []
  }, () => alert('获取位置失败，请检查定位权限'))
}

const goShop = (id) => router.push(`/shop/${id}`)
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 16px; }
.search-input { flex: 1; padding: 10px 16px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; outline: none; }
.type-nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.type-item { padding: 6px 14px; border-radius: 20px; background: #f0f0f0; font-size: 13px; cursor: pointer; transition: .2s; }
.type-item.active, .type-item:hover { background: #e74c3c; color: #fff; }
.toolbar { margin-bottom: 12px; }
.shop-card { cursor: pointer; transition: transform .15s; }
.shop-card:hover { transform: translateY(-2px); }
.shop-card h3 { font-size: 16px; margin-bottom: 6px; }
.shop-addr { font-size: 12px; color: #888; margin-bottom: 6px; }
.shop-meta { display: flex; gap: 12px; font-size: 12px; color: #666; }
.empty { text-align: center; color: #999; padding: 40px 0; }
</style>
