/* ============================================================
   city-review 个人中心 — 用户信息 + 统计数据 + 签到
   ============================================================ */

var myUserId = null;

// ---- 初始化：必须登录 ----
(async function () {
  if (!requireAuth()) return;

  // Call initApp from common.js (loads token, fetches /api/user/me)
  await initApp();
  var u = getUser();
  if (!u) {
    window.location.href = 'login.html';
    return;
  }
  myUserId = u.id;

  loadProfile();
  loadStatistics();
  loadSignCalendar();
})();

/** 加载用户头部信息 */
function loadProfile() {
  const u = getUser();
  if (!u) return;
  document.getElementById('profileName').textContent = u.nickName || '用户' + u.id;
  document.getElementById('profileIntro').textContent = '城市点评用户';
  var avatar = document.getElementById('profileAvatar');
  avatar.style.backgroundImage = 'url(' + (u.icon || '/imgs/default-avatar.svg') + ')';
  avatar.style.backgroundSize = 'cover';
  avatar.style.backgroundPosition = 'center';
  avatar.textContent = '';
}

/** 加载统计数据 */
async function loadStatistics() {
  try {
    const res = await api.get('/user/statistics');
    if (res.data.code === 200 && res.data.data) {
      const d = res.data.data;
      document.getElementById('followCount').textContent = d.followCount || 0;
      document.getElementById('fansCount').textContent = d.fansCount || 0;
      document.getElementById('likeCount').textContent = d.likeCount || 0;
    }
  } catch (e) {
    console.warn('统计数据加载失败', e);
  }
}

/** 签到（Redis BitMap） */
async function handleSign() {
  try {
    const res = await api.post('/user/sign');
    if (res.data.code === 200) {
      const btn = document.getElementById('btnSign');
      btn.classList.add('signed');
      btn.querySelector('.sign-btn-text').textContent = '已签到';
      showToast('签到成功', 'success');
      loadSignStats();
      loadSignCalendar();
    } else {
      showToast(res.data.msg || '签到失败', 'error');
    }
  } catch (e) {
    showToast('签到失败，请稍后再试', 'error');
  }
}

/** 加载连续签到 */
async function loadSignStats() {
  try {
    const res = await api.get('/user/sign/count');
    if (res.data.code === 200) {
      const days = res.data.data || 0;
      const desc = document.getElementById('signDesc');
      if (days > 0) {
        desc.textContent = '已连续签到 ' + days + ' 天';
      }
      const btn = document.getElementById('btnSign');
      if (isTodaySigned()) {
        btn.classList.add('signed');
        btn.querySelector('.sign-btn-text').textContent = '已签到';
      }
    }
  } catch (e) { /* ignore */ }
}

/** 加载签到日历 */
async function loadSignCalendar() {
  try {
    const res = await api.get('/user/sign/calendar');
    if (res.data.code !== 200) return;

    const data = res.data.data;
    if (!data || !data.calendar) return;

    // 更新标题和统计
    document.getElementById('calendarTitle').textContent =
      '签到日历 ' + (data.yearMonth || '');
    document.getElementById('calendarTotal').textContent =
      '本月共 ' + (data.totalSignDays || 0) + ' 天';

    // 计算日历偏移（当月1号是周几）
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).getDay();

    const grid = document.getElementById('calendarGrid');
    let html = '';

    // 空白填充
    for (let i = 0; i < firstDay; i++) {
      html += '<div class="calendar-day empty">0</div>';
    }

    // 每日状态
    const today = data.today;
    data.calendar.forEach((status, idx) => {
      const day = idx + 1;
      const cls = ['calendar-day'];
      if (status === 1) cls.push('signed');
      if (day === today) cls.push('today');
      html += `<div class="${cls.join(' ')}">${day}</div>`;
    });

    grid.innerHTML = html;
    loadSignStats();
  } catch (e) {
    console.warn('签到日历加载失败', e);
  }
}

function isTodaySigned() {
  const days = document.querySelectorAll('.calendar-day.signed');
  const today = document.querySelector('.calendar-day.today');
  return today && today.classList.contains('signed');
}

/** 跳转个人信息详情页 */
function goInfo() {
  window.location.href = 'info.html';
}

/** 跳转我的点评（我发布的探店笔记） */
function goMyBlogs() {
  window.location.href = 'blog.html?mode=my';
}

/** 跳转我的关注/粉丝列表 */
function goMyFollow(type) {
  if (!myUserId) { showToast('请先登录'); return; }
  window.location.href = 'follow-list.html?userId=' + myUserId + '&type=' + type;
}
