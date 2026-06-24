/* ============================================================
   city-review 私聊界面 — 独立的一对一聊天
   ============================================================ */

var params = new URLSearchParams(window.location.search);
var peerId = params.get('userId');
var peerName = '';
var myId = null;
(function () {
  if (!localStorage.getItem('token')) {
    window.location.href = 'login.html';
    return;
  }
  if (!peerId) {
    document.getElementById('chatMsgs').innerHTML =
      '<p style="text-align:center;padding:80px 0;color:#999;">缺少用户参数</p>';
    return;
  }

  // 获取当前用户 ID
  api.get('/user/me').then(function (res) {
    if (res.data.code === 200 && res.data.data) {
      myId = res.data.data.id;
    }
  });

  // 获取对方昵称
  api.get('/user/profile/' + peerId).then(function (res) {
    if (res.data.code === 200 && res.data.data) {
      peerName = res.data.data.nickname || '聊天';
    }
    document.getElementById('chatPeerName').textContent = peerName;
  }).catch(function () {
    document.getElementById('chatPeerName').textContent = '私聊';
  });

  loadMessages(false);

  // 回车发送
  document.getElementById('chatInput').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') sendMsg();
  });

  // 长轮询：等待通知 → 拉新消息 → 继续等
  (function pollLoop() {
    api.get('/messages/poll', { timeout: 35000 }).then(function (res) {
      if (res.data.code === 200) {
        loadMessages(true);
      }
    }).catch(function () {}).then(function () {
      pollLoop();
    });
  })();
})();

function loadMessages(silent) {
  api.get('/messages/with/' + peerId).then(function (res) {
    if (res.data.code === 200) {
      renderMessages(res.data.data || [], silent);
    }
  }).catch(function () {});
}

function renderMessages(msgs, silent) {
  var container = document.getElementById('chatMsgs');
  var wasAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 80;

  var html = '';
  var lastDate = '';

  msgs.forEach(function (m) {
    var dateStr = (m.createdAt || '').substring(0, 10);
    if (dateStr !== lastDate) {
      html += '<div class="msg-time">' + dateStr + '</div>';
      lastDate = dateStr;
    }

    var isMine = m.fromUserId == myId;
    html += '<div class="msg-row ' + (isMine ? 'mine' : 'peer') + '">';
    if (!isMine) {
      html += '<img class="msg-avatar" src="' + (m.fromAvatar || '/imgs/default-avatar.svg') + '" onerror="this.src=\'/imgs/default-avatar.svg\'">';
    }
    html += '<div class="msg-bubble">' + escHtml(m.content) + '</div>';
    if (isMine) {
      html += '<img class="msg-avatar" src="' + (m.fromAvatar || '/imgs/default-avatar.svg') + '" onerror="this.src=\'/imgs/default-avatar.svg\'">';
    }
    html += '</div>';
  });

  container.innerHTML = html || '<p style="text-align:center;padding:60px 0;color:#BBB;">暂无消息，打个招呼吧~</p>';

  if (!silent || wasAtBottom) {
    container.scrollTop = container.scrollHeight;
  }
}

function sendMsg() {
  var input = document.getElementById('chatInput');
  var content = input.value.trim();
  if (!content) return;

  var btn = document.getElementById('chatSendBtn');
  btn.disabled = true;

  api.post('/messages', { toUserId: Number(peerId), content: content }).then(function (res) {
    if (res.data.code === 200) {
      input.value = '';
      loadMessages(false);
    } else {
      showToast(res.data.msg || '发送失败');
    }
  }).catch(function () {
    showToast('发送失败');
  }).then(function () {
    btn.disabled = false;
  });
}

function escHtml(s) {
  return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
}
