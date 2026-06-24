/* ============================================================
   city-review 笔记详情页 — 真实评论功能
   ============================================================ */

var params = new URLSearchParams(window.location.search);
var blogId = params.get('id');

var detailData = null;
var commentList = [];  // 从数据库加载的真实评论列表

(async function () {
  if (!blogId) {
    document.getElementById('detailContent').innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">笔记不存在</p>';
    return;
  }

  try {
    var res = await api.get('/blog/' + blogId);
    if (res.data.code === 200) {
      detailData = res.data.data;
      renderDetail();
      loadComments();
    } else {
      document.getElementById('detailContent').innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">' + (res.data.msg || '笔记不存在') + '</p>';
    }
  } catch (e) {
    document.getElementById('detailContent').innerHTML = '<p style="text-align:center;padding:60px 0;color:#999;">加载失败</p>';
  }

  // 分享按钮
  document.getElementById('detailShare').addEventListener('click', function () {
    showToast('分享功能开发中');
  });

  // 回车发送评论
  document.getElementById('commentInput').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') sendComment();
  });
})();

// ==================== 评论加载（真实 API） ====================
function loadComments() {
  api.get('/comments/blog/' + blogId).then(function (res) {
    if (res.data.code === 200) {
      commentList = res.data.data || [];
    } else {
      commentList = [];
    }
    renderComments();
  }).catch(function () {
    commentList = [];
    renderComments();
  });
}

// ==================== 发送评论 ====================
function sendComment() {
  var input = document.getElementById('commentInput');
  var content = input.value.trim();
  if (!content) {
    showToast('请输入评论');
    return;
  }
  if (!localStorage.getItem('token')) {
    showToast('请先登录');
    return;
  }

  var btn = document.getElementById('commentSendBtn');
  btn.disabled = true;
  btn.textContent = '发送中...';

  api.post('/comments', { blogId: Number(blogId), content: content }).then(function (res) {
    if (res.data.code === 200) {
      input.value = '';
      // 重新加载评论列表，获取最新的真实数据
      loadComments();
    } else if (res.data.code === 401) {
      showToast('请先登录');
    } else {
      showToast(res.data.msg || '评论失败');
    }
  }).catch(function () {
    showToast('评论失败，请重试');
  }).then(function () {
    btn.disabled = false;
    btn.textContent = '发送';
  });
}

// ==================== 渲染笔记详情 ====================
function renderDetail() {
  if (!detailData) return;
  var d = detailData;

  document.getElementById('detailTitle').textContent = d.title || '笔记详情';

  var imgsHtml = renderDetailImages(d.images);
  var liked = d.isLiked ? 'liked' : '';
  var heartIcon = d.isLiked ? 'fas fa-heart' : 'far fa-heart';

  document.getElementById('detailContent').innerHTML =
    '<div class="detail-body">' +
      '<div class="author-row">' +
        '<img class="author-avatar" src="' + (d.icon || '/imgs/default-avatar.svg') + '" onerror="this.src=\'/imgs/default-avatar.svg\'" alt="" ' +
          'onclick="event.stopPropagation();window.location.href=\'user-profile.html?userId=' + (d.userId || '') + '\'" style="cursor:pointer;">' +
        '<div>' +
          '<div class="author-name" onclick="event.stopPropagation();window.location.href=\'user-profile.html?userId=' + (d.userId || '') + '\'" style="cursor:pointer;">' + escHtml(d.name || '匿名') + '</div>' +
          '<div class="author-shop" onclick="event.stopPropagation();window.location.href=\'shop-detail.html?id=' + d.shopId + '\'" style="cursor:pointer;text-decoration:underline;">' +
            '<i class="fas fa-store"></i> ' + escHtml(d.shopName || '未知商户') + ' &nbsp;<span style="font-size:11px;">查看商户 ›</span>' +
          '</div>' +
        '</div>' +
      '</div>' +

      (d.title ? '<h2 class="detail-title">' + escHtml(d.title) + '</h2>' : '') +
      (d.content ? '<div class="detail-text">' + escHtml(d.content) + '</div>' : '') +
      imgsHtml +

      '<div class="detail-actions">' +
        '<div class="detail-action ' + liked + '" onclick="toggleLike()">' +
          '<i class="' + heartIcon + '"></i>' +
          '<span id="likeCount">' + (d.liked || 0) + '</span>' +
        '</div>' +
        '<div class="detail-action" onclick="document.getElementById(\'commentInput\').focus()">' +
          '<i class="far fa-comment-dots"></i>' +
          '<span id="commentCount">' + (d.comments || 0) + '</span>' +
        '</div>' +
      '</div>' +
    '</div>';
}

function renderDetailImages(imagesStr) {
  if (!imagesStr) return '';
  var imgs = imagesStr.split(',').filter(Boolean);
  if (imgs.length === 0) return '';
  return '<div class="detail-images">' +
    imgs.map(function (u) {
      return '<img class="detail-image-item" src="' + u + '" loading="lazy" onerror="this.src=\'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22200%22%3E%3Crect fill=%22%23F0F0F0%22 width=%22200%22 height=%22200%22/%3E%3Ctext x=%22100%22 y=%22110%22 text-anchor=%22middle%22 font-size=%2260%22%3E🖼%3C/text%3E%3C/svg%3E\'">';
    }).join('') +
  '</div>';
}

// ==================== 渲染评论列表（真实数据） ====================
function renderComments() {
  var container = document.getElementById('commentSection');
  if (!container) return;

  var commentCount = commentList.length;

  // 更新顶部评论计数（ID 为 commentCount）
  var countEl = document.getElementById('commentCount');
  if (countEl) countEl.textContent = commentCount;

  var html = '<div class="comment-section">';
  html += '<div class="comment-title"><i class="far fa-comment-dots"></i> 评论 ' + commentCount + '</div>';

  if (commentList.length === 0) {
    html += '<div class="comment-empty">暂无评论，来说两句吧~</div>';
  } else {
    commentList.forEach(function (c) {
      var avatarSrc = c.userAvatar || '/imgs/default-avatar.svg';
      html += '<div class="comment-item">' +
        '<img class="comment-avatar" src="' + avatarSrc + '" onerror="this.src=\'/imgs/default-avatar.svg\'" alt="">' +
        '<div class="comment-body">' +
          '<div class="comment-nick">' + escHtml(c.userNickname || '匿名') + '</div>' +
          '<div class="comment-text">' + escHtml(c.content) + '</div>' +
          '<div class="comment-time">' + timeAgo(new Date(c.createdAt)) + '</div>' +
        '</div>' +
      '</div>';
    });
  }

  html += '</div>';
  container.innerHTML = html;
}

// ==================== 时间格式 ====================
function timeAgo(date) {
  var seconds = Math.floor((new Date() - date) / 1000);
  if (seconds < 60) return '刚刚';
  var minutes = Math.floor(seconds / 60);
  if (minutes < 60) return minutes + '分钟前';
  var hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + '小时前';
  var days = Math.floor(hours / 24);
  if (days <= 7) return days + '天前';
  var y = date.getFullYear();
  var m = ('0' + (date.getMonth() + 1)).slice(-2);
  var d = ('0' + date.getDate()).slice(-2);
  return y + '-' + m + '-' + d;
}

// ==================== 点赞 ====================
function toggleLike() {
  if (!blogId) return;
  api.put('/blog/like/' + blogId).then(function (res) {
    if (res.data.code === 200) {
      if (detailData) {
        detailData.isLiked = !detailData.isLiked;
        detailData.liked = (detailData.liked || 0) + (detailData.isLiked ? 1 : -1);
      }
      // 更新 UI
      var likeBtn = document.querySelector('.detail-action.liked, .detail-action:first-child');
      var likeCount = document.getElementById('likeCount');
      if (detailData) {
        if (detailData.isLiked) {
          likeBtn.classList.add('liked');
          likeBtn.querySelector('i').className = 'fas fa-heart';
        } else {
          likeBtn.classList.remove('liked');
          likeBtn.querySelector('i').className = 'far fa-heart';
        }
        if (likeCount) likeCount.textContent = detailData.liked;
      }
    } else if (res.data.code === 401) {
      showToast('请先登录');
    }
  }).catch(function () {
    showToast('操作失败');
  });
}

function escHtml(s) { return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : ''; }
