/* ============================================================
   city-review 首页 — 完整交互（搜索/分类/城市/详情）
   ============================================================ */

// ---- 全局状态 ----
let currentCity = localStorage.getItem('city') || '杭州';
let activeCategory = null;
let searchKeyword = '';
let searchTimer = null;
let allBlogs = [];

// 国内城市列表
const CITY_LIST = [
  { group: '热门', cities: ['北京','上海','广州','深圳','杭州','成都','武汉','南京','重庆','西安','长沙','苏州'] },
  { group: '华东', cities: ['上海','杭州','南京','苏州','宁波','无锡','合肥','济南','青岛','厦门','福州','南昌'] },
  { group: '华北', cities: ['北京','天津','石家庄','太原','呼和浩特'] },
  { group: '华南', cities: ['广州','深圳','东莞','佛山','南宁','海口','珠海'] },
  { group: '华中', cities: ['武汉','长沙','郑州','洛阳'] },
  { group: '西南', cities: ['成都','重庆','昆明','贵阳','拉萨'] },
  { group: '西北', cities: ['西安','兰州','西宁','银川','乌鲁木齐'] },
  { group: '东北', cities: ['沈阳','大连','哈尔滨','长春','吉林'] }
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
    html += '<div class="city-group-title">' + group.group + '</div><div class="city-tags">';
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
  showToast('已切换到 ' + city);
  loadPosts();
}

// ==================== 数据加载（API） ====================
async function loadPosts() {
  try {
    const res = await api.get('/blog/list', { params: { page: 1, size: 20 } });
    if (res.data.code === 200 && res.data.data) {
      allBlogs = res.data.data.records || [];
    }
  } catch (e) {
    console.warn('帖子加载失败，使用本地数据', e);
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
        <img class="post-avatar" src="${p.avatar || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><circle cx=%2250%22 cy=%2250%22 r=%2250%22 fill=%22%23EEE%22/></svg>'}" alt="" loading="lazy">
        <div class="post-user-info">
          <div class="post-nickname">${escHtml(p.nickname || '匿名')}</div>
          <div class="post-shop"><i class="fas fa-store"></i> ${escHtml(p.shopName || '未知商户')}</div>
        </div>
      </div>
      <h3 class="post-title">${escHtml(p.title || '')}</h3>
      <p class="post-content">${escHtml(p.content || '')}</p>
      ${renderImages(p.images)}
      <div class="post-actions" onclick="event.stopPropagation()">
        <div class="post-action" onclick="handleLike(event, ${p.id})">
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
  return `<div class="post-images">${imgs.map(u => `<img class="post-image-item" src="${u}" loading="lazy" onerror="this.style.display='none'">`).join('')}</div>`;
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
    el.innerHTML = `<div style="padding:16px;text-align:center;color:#999;">未找到与"${escHtml(keyword)}"相关的商户</div>`;
    return;
  }
  el.innerHTML = `
    <div style="padding:8px 16px;font-size:12px;color:#999;">搜索结果（${shops.length}）</div>
    ${shops.map(s => `
      <div class="card" style="margin:8px 12px;padding:12px;cursor:pointer" onclick="window.location.href='shop.html?id=${s.id}'">
        <b>${escHtml(s.name)}</b>
        <p style="font-size:12px;color:#888;margin-top:4px;">📍 ${escHtml(s.area||'')} ${escHtml(s.address||'')}</p>
        <p style="font-size:11px;color:#aaa;">⭐ ${((s.score||0)/10).toFixed(1)} | 💰 ¥${s.avgPrice||'-'}/人</p>
      </div>
    `).join('')}
  `;
}

function hideSearchResults() {
  const el = document.getElementById('searchResults');
  if (el) el.style.display = 'none';
  searchKeyword = '';
}

// ==================== 分类过滤 ====================
function filterByCategory(catName) {
  if (activeCategory === catName) {
    // 取消筛选，显示全部
    activeCategory = null;
    hideSearchResults();
    renderPosts(allBlogs);
    showToast('已显示全部');
    document.querySelectorAll('.category-item').forEach(el => el.style.opacity = '1');
    return;
  }
  activeCategory = catName;
  hideSearchResults();

  // 先获取分类下的商户 ID 列表，再过滤帖子
  api.get('/shop/list', { params: { category: catName, city: currentCity } })
    .then(res => {
      if (res.data.code !== 200) return;
      const shopIds = new Set((res.data.data || []).map(s => s.id));
      const filtered = allBlogs.filter(b => shopIds.has(b.shopId) || shopIds.has(Number(b.shopId)));
      renderPosts(filtered);

      // 高亮分类
      document.querySelectorAll('.category-item').forEach(el => {
        const name = el.dataset.name;
        el.style.opacity = (name === catName) ? '1' : '0.5';
      });
      showToast('分类：' + catName + (filtered.length === 0 ? '（暂无帖子）' : ''));
    }).catch(() => showToast('加载失败'));
}

// ==================== 帖子详情跳转 ====================
function goDetail(id) {
  window.location.href = 'detail.html?id=' + id;
}

// ==================== 点赞（真实 API） ====================
async function handleLike(e, blogId) {
  e.stopPropagation();
  try {
    const res = await api.put('/blog/like/' + blogId);
    if (res.data.code === 200) {
      // 更新本地状态
      const blog = allBlogs.find(b => b.id === blogId);
      if (blog) {
        blog.isLiked = !blog.isLiked;
        blog.liked = (blog.liked || 0) + (blog.isLiked ? 1 : -1);
      }
      renderPosts(activeCategory ? allBlogs.filter(b =>
        (b.shopName || '').includes(activeCategory)) : allBlogs);
    } else if (res.data.code === 401) {
      showToast('请先登录');
    }
  } catch (e) {
    showToast('操作失败');
  }
}

// ==================== 事件绑定 ====================
function bindEvents() {
  bindSearchEvents();

  // 城市切换 - 打开选择器
  const cityEl = document.querySelector('.top-bar-left');
  if (cityEl) {
    cityEl.onclick = openCityPicker;
  }

  // 分类点击
  document.querySelectorAll('.category-item').forEach(item => {
    item.addEventListener('click', function () {
      const name = this.dataset.name;
      if (name) filterByCategory(name);
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

  // "更多"按钮
  const btnMore = document.getElementById('btnMore');
  if (btnMore) {
    btnMore.addEventListener('click', () => showToast('更多精选 — 开发中'));
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
      showToast(({map:'地图',message:'消息'}[name]||name) + ' — 功能开发中');
    });
  });

  // 城市选择器 - 点击遮罩关闭
  const overlay = document.getElementById('cityPicker');
  if (overlay) {
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) closeCityPicker();
    });
  }

  // 通知图标
  const notify = document.querySelector('.notification-icon');
  if (notify) notify.parentElement.addEventListener('click', () => showToast('消息通知 — 开发中'));
}

// ---- 工具 ----
function escHtml(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') : ''; }
function fmtCount(n) { return n >= 10000 ? (n/10000).toFixed(1).replace(/\.0$/,'')+'w' : n >= 1000 ? (n/1000).toFixed(1).replace(/\.0$/,'')+'k' : String(n||0); }
