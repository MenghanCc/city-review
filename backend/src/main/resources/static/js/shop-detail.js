/* ============================================================
   city-review 商户详情页
   ============================================================ */
var params = new URLSearchParams(window.location.search);
var shopId = params.get('id');

if (!shopId) {
  document.body.innerHTML = '<p style="text-align:center;padding:80px 0;color:#999;">商户不存在</p>';
}

(async function() {
  if (!shopId) return;
  try {
    var res = await api.get('/shop/' + shopId);
    if (res.data.code !== 200 || !res.data.data) {
      document.getElementById('sdImages').innerHTML = '<p class="empty-hint">商户不存在</p>';
      return;
    }
    var d = res.data.data;
    renderImages(d);
    renderInfo(d);
    renderBiz(d);
    renderDesc(d);
  } catch(e) {
    document.getElementById('sdImages').innerHTML = '<p class="empty-hint">加载失败</p>';
  }

  // 「大家说」独立加载帖子数据，不依赖商户缓存
  loadBlogs();
  // 加载优惠券
  loadVouchers();
  // 加载商品
  loadProducts();
})();

function renderImages(d) {
  var imgs = (d.images || '').split(',').filter(Boolean);
  if (imgs.length === 0 && d.coverImg) imgs = [d.coverImg];
  if (imgs.length === 0) imgs = ['https://picsum.photos/seed/shop' + d.id + '/800/400'];

  var html = imgs.map(function(u, i) {
    return '<img src="' + u + '" onerror="this.src=\'https://picsum.photos/seed/shopfallback' + d.id + i + '/800/400\'">';
  }).join('');

  var dots = '';
  if (imgs.length > 1) {
    dots = '<div class="sd-img-dots">';
    for (var j = 0; j < imgs.length; j++) {
      dots += '<span class="sd-img-dot' + (j === 0 ? ' active' : '') + '"></span>';
    }
    dots += '</div>';
  }
  document.getElementById('sdImages').innerHTML = html + dots;
}

function renderInfo(d) {
  document.getElementById('shopName').textContent = d.name;
  var starCount = Math.round((d.score || 0) / 10); // 37→3.7→4星, 48→4.8→5星
  var starsHtml = '';
  for (var i = 0; i < 5; i++) {
    starsHtml += i < starCount ? '<i class="fas fa-star"></i>' : '<i class="far fa-star"></i>';
  }
  document.getElementById('sdInfo').innerHTML =
    '<div class="sd-name">' + esc(d.name) + '</div>' +
    '<div class="sd-category">' + esc(d.categoryName || '') + ' · ' + esc(d.city || '') + ' ' + esc(d.area || '') + '</div>' +
    '<div class="sd-score-row"><span class="sd-score-num">' + ((d.score||0)/10).toFixed(1) + '</span><span class="sd-stars">' + starsHtml + '</span></div>' +
    '<div class="sd-price">💰 人均 ¥' + (d.avgPrice || '-') + ' &nbsp;|&nbsp; 📦 已售 ' + (d.sold||0) + '</div>';
}

function renderBiz(d) {
  document.getElementById('sdBiz').innerHTML =
    '<div class="sd-biz-row"><i class="fas fa-phone"></i> ' + esc(d.phone || '暂无') + '<span class="copy-btn" onclick="copyPhone(\'' + (d.phone||'') + '\')">复制</span></div>' +
    '<div class="sd-biz-row"><i class="fas fa-clock"></i> ' + esc(d.openHours || '暂无') + '</div>' +
    '<div class="sd-biz-row"><i class="fas fa-map-marker-alt"></i> ' + esc(d.address || '暂无') + '</div>';
}

function copyPhone(phone) {
  if (!phone) return;
  navigator.clipboard.writeText(phone).then(function() { showToast('已复制'); });
}

function renderDesc(d) {
  var desc = d.description || '';
  if (!desc) return;
  document.getElementById('sdDesc').innerHTML =
    '<div class="sd-desc-title">商户简介</div>' +
    '<div class="sd-desc-text" id="descText">' + esc(desc) + '</div>' +
    '<span class="sd-desc-toggle" onclick="toggleDesc()">展开全部</span>';
}

function toggleDesc() {
  var el = document.getElementById('descText');
  var btn = document.querySelector('.sd-desc-toggle');
  var expanded = el.classList.contains('expanded');
  if (expanded) { el.classList.remove('expanded'); btn.textContent = '展开全部'; }
  else { el.classList.add('expanded'); btn.textContent = '收起'; }
}

// 「大家说」独立加载帖子（不依赖商户缓存）
function loadBlogs() {
  var container = document.getElementById('sdBlogs');
  container.innerHTML = '<p style="text-align:center;color:#999;padding:20px;">加载中...</p>';

  api.get('/blog/shop/' + shopId, { params: { page: 1, size: 20 } }).then(function(res) {
    if (res.data.code === 200 && res.data.data) {
      var blogs = res.data.data.records || res.data.data;
      renderBlogs(blogs);
    } else {
      container.innerHTML = '<p class="empty-hint">暂无探店笔记</p>';
    }
  }).catch(function() {
    container.innerHTML = '<p class="empty-hint">暂无探店笔记</p>';
  });
}

function renderBlogs(blogs) {
  var container = document.getElementById('sdBlogs');
  if (!blogs || blogs.length === 0) {
    container.innerHTML = '<p class="empty-hint">暂无探店笔记，快来写第一条点评吧~</p>';
    return;
  }

  container.innerHTML = blogs.map(function(b) {
    // 首图作为缩略图
    var thumb = '';
    if (b.images) {
      var firstImg = b.images.split(',')[0];
      if (firstImg) {
        thumb = '<img class="sd-blog-thumb" src="' + firstImg + '" onerror="this.style.display=\'none\'">';
      }
    }

    // 内容预览（截取前60字）
    var preview = '';
    if (b.content) {
      preview = b.content.length > 60 ? b.content.substring(0, 60) + '...' : b.content;
    }

    // 评分星星
    var starsHtml = '';
    if (b.score) {
      var starCount = Math.round(b.score);
      for (var i = 0; i < 5; i++) {
        starsHtml += i < starCount ? '<i class="fas fa-star"></i>' : '<i class="far fa-star"></i>';
      }
    }

    return '<div class="sd-blog-item" onclick="window.location.href=\'detail.html?id=' + b.id + '\'">' +
      '<div class="sd-blog-top">' +
        '<img class="sd-blog-avatar" src="' + (b.icon || b.avatar || '/imgs/default-avatar.svg') + '" onerror="this.src=\'/imgs/default-avatar.svg\'">' +
        '<div class="sd-blog-info">' +
          '<div class="sd-blog-nick">' + esc(b.name || b.nickname || '匿名') + '</div>' +
          (b.score ? '<div class="sd-blog-stars">' + starsHtml + ' ' + (b.score*1).toFixed(1) + '</div>' : '') +
        '</div>' +
        '<span class="sd-blog-time">' + timeAgoStr(b.createTime) + '</span>' +
      '</div>' +
      (b.title ? '<div class="sd-blog-title">' + esc(b.title) + '</div>' : '') +
      (preview ? '<div class="sd-blog-preview">' + esc(preview) + '</div>' : '') +
      (thumb ? thumb : '') +
      '<div class="sd-blog-meta">' +
        '<span class="' + (b.isLiked ? 'liked' : '') + '"><i class="' + (b.isLiked ? 'fas' : 'far') + ' fa-heart"></i> ' + (b.liked || 0) + '</span>' +
        ' &nbsp;<i class="far fa-comment-dots"></i> ' + (b.comments || 0) +
      '</div>' +
    '</div>';
  }).join('');
}

function timeAgoStr(dateStr) {
  if (!dateStr) return '';
  var date = new Date(dateStr);
  var now = new Date();
  var seconds = Math.floor((now - date) / 1000);
  if (seconds < 60) return '刚刚';
  var minutes = Math.floor(seconds / 60);
  if (minutes < 60) return minutes + '分钟前';
  var hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + '小时前';
  var days = Math.floor(hours / 24);
  if (days <= 30) return days + '天前';
  return dateStr.substring(0, 10);
}

function goWriteBlog() {
  if (!localStorage.getItem('token')) {
    window.location.href = 'login.html';
    return;
  }
  window.location.href = 'write-blog.html?shopId=' + shopId;
}

// ---- 优惠券 ----
function loadVouchers() {
  api.get('/voucher/list/' + shopId).then(function (res) {
    if (res.data.code === 200) {
      renderVouchers(res.data.data || []);
    }
  }).catch(function () {
    document.getElementById('sdVouchers').innerHTML =
      '<p class="empty-hint">暂无可用优惠券</p>';
  });
}

function renderVouchers(vouchers) {
  var container = document.getElementById('sdVouchers');
  if (!vouchers || vouchers.length === 0) {
    container.innerHTML = '<p class="empty-hint">暂无可用优惠券</p>';
    return;
  }

  container.innerHTML = vouchers.map(function (v) {
    var isSeckill = v.type === 1;
    var badge = isSeckill
      ? '<span class="vc-badge vc-badge-seckill">限时秒杀</span>'
      : '<span class="vc-badge vc-badge-normal">平价券</span>';

    var endStr = v.endTime ? '截止 ' + (v.endTime + '').substring(0, 16).replace('T', ' ') : '';
    var info = isSeckill
      ? '库存: ' + (v.stock || 0) + ' 份 | 每人限购1份 | ' + endStr
      : '无限量 | 随时可买';

    var btnHtml = isSeckill
      ? '<button class="vc-btn vc-btn-seckill" onclick="event.stopPropagation();seckillVoucher(' + v.id + ')">⚡ 立即秒杀</button>'
      : '<button class="vc-btn vc-btn-buy" onclick="event.stopPropagation();buyVoucher(' + v.id + ')">立即购买</button>';

    return '<div class="vc-item">' +
      '<div class="vc-left">' +
        '<div class="vc-price"><span class="vc-symbol">¥</span>' + (v.payValue / 100).toFixed(1) + '</div>' +
        '<div class="vc-discount">省 ¥' + ((v.actualValue - v.payValue) / 100).toFixed(1) + '</div>' +
      '</div>' +
      '<div class="vc-main">' +
        '<div class="vc-title">' + badge + esc(v.title) + '</div>' +
        '<div class="vc-sub">' + esc(v.subTitle || '') + '</div>' +
        '<div class="vc-info">' + info + '</div>' +
        btnHtml +
      '</div>' +
    '</div>';
  }).join('');
}

function buyVoucher(voucherId) {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  showToast('购买功能开发中');
}

function seckillVoucher(voucherId) {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  showToast('秒杀功能开发中');
}

// ---- 商品 ----
function loadProducts() {
  api.get('/products/available', { params: { shopId: shopId } }).then(function (res) {
    if (res.data.code === 200) {
      renderProducts(res.data.data || []);
    }
  }).catch(function () {
    document.getElementById('sdProducts').innerHTML = '<p class="empty-hint">暂无商品</p>';
  });
}

function renderProducts(products) {
  var container = document.getElementById('sdProducts');
  if (!products || products.length === 0) {
    container.innerHTML = '<p class="empty-hint">暂无商品</p>';
    return;
  }

  container.innerHTML = products.map(function (p) {
    var img = p.coverImg || 'https://picsum.photos/seed/prod' + p.id + '/400/300';
    return '<div class="prod-item" onclick="event.stopPropagation();">' +
      '<img class="prod-img" src="' + img + '" onerror="this.src=\'https://picsum.photos/seed/fallback' + p.id + '/400/300\'">' +
      '<div class="prod-body">' +
        '<div class="prod-name">' + esc(p.name) + '</div>' +
        '<div class="prod-desc">' + esc(p.description || '') + '</div>' +
        '<div class="prod-bottom">' +
          '<span class="prod-price">¥' + (p.price * 1).toFixed(2) + '</span>' +
          '<button class="prod-buy-btn" onclick="event.stopPropagation();buyProduct(' + p.id + ')">购买</button>' +
        '</div>' +
      '</div>' +
    '</div>';
  }).join('');
}

function buyProduct(productId) {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  api.post('/products/purchase', { productId: productId }).then(function (res) {
    if (res.data.code === 200) {
      showToast('购买成功！');
    } else {
      showToast(res.data.msg || '购买失败');
    }
  }).catch(function () { showToast('购买失败'); });
}

function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') : ''; }
