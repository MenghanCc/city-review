/* ============================================================
   city-review 公共模块 — Token 持久化 & Axios 封装
   ============================================================ */

// ---- Axios 实例 ----
const api = axios.create({
  baseURL: '/api',
  timeout: 10000
});

// 请求拦截器：自动注入 Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = token;
  return config;
}, err => Promise.reject(err));

// 响应拦截器：401 自动跳登录
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login.html') {
        window.location.href = '/login.html';
      }
    }
    return Promise.reject(err);
  }
);

// ---- 当前用户状态 ----
let currentUser = null;

/** 页面初始化：加载 Token + 尝试获取用户信息 */
async function initApp() {
  const token = localStorage.getItem('token');
  if (!token) return;

  try {
    const res = await api.get('/user/me');
    if (res.data && res.data.code === 200) {
      currentUser = res.data.data;
      console.log('✅ city-review 用户已登录:', currentUser.nickName || currentUser.id);
    }
  } catch (e) {
    // Token 失效，清除
    localStorage.removeItem('token');
    currentUser = null;
  }
}

/** 获取当前用户（同步） */
function getUser() {
  return currentUser;
}

/** 检查登录态，未登录跳转 login.html */
function requireAuth() {
  const token = localStorage.getItem('token');
  if (!token) {
    window.location.href = '/login.html';
    return false;
  }
  return true;
}

/** 退出登录 */
function logout() {
  localStorage.removeItem('token');
  currentUser = null;
  window.location.href = '/login.html';
}

/** Toast 轻提示 — 自动消失，不阻断用户操作 */
function showToast(msg, type) {
  // 移除已有 toast
  const old = document.querySelector('.toast-msg');
  if (old) old.remove();

  const el = document.createElement('div');
  el.className = 'toast-msg' + (type === 'success' ? ' toast-success' : '') + (type === 'error' ? ' toast-error' : '');
  el.textContent = msg;
  document.body.appendChild(el);

  // 立即显示
  el.classList.add('show');

  // 2.5 秒后自动消失
  setTimeout(function () {
    el.classList.remove('show');
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 300);
  }, 2500);
}

console.log('🏙️ city-review common.js 已加载');
