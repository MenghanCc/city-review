/* ============================================================
   city-review 笔记列表页
   mode=my   → 我的点评（当前用户的帖子）
   否则      → 城市精选笔记
   ============================================================ */

const params = new URLSearchParams(window.location.search);
const isMyMode = params.get('mode') === 'my';
let city = params.get('city') || localStorage.getItem('city') || '武汉';
localStorage.setItem('city', city);

let page = 1;
const SIZE = 10;
let totalPages = 1;
let allItems = [];
let currentSort = 'time';

document.addEventListener('DOMContentLoaded', async () => {
  if (isMyMode) {
    document.getElementById('headerCity').textContent = '我的点评';
    document.getElementById('sortBar').style.display = 'none';
  } else {
    document.getElementById('headerCity').textContent = '精选笔记 · ' + city;
  }
  await loadPage(1);
});

function switchSort(sort) {
  if (isMyMode || currentSort === sort) return;
  currentSort = sort;
  document.querySelectorAll('.sort-item').forEach(function(el) {
    el.classList.toggle('active', el.dataset.sort === sort);
  });
  allItems = [];
  loadPage(1);
}

async function loadPage(p) {
  const container = document.getElementById('blogContainer');
  if (p === 1) container.innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">加载中...</p>';

  try {
    var res;
    if (isMyMode) {
      res = await api.get('/blog/of/me', { params: { current: p } });
    } else {
      res = await api.get('/blog/list', { params: { city: city, page: p, size: SIZE, sort: currentSort } });
    }
    if (res.data.code !== 200 || !res.data.data) return;
    var data = res.data.data;
    if (isMyMode) {
      // /of/me 直接返回记录数组
      totalPages = 1;
      if (p === 1) allItems = Array.isArray(data) ? data : (data.records || []);
      else allItems = allItems.concat(Array.isArray(data) ? data : (data.records || []));
    } else {
      totalPages = data.pages || 1;
      if (p === 1) allItems = data.records || [];
      else allItems = allItems.concat(data.records || []);
    }
    page = p;
    render();
  } catch (e) {
    if (p === 1) container.innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">加载失败</p>';
  }
}

function render() {
  const container = document.getElementById('blogContainer');
  if (allItems.length === 0) {
    container.innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">' + (isMyMode ? '你还没有发布过笔记' : city + '暂无笔记，去其他城市看看吧') + '</p>';
    document.getElementById('loadMoreWrap').style.display = 'none';
    return;
  }
  container.innerHTML = allItems.map(b => `
    <div class="blog-item" onclick="window.location.href='detail.html?id=${b.id}'">
      <div class="blog-header">
        <img class="blog-avatar" src="${b.avatar || b.icon || '/imgs/default-avatar.svg'}" onerror="this.src='/imgs/default-avatar.svg'" alt="" style="width:36px;height:36px;border-radius:50%;object-fit:cover;">
        <div>
          <div class="blog-username">${escHtml(b.nickname || b.name || '匿名')}</div>
          <div class="blog-shop"><i class="fas fa-store"></i> ${escHtml(b.shopName || '')}</div>
        </div>
      </div>
      <div class="blog-title">${escHtml(b.title)}</div>
      <div class="blog-summary">${escHtml(b.content || '')}</div>
      ${renderImgs(b.images)}
      <div class="blog-footer">
        <span class="${b.isLiked ? 'liked' : ''}" onclick="event.stopPropagation();handleLike(${b.id})"><i class="${b.isLiked ? 'fas' : 'far'} fa-heart"></i> ${b.liked || 0}</span>
        <span><i class="far fa-comment-dots"></i> ${b.comments || 0}</span>
        <span style="margin-left:auto;font-size:11px;">${(b.createTime || '').substring(0,10)}</span>
      </div>
    </div>
  `).join('');

  // 加载更多按钮（我的模式不显示）
  const wrap = document.getElementById('loadMoreWrap');
  if (isMyMode) { wrap.style.display = 'none'; return; }
  if (page >= totalPages) {
    wrap.style.display = allItems.length > SIZE ? 'block' : 'none';
    if (wrap.children[0]) wrap.children[0].textContent = '已加载全部';
  } else {
    wrap.style.display = 'block';
    if (wrap.children[0]) wrap.children[0].textContent = '加载更多';
  }
}

var FB = 'data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%22120%22%20height=%22120%22%3E%3Crect%20fill=%22%23F0F0F0%22%20width=%22120%22%20height=%22120%22/%3E%3Ctext%20x=%2260%22%20y=%2270%22%20text-anchor=%22middle%22%20font-size=%2240%22%3E%F0%9F%96%BC%3C/text%3E%3C/svg%3E';

function renderImgs(imagesStr) {
  if (!imagesStr) return '';
  var imgs = imagesStr.split(',').filter(Boolean).slice(0, 3);
  if (!imgs.length) return '';
  var html = '<div class="blog-images">';
  imgs.forEach(function(u) {
    html += '<img class="blog-img" src="' + u + '" loading="lazy" onerror="this.onerror=null;this.src=\'' + FB + '\'">';
  });
  html += '</div>';
  return html;
}

async function handleLike(blogId) {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  try {
    var res = await api.put('/blog/like/' + blogId);
    if (res.data.code === 200) {
      var blog = allItems.find(function(b) { return b.id === blogId; });
      if (blog) {
        blog.isLiked = !blog.isLiked;
        blog.liked = (blog.liked || 0) + (blog.isLiked ? 1 : -1);
      }
      render();
    }
  } catch (e) { showToast('操作失败'); }
}

function loadNextPage() {
  if (page >= totalPages) return;
  loadPage(page + 1);
}

function escHtml(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') : ''; }
