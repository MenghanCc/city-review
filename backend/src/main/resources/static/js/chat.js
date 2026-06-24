/* ============================================================
   city-review 消息页 — 会话列表 + 3s 短轮询
   点击会话 → 跳转 private-chat.html
   ============================================================ */

(function () {
  if (!localStorage.getItem('token')) {
    window.location.href = 'login.html';
    return;
  }
  loadConversations();
  pollLoop();
})();

// 长轮询循环：等待通知 → 拉数据 → 继续等
function pollLoop() {
  api.get('/messages/poll', { timeout: 35000 }).then(function (res) {
    if (res.data.code === 200) {
      loadConversations();
    }
  }).catch(function () {}).then(function () {
    pollLoop();
  });
}

function loadConversations() {
  api.get('/messages/conversations').then(function (res) {
    if (res.data.code === 200) {
      renderConversations(res.data.data || []);
    }
  }).catch(function () {});
}

function renderConversations(list) {
  var container = document.getElementById('convList');
  if (list.length === 0) {
    container.innerHTML =
      '<div class="conv-empty"><i class="far fa-comment-dots"></i><p>暂无消息</p></div>';
    return;
  }

  container.innerHTML = list.map(function (c) {
    var timeStr = c.lastTime ? (c.lastTime + '').substring(5, 16).replace('T', ' ') : '';
    var badgeHtml = c.unreadCount > 0
      ? '<span class="conv-badge">' + (c.unreadCount > 99 ? '99+' : c.unreadCount) + '</span>'
      : '';

    return '<div class="conv-item" onclick="openChat(' + c.userId + ')">' +
      '<img class="conv-avatar" src="' + (c.userAvatar || '/imgs/default-avatar.svg') + '" onerror="this.src=\'/imgs/default-avatar.svg\'">' +
      '<div class="conv-info">' +
        '<div class="conv-name">' + escHtml(c.userNickname || '用户') + '</div>' +
        '<div class="conv-last">' + escHtml(c.lastMessage || '') + '</div>' +
      '</div>' +
      '<div class="conv-meta">' +
        '<div class="conv-time">' + timeStr + '</div>' +
        badgeHtml +
      '</div>' +
    '</div>';
  }).join('');
}

function openChat(peerId) {
  window.location.href = 'private-chat.html?userId=' + peerId;
}

function escHtml(s) {
  return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
}
