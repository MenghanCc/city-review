<template>
  <div class="follow-page">
    <div class="tabs">
      <button :class="{ active: tab === 'follow' }" @click="tab='follow'" class="tab-btn">关注列表</button>
      <button :class="{ active: tab === 'fans' }" @click="tab='fans'" class="tab-btn">粉丝列表</button>
    </div>

    <div class="user-list">
      <div v-for="u in users" :key="u.id" class="card user-row">
        <span>{{ u.nickName }}</span>
        <button v-if="tab === 'follow'" @click="doUnfollow(u.id)" class="btn btn-outline btn-sm">取关</button>
      </div>
      <p v-if="users.length === 0" class="empty">暂无数据</p>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { getMe } from '../api/user'
import { followList, fansList, follow } from '../api/follow'

const tab = ref('follow')
const users = ref([])
let userId = null

onMounted(async () => {
  const res = await getMe()
  if (res.data.code === 200) userId = res.data.data.id
  loadList()
})

watch(tab, () => loadList())

const loadList = async () => {
  if (!userId) return
  const res = tab.value === 'follow' ? await followList(userId) : await fansList(userId)
  if (res.data.code === 200) users.value = res.data.data || []
}

const doUnfollow = async (id) => {
  await follow(id, false)
  loadList()
}
</script>

<style scoped>
.tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.tab-btn { padding: 8px 20px; border: none; border-radius: 20px; background: #f0f0f0; font-size: 13px; cursor: pointer; }
.tab-btn.active { background: #e74c3c; color: #fff; }
.user-row { display: flex; justify-content: space-between; align-items: center; }
.btn-sm { padding: 6px 14px; font-size: 12px; }
</style>
