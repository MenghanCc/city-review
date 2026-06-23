<template>
  <div class="blog-detail" v-if="blog">
    <h2>{{ blog.title }}</h2>
    <p class="blog-meta">👤 {{ blog.name }} | 🕐 {{ blog.createTime }}</p>
    <div class="content" v-html="blog.content"></div>

    <!-- 点赞 -->
    <div class="actions">
      <button @click="doLike" class="btn" :class="blog.isLike ? 'btn-primary' : 'btn-outline'">
        {{ blog.isLike ? '❤️ 已点赞' : '🤍 点赞' }}
      </button>
      <span class="like-count">共 {{ blog.liked }} 人点赞</span>
    </div>

    <!-- 点赞排行榜 -->
    <div class="section">
      <h4>🏆 点赞排行榜</h4>
      <div class="like-users">
        <span v-for="u in likeUsers" :key="u.id" class="like-user">
          {{ u.nickName }}
        </span>
        <span v-if="likeUsers.length === 0">暂无点赞</span>
      </div>
    </div>
  </div>
  <p v-else class="empty">加载中...</p>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getBlogById, likeBlog, getBlogLikes } from '../api/blog'

const route = useRoute()
const blog = ref(null)
const likeUsers = ref([])

onMounted(async () => {
  const id = route.params.id
  const [bRes, lRes] = await Promise.all([getBlogById(id), getBlogLikes(id, 10)])
  if (bRes.data.code === 200) blog.value = bRes.data.data
  if (lRes.data.code === 200) likeUsers.value = lRes.data.data || []
})

const doLike = async () => {
  const res = await likeBlog(route.params.id)
  if (res.data.code === 200) {
    blog.value.isLike = !blog.value.isLike
    blog.value.liked += blog.value.isLike ? 1 : -1
    const lRes = await getBlogLikes(route.params.id, 10)
    if (lRes.data.code === 200) likeUsers.value = lRes.data.data || []
  }
}
</script>

<style scoped>
.blog-meta { color: #888; font-size: 13px; margin-bottom: 16px; }
.content { line-height: 1.8; margin-bottom: 20px; }
.actions { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.like-count { font-size: 13px; color: #888; }
.section { margin-top: 20px; }
.section h4 { margin-bottom: 10px; }
.like-users { display: flex; flex-wrap: wrap; gap: 8px; }
.like-user { padding: 4px 12px; background: #fde; border-radius: 12px; font-size: 13px; color: #e74c3c; }
</style>
