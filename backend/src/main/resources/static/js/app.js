/* ============================================================
   city-review 城市点评 — 首页逻辑（大众点评风格）
   ============================================================ */

// ==================== 模拟帖子数据 ====================
const postList = [
  {
    id: 1,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=fish',
    nickname: '小鱼同学',
    title: '无尽浪漫的夜晚｜在万花丛中摇晃着红酒杯🍷品战斧牛排🥩',
    content: '生活就是一半烟火·一半诗意。手执烟火谋生活，心怀诗意以谋爱。男朋友给不了的浪漫要学会自己给🍒 无法重来的一生，尽量快乐。这家花园西餐厅到处都是花，美好无处不在～',
    images: [
      'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1544025162-d76694265947?w=300&h=300&fit=crop'
    ],
    likes: 128,
    comments: 32,
    shopName: '小筑里·浪漫花园餐厅',
    isLiked: false
  },
  {
    id: 2,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=coco',
    nickname: '可可今天不吃肉',
    title: '人均30💰杭州这家港式茶餐厅我疯狂打call‼️',
    content: '又吃到一家好吃的茶餐厅🍴环境是怀旧tvb港风📺边吃边拍照片📷几十种菜品均价都在20+💰可以是很平价了！黯然销魂饭我吹爆！米饭上盖满了甜甜的叉烧还有两颗溏心蛋🍳',
    images: [
      'https://images.unsplash.com/photo-1552566626-52f8b828add9?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=300&h=300&fit=crop'
    ],
    likes: 96,
    comments: 18,
    shopName: '九记冰厅(远洋店)',
    isLiked: true
  },
  {
    id: 3,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=keai',
    nickname: '可爱多',
    title: '杭州周末好去处｜💰50就可以骑马啦🐎',
    content: '没想到在杭州周边还能找到这么宝藏的马场！50块钱就能体验骑马，教练很耐心，马儿也很温顺🐴 非常适合周末带小朋友来玩，拍照也很出片📸',
    images: [
      'https://images.unsplash.com/photo-1553284965-83e94889a2af?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1566312525350-03d3effd2931?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1534773727418-e77b2f4f653b?w=300&h=300&fit=crop'
    ],
    likes: 203,
    comments: 45,
    shopName: '骑士马术俱乐部',
    isLiked: false
  },
  {
    id: 4,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hotpot',
    nickname: '吃货小张',
    title: '海底捞新出的番茄锅底也太好吃了🍅人均104吃到撑',
    content: '周末和朋友去海底捞打卡了新出的浓香番茄锅底，真的绝了！🍲 点了一桌子菜，服务还是一如既往的好，小姐姐还送了小零食，爱了爱了💕',
    images: [
      'https://images.unsplash.com/photo-1552611052-bdfb7343e9f8?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1506354666786-959d6d497f1a?w=300&h=300&fit=crop'
    ],
    likes: 67,
    comments: 12,
    shopName: '海底捞火锅(水晶城店)',
    isLiked: false
  },
  {
    id: 5,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=ktvfan',
    nickname: '麦霸小王',
    title: '开乐迪KTV夜场实测｜音效超赞🎵性价比绝了',
    content: '作为资深K歌爱好者，这家KTV我真的吹爆！🎤 音响是进口的，曲库超全，新歌更新也快。重点是价格真的很良心，学生党也能轻松消费。包厢装修很有格调，拍照也好看📷',
    images: [
      'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=300&h=300&fit=crop',
      'https://images.unsplash.com/photo-1571896349842-daf07917be4c?w=300&h=300&fit=crop'
    ],
    likes: 152,
    comments: 28,
    shopName: '开乐迪KTV(运河上街店)',
    isLiked: false
  }
];

// ==================== 渲染帖子列表 ====================
function renderPosts() {
  const container = document.getElementById('feedList');
  if (!container) return;

  container.innerHTML = postList.map(post => `
    <article class="post-card" data-id="${post.id}" onclick="handlePostClick(${post.id})">
      <!-- 卡片头部 -->
      <div class="post-header">
        <img class="post-avatar" src="${post.avatar}" alt="${post.nickname}" loading="lazy"
             onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><circle cx=%2250%22 cy=%2250%22 r=%2250%22 fill=%22%23EEE%22/><text x=%2250%22 y=%2265%22 text-anchor=%22middle%22 font-size=%2240%22>👤</text></svg>'">
        <div class="post-user-info">
          <div class="post-nickname">${escHtml(post.nickname)}</div>
          <div class="post-shop">
            <i class="fas fa-store"></i> ${escHtml(post.shopName)}
          </div>
        </div>
      </div>

      <!-- 标题 -->
      <h3 class="post-title">${escHtml(post.title)}</h3>

      <!-- 正文摘要 -->
      <p class="post-content">${escHtml(post.content)}</p>

      <!-- 图片横向滑动 -->
      ${post.images && post.images.length > 0 ? `
      <div class="post-images">
        ${post.images.map(img => `
          <img class="post-image-item" src="${img}" alt="探店图片" loading="lazy"
               onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22120%22 height=%22120%22><rect fill=%22%23EEE%22 width=%22120%22 height=%22120%22/><text x=%2260%22 y=%2270%22 text-anchor=%22middle%22 font-size=%2230%22>🖼️</text></svg>'">
        `).join('')}
      </div>` : ''}

      <!-- 底部操作栏 -->
      <div class="post-actions" onclick="event.stopPropagation()">
        <div class="post-action ${post.isLiked ? 'liked' : ''}" onclick="handleLike(${post.id})">
          <i class="${post.isLiked ? 'fas' : 'far'} fa-heart"></i>
          <span>${formatCount(post.likes)}</span>
        </div>
        <div class="post-action">
          <i class="far fa-comment-dots"></i>
          <span>${formatCount(post.comments)}</span>
        </div>
        <div class="post-action" onclick="handleShare(${post.id})">
          <i class="far fa-share-square"></i>
          <span>分享</span>
        </div>
      </div>
    </article>
  `).join('');
}

// ==================== 交互处理 ====================

/** 点击帖子卡片 */
function handlePostClick(id) {
  const post = postList.find(p => p.id === id);
  console.log('📝 帖子详情 JSON：', JSON.stringify(post, null, 2));
  showToast('跳转至详情页：' + post.title);
}

/** 点赞 / 取消点赞 */
function handleLike(id) {
  const post = postList.find(p => p.id === id);
  if (!post) return;

  if (post.isLiked) {
    post.isLiked = false;
    post.likes--;
  } else {
    post.isLiked = true;
    post.likes++;
  }
  renderPosts();
  console.log(`❤️ 帖子 ${id} 点赞状态：${post.isLiked ? '已点赞' : '已取消'}，当前点赞数：${post.likes}`);
}

/** 分享 */
function handleShare(id) {
  showToast('分享功能开发中');
  console.log(`📤 分享帖子 ID=${id}`);
}

// ==================== 分类导航点击 ====================
document.addEventListener('DOMContentLoaded', () => {
  // 加载帖子
  renderPosts();

  // 分类点击
  document.querySelectorAll('.category-item').forEach(item => {
    item.addEventListener('click', function () {
      const category = this.dataset.category;
      const name = this.dataset.name || this.querySelector('span').textContent;
      showToast('分类' + name + ' — 功能开发中');
    });
  });

  // 搜索框点击
  document.getElementById('searchBox').addEventListener('click', () => {
    showToast('搜索功能开发中');
  });

  // "更多"链接
  document.getElementById('btnMore').addEventListener('click', () => {
    showToast('更多精选笔记 — 功能开发中');
  });

  // 底部 TabBar 点击 — 真实页面跳转
  document.querySelectorAll('.tab-item').forEach(tab => {
    tab.addEventListener('click', function () {
      const tabName = this.dataset.tab;
      if (tabName === 'home') return; // 当前已是首页

      if (tabName === 'mine') {
        // 跳转个人中心（带登录校验）
        if (!localStorage.getItem('token')) {
          window.location.href = 'login.html';
        } else {
          window.location.href = 'my.html';
        }
      } else {
        const tabLabels = { map: '地图', message: '消息', mine: '我的' };
        showToast((tabLabels[tabName] || tabName) + ' — 功能开发中');
      }
    });
  });

  // 城市切换
  document.querySelector('.top-bar-left').addEventListener('click', () => {
    showToast('城市切换功能开发中');
  });
});

// ==================== 工具函数 ====================

/** HTML 转义防 XSS */
function escHtml(str) {
  if (!str) return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 点赞数格式化（1000 → 1k） */
function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w';
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return String(n);
}

console.log('🏙️ 城市点评 首页已就绪 | 当前模拟帖子数：' + postList.length);
