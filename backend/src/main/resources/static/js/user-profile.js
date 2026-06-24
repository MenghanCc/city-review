/* ============================================================
   city-review 用户主页
   ============================================================ */

var params = new URLSearchParams(window.location.search);
var userId = params.get('userId');
var currentPage = 1;
var totalPages = 1;
var allBlogs = [];
var userData = null;

var myId = null;

// 获取当前登录用户 ID（调用 /api/user/me）
function fetchMyId(callback) {
  if (!localStorage.getItem('token')) { callback(); return; }
  api.get('/user/me').then(function (res) {
    if (res.data.code === 200 && res.data.data) {
      myId = res.data.data.id;
    }
    callback();
  }).catch(function () { callback(); });
}

(function () {
  if (!userId) {
    document.body.innerHTML = '<p style="text-align:center;padding:80px 0;color:#999;">用户不存在</p>';
    return;
  }

  // 先检查是否自己主页，再加载数据
  fetchMyId(function () {
    if (myId == userId) {
      var bar = document.querySelector('.up-bottom-bar');
      if (bar) bar.style.display = 'none';
    }
  });

  loadUserProfile();
  loadBlogs(1);
})();

// ---- 用户资料 ----
function loadUserProfile() {
  api.get('/user/profile/' + userId).then(function (res) {
    if (res.data.code !== 200 || !res.data.data) return;
    userData = res.data.data;
    renderProfile(userData);
  }).catch(function () {});
}

function renderProfile(d) {
  document.getElementById('upNickname').textContent = d.nickname || '用户主页';
  document.getElementById('upAvatar').src = d.avatar || '/imgs/default-avatar.svg';
  document.getElementById('upName').textContent = d.nickname || '未知';
  document.getElementById('upAvatar').onerror = function () {
    this.src = '/imgs/default-avatar.svg';
  };

  // 性别
  var genderEl = document.getElementById('upGender');
  if (d.gender === true || d.gender === '1') {
    genderEl.textContent = '♀';
    genderEl.className = 'up-gender female';
  } else if (d.gender === false || d.gender === '0') {
    genderEl.textContent = '♂';
    genderEl.className = 'up-gender';
  } else {
    genderEl.style.display = 'none';
  }

  // 生日 + 所在地
  var metaParts = [];
  if (d.birthday) metaParts.push(d.birthday);
  if (d.city) metaParts.push(d.city);
  document.getElementById('upMeta').textContent = metaParts.join(' · ') || '';

  // 简介
  document.getElementById('upBio').textContent = d.introduce || '';

  // 统计数据
  document.getElementById('upFollowCount').textContent = d.followCount || 0;
  document.getElementById('upFansCount').textContent = d.fansCount || 0;
  document.getElementById('upLikeCount').textContent = d.likeCount || 0;

  // 关注按钮
  updateFollowBtn(d.isFollowed);
}

// ---- 关注/取关 ----
function updateFollowBtn(isFollowed) {
  var btn = document.getElementById('upFollowBtn');
  if (!btn) return;
  if (isFollowed) {
    btn.innerHTML = '<i class="fas fa-check"></i> 已关注';
    btn.classList.add('following');
  } else {
    btn.innerHTML = '<i class="fas fa-user-plus"></i> 关注';
    btn.classList.remove('following');
  }
}

function toggleFollow() {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  if (!userData) return;
  if (myId == userId) { showToast('不能关注自己'); return; }

  var isFollowed = userData.isFollowed;
  var action = isFollowed ? false : true;

  api.put('/follow/' + userId + '/' + action).then(function (res) {
    if (res.data.code === 200) {
      userData.isFollowed = !isFollowed;
      userData.fansCount += userData.isFollowed ? 1 : -1;
      document.getElementById('upFansCount').textContent = userData.fansCount;
      updateFollowBtn(userData.isFollowed);
      showToast(isFollowed ? '已取消关注' : '关注成功');
    } else if (res.data.code === 401) {
      showToast('请先登录');
    }
  }).catch(function () { showToast('操作失败'); });
}

// ---- 跳转 ----
function goFollowList(type) {
  window.location.href = 'follow-list.html?userId=' + userId + '&type=' + type;
}

function goChat() {
  if (!localStorage.getItem('token')) { showToast('请先登录'); return; }
  window.location.href = 'private-chat.html?userId=' + userId;
}

function goDetail(blogId) {
  window.location.href = 'detail.html?id=' + blogId;
}

// ---- 笔记列表 ----
function loadBlogs(page) {
  api.get('/blog/user/' + userId, { params: { page: page, size: 10 } }).then(function (res) {
    if (res.data.code === 200 && res.data.data) {
      var data = res.data.data;
      totalPages = data.pages || 1;
      if (page === 1) {
        allBlogs = data.records || [];
      } else {
        allBlogs = allBlogs.concat(data.records || []);
      }
      currentPage = page;
      renderBlogs();
    }
  }).catch(function () {});
}

function renderBlogs() {
  var container = document.getElementById('upBlogsGrid');
  var titleEl = document.getElementById('upBlogsTitle');
  var loadMoreEl = document.getElementById('upLoadMore');
  var total = allBlogs.length;

  titleEl.textContent = 'Ta 的笔记（共 ' + total + ' 篇）';

  if (total === 0) {
    container.innerHTML =
      '<div class="up-empty"><i class="far fa-file-alt"></i><p>Ta 还没有发布过笔记</p></div>';
    loadMoreEl.style.display = 'none';
    return;
  }

  container.innerHTML = allBlogs.map(function (b) {
    // 首图作为封面
    var cover = '/imgs/default-avatar.svg';
    if (b.images) {
      var first = b.images.split(',')[0];
      if (first) cover = first;
    }

    return '<div class="up-blog-card" onclick="goDetail(' + b.id + ')">' +
      '<img class="up-blog-thumb" src="' + cover + '" onerror="this.src=\'/imgs/default-avatar.svg\'">' +
      '<div class="up-blog-body">' +
        '<div class="up-blog-title">' + escHtml(b.title || '无标题') + '</div>' +
        '<div class="up-blog-footer">' +
          '<span class="' + (b.isLiked ? 'liked' : '') + '">' +
            '<i class="' + (b.isLiked ? 'fas' : 'far') + ' fa-heart"></i> ' + (b.liked || 0) +
          '</span>' +
          '<span>💬 ' + (b.comments || 0) + '</span>' +
        '</div>' +
      '</div>' +
    '</div>';
  }).join('');

  // 加载更多
  if (currentPage < totalPages) {
    loadMoreEl.style.display = 'block';
  } else {
    loadMoreEl.style.display = total > 10 ? 'block' : 'none';
    if (loadMoreEl.querySelector('button')) {
      loadMoreEl.querySelector('button').textContent = '已加载全部';
    }
  }
}

function loadMore() {
  if (currentPage >= totalPages) return;
  loadBlogs(currentPage + 1);
}

function escHtml(s) {
  return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
}
