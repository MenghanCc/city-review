<template>
  <div class="blog-list">
    <div class="tabs">
      <button :class="{ active: tab === 'hot' }" @click="switchTab('hot')" class="tab-btn">🔥 热门</button>
      <button :class="{ active: tab === 'feed' }" @click="switchTab('feed')" class="tab-btn">📡 关注流</button>
      <button :class="{ active: tab === 'my' }" @click="switchTab('my')" class="tab-btn">📝 我的</button>
    </div>
    <div v-for="blog in blogs" :key="blog.id" class="card blog-card" @click="$router.push(`/blog/${blog.id}`)">
      <div class="blog-header">
        <img :src="blog.icon" class="avatar" v-if="blog.icon" />
        <span class="bl-name">{{ blog.name }}</span>
      </div>
      <h3>{{ blog.title }}</h3>
      <p class="bl-meta">❤️ {{ blog.liked }} | 💬 {{ blog.comments || 0 }}</p>
    </div>
    <p v-if="blogs.length === 0" class="empty">暂无笔记</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHotBlogs, getMyBlogs, getFeed } from '../api/blog'

const tab = ref('hot')
const blogs = ref([])

onMounted(() => loadTab('hot'))

const switchTab = (t) => { tab.value = t; loadTab(t) }

const loadTab = async (t) => {
  let res
  if (t === 'hot') res = await getHotBlogs()
  else if (t === 'feed') res = await getFeed()
  else res = await getMyBlogs()
  if (res.data.code === 200) blogs.value = res.data.data?.list || res.data.data || []
}
</script>

<style scoped>
.tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.tab-btn { padding: 8px 20px; border: none; border-radius: 20px; background: #f0f0f0; font-size: 13px; cursor: pointer; }
.tab-btn.active { background: #e74c3c; color: #fff; }
.blog-card { cursor: pointer; }
.blog-card:hover { transform: translateY(-2px); transition: .15s; }
.blog-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.avatar { width: 24px; height: 24px; border-radius: 50%; }
.bl-name { font-size: 13px; color: #888; }
.bl-meta { font-size: 12px; color: #aaa; margin-top: 6px; }
</style>
