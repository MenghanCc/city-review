/* ============================================================
   city-review 关注/粉丝列表
   ============================================================ */

var params = new URLSearchParams(window.location.search);
var userId = params.get('userId');
var listType = params.get('type') || 'follow'; // 'follow' | 'fans'

(function () {
  if (!userId) {
    document.getElementById('flList').innerHTML =
      '<p class="fl-empty"><i class="fas fa-exclamation-circle"></i><p>参数错误</p></p>';
    return;
  }
  document.getElementById('flTitle').textContent = listType === 'fans' ? '粉丝列表' : '关注列表';
  loadList();
})();

function loadList() {
  var endpoint = listType === 'fans'
    ? '/follow/fans/' + userId
    : '/follow/list/' + userId;

  api.get(endpoint).then(function (res) {
    if (res.data.code === 200) {
      renderList(res.data.data || []);
    }
  }).catch(function () {
    document.getElementById('flList').innerHTML =
      '<p class="fl-empty">加载失败</p>';
  });
}

function renderList(list) {
  var container = document.getElementById('flList');
  if (!list || list.length === 0) {
    container.innerHTML =
      '<div class="fl-empty"><i class="far fa-user-circle"></i><p>' + (listType === 'fans' ? '暂无粉丝' : '暂未关注任何人') + '</p></div>';
    return;
  }

  container.innerHTML = list.map(function (u) {
    var avatarSrc = u.icon || '';
    return '<div class="fl-item" onclick="goProfile(' + u.id + ')">' +
      (avatarSrc ? '<img class="fl-avatar" src="' + avatarSrc + '" onerror="this.style.display=\'none\'">'
                 : '<div class="fl-avatar fl-avatar-placeholder"><i class="fas fa-user"></i></div>') +
      '<div class="fl-info">' +
        '<div class="fl-name">' + escHtml(u.nickName || '用户') + '</div>' +
        '<div class="fl-bio">' + escHtml(u.introduce || '') + '</div>' +
      '</div>' +
      '<i class="fas fa-chevron-right" style="color:#CCC;font-size:14px;"></i>' +
    '</div>';
  }).join('');
}

function goProfile(id) {
  window.location.href = 'user-profile.html?userId=' + id;
}

function escHtml(s) {
  return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
}
