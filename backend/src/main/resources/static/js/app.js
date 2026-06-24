/* ============================================================
   city-review 首页 — 完整交互（搜索/分类/城市/详情）
   ============================================================ */

// ---- 全局状态 ----
let currentCity = localStorage.getItem('city');
if (!currentCity) { currentCity = '武汉'; localStorage.setItem('city', '武汉'); }
let searchKeyword = '';
let searchTimer = null;
let allBlogs = [];
let currentSort = 'time';  // 'time' | 'likes'

// 国内城市列表
const CITY_LIST = [
  { group: '热门', cities: ['武汉','北京','上海','杭州','广州','深圳','成都','南京','重庆','西安','长沙','苏州'] }
];

document.addEventListener('DOMContentLoaded', async () => {
  renderCity();
  buildCityPicker();
  await loadPosts();
  bindEvents();
  console.log('🏙️ city-review 首页就绪 | 城市：' + currentCity);
});

// ==================== 城市选择器 ====================
function renderCity() {
  const el = document.querySelector('.city-name');
  if (el) el.textContent = currentCity;
}

function openCityPicker() {
  document.getElementById('cityPicker').style.display = 'flex';
}

function closeCityPicker() {
  document.getElementById('cityPicker').style.display = 'none';
}

function buildCityPicker() {
  const body = document.getElementById('cityPickerBody');
  if (!body) return;
  let html = '';
  CITY_LIST.forEach(group => {
    html += '<div class="city-tags">';
    group.cities.forEach(c => {
      const cls = c === currentCity ? 'city-tag active' : 'city-tag';
      html += '<span class="' + cls + '" onclick="selectCity(\'' + c + '\')">' + c + '</span>';
    });
    html += '</div>';
  });
  body.innerHTML = html;
}

function selectCity(city) {
  currentCity = city;
  localStorage.setItem('city', city);
  renderCity();
  closeCityPicker();
  buildCityPicker();
  hideSearchResults();
  showToast('已切换到 ' + city);
  loadPosts();
}

// ==================== 数据加载（API） ====================
async function loadPosts() {
  allBlogs = [];
  const container = document.getElementById('feedList');
  if (container) container.innerHTML = '<p style="text-align:center;padding:40px 0;color:#999;">加载中...</p>';

  try {
    const res = await api.get('/blog/list', { params: { city: currentCity, page: 1, size: 5, sort: currentSort } });
    if (res.data.code === 200 && res.data.data) {
      allBlogs = res.data.data.records || [];
    }
  } catch (e) {
    console.warn('帖子加载失败', e);
  }
  renderPosts(allBlogs);
}

/** 渲染帖子卡片 */
function renderPosts(posts) {
  const container = document.getElementById('feedList');
  if (!container) return;

  if (!posts || posts.length === 0) {
    container.innerHTML = '<p style="text-align:center;color:#999;padding:40px 0;">暂无内容</p>';
    return;
  }

  container.innerHTML = posts.map(p => `
    <article class="post-card" onclick="goDetail(${p.id})">
      <div class="post-header">
        <img class="post-avatar" src="${p.avatar || '/imgs/default-avatar.svg'}" onerror="this.src='/imgs/default-avatar.svg'" alt="" loading="lazy"
             onclick="event.stopPropagation();window.location.href='user-profile.html?userId=${p.userId || ''}'" style="cursor:pointer;">
        <div class="post-user-info">
          <div class="post-nickname" onclick="event.stopPropagation();window.location.href='user-profile.html?userId=${p.userId || ''}'" style="cursor:pointer;">${escHtml(p.nickname || '匿名')}</div>
          <div class="post-shop"><i class="fas fa-store"></i> ${escHtml(p.shopName || '未知商户')}</div>
        </div>
      </div>
      <h3 class="post-title">${escHtml(p.title || '')}</h3>
      <p class="post-content">${escHtml(p.content || '')}</p>
      ${renderImages(p.images)}
      <div class="post-actions" onclick="event.stopPropagation()">
        <div class="post-action ${p.isLiked ? 'liked' : ''}" onclick="handleLike(event, ${p.id})">
          <i class="${p.isLiked ? 'fas' : 'far'} fa-heart"></i>
          <span>${fmtCount(p.liked || 0)}</span>
        </div>
        <div class="post-action">
          <i class="far fa-comment-dots"></i>
          <span>${fmtCount(p.comments || 0)}</span>
        </div>
      </div>
    </article>
  `).join('');
}

function renderImages(imagesStr) {
  if (!imagesStr) return '';
  const imgs = imagesStr.split(',').filter(Boolean);
  if (imgs.length === 0) return '';
  return `<div class="post-images">${imgs.map(u => `<img class="post-image-item" src="${u}" loading="lazy" onerror="this.src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22120%22 height=%22120%22%3E%3Crect fill=%22%23F0F0F0%22 width=%22120%22 height=%22120%22/%3E%3Ctext x=%2260%22 y=%2270%22 text-anchor=%22middle%22 font-size=%2240%22%3E🖼%3C/text%3E%3C/svg%3E'">`).join('')}</div>`;
}

// ==================== 搜索（防抖） ====================
function bindSearchEvents() {
  const searchBox = document.getElementById('searchBox');
  const input = document.querySelector('.search-input');
  if (!input) return;

  // 点击搜索框区域
  if (searchBox) {
    searchBox.addEventListener('click', () => input.focus());
  }

  // keyup 防抖
  input.addEventListener('keyup', function (e) {
    const keyword = input.value.trim();
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      if (keyword) {
        searchShops(keyword);
      } else {
        hideSearchResults();
        renderPosts(allBlogs);
      }
    }, 400);
  });

  // 回车立即搜索
  input.addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
      clearTimeout(searchTimer);
      const keyword = input.value.trim();
      if (keyword) searchShops(keyword);
    }
  });

  // 允许输入
  input.removeAttribute('readonly');
}

async function searchShops(keyword) {
  searchKeyword = keyword;
  try {
    const res = await api.get('/shop/list', {
      params: { name: keyword, city: currentCity }
    });
    if (res.data.code === 200) {
      const shops = res.data.data || [];
      showSearchResults(shops, keyword);
    }
  } catch (e) {
    showToast('搜索失败');
  }
}

function showSearchResults(shops, keyword) {
  const el = document.getElementById('searchResults');
  if (!el) return;

  el.style.display = 'block';
  if (shops.length === 0) {
    el.innerHTML = '<div class="result-empty">未找到与"' + escHtml(keyword) + '"相关的商户</div>';
    return;
  }
  el.innerHTML = shops.map(s =>
    '<div class="result-item" onclick="goShop(' + s.id + ')">' +
      '<div class="result-name">' + escHtml(s.name) + '</div>' +
      '<div class="result-meta">📍 ' + escHtml(s.area||'') + ' ' + escHtml(s.address||'') + ' &nbsp;|&nbsp; ⭐' + ((s.score||0)/10).toFixed(1) + ' &nbsp;|&nbsp; 💰¥' + (s.avgPrice||'-') + '/人</div>' +
    '</div>'
  ).join('');
}

function goShop(id) {
  window.location.href = 'shop-detail.html?id=' + id;
}

function hideSearchResults() {
  const el = document.getElementById('searchResults');
  if (el) el.style.display = 'none';
  searchKeyword = '';
}

// ==================== 分类导航 → 跳转店铺列表页 ====================
function goCategory(catName) {
  window.location.href = 'shop-list.html?city=' + encodeURIComponent(currentCity) + '&category=' + encodeURIComponent(catName);
}

// ==================== 帖子详情跳转 ====================
function goDetail(id) {
  window.location.href = 'detail.html?id=' + id;
}

// ==================== 点赞（真实 API） ====================
async function handleLike(e, blogId) {
  e.stopPropagation();
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  try {
    var res = await api.put('/blog/like/' + blogId);
    if (res.data.code === 200) {
      var blog = allBlogs.find(function(b) { return b.id === blogId; });
      if (blog) {
        blog.isLiked = !blog.isLiked;
        blog.liked = (blog.liked || 0) + (blog.isLiked ? 1 : -1);
      }
      renderPosts(allBlogs);
    }
  } catch (e) {
    showToast('操作失败');
  }
}

// ==================== 排序切换 ====================
function switchSort(sort) {
  if (currentSort === sort) return;
  currentSort = sort;
  // 更新 UI
  document.querySelectorAll('.sort-item').forEach(function(el) {
    el.classList.toggle('active', el.dataset.sort === sort);
  });
  loadPosts();
}

// ==================== 事件绑定 ====================
function bindEvents() {
  bindSearchEvents();

  // 城市切换 - 打开选择器
  const cityEl = document.querySelector('.top-bar-left');
  if (cityEl) {
    cityEl.onclick = openCityPicker;
  }

  // 分类点击 → 跳转店铺列表
  document.querySelectorAll('.category-item').forEach(item => {
    item.addEventListener('click', function () {
      const name = this.dataset.name;
      if (name) goCategory(name);
    });
  });

  // 搜索框点击
  const searchBox = document.getElementById('searchBox');
  if (searchBox) {
    searchBox.addEventListener('click', () => {
      const input = document.querySelector('.search-input');
      if (input) input.focus();
    });
  }

  // "更多"按钮 — 跳转帖子列表页
  const btnMore = document.getElementById('btnMore');
  if (btnMore) {
    btnMore.addEventListener('click', () => {
      window.location.href = 'blog.html?city=' + encodeURIComponent(currentCity);
    });
  }

  // 底部 TabBar
  document.querySelectorAll('.tab-item').forEach(tab => {
    tab.addEventListener('click', function () {
      const name = this.dataset.tab;
      if (name === 'home') return;
      if (name === 'mine') {
        window.location.href = localStorage.getItem('token') ? 'my.html' : 'login.html';
        return;
      }
      if (name === 'map') {
        window.location.href = 'map.html';
        return;
      }
      if (name === 'message') {
        window.location.href = 'chat.html';
        return;
      }
      showToast(name + ' — 功能开发中');
    });
  });

  // 城市选择器 - 点击遮罩关闭
  const overlay = document.getElementById('cityPicker');
  if (overlay) {
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) closeCityPicker();
    });
  }

}

// ---- 工具 ----
function escHtml(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') : ''; }
function fmtCount(n) { return n >= 10000 ? (n/10000).toFixed(1).replace(/\.0$/,'')+'w' : n >= 1000 ? (n/1000).toFixed(1).replace(/\.0$/,'')+'k' : String(n||0); }
