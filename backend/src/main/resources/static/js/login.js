/* ============================================================
   city-review 登录逻辑 — Bug A & B 已修复
   ============================================================ */

let codeTimer = null;
let countdown = 0;

/** 发送验证码（Bug B：已删除冗余 alert 弹窗） */
async function handleSendCode() {
  const phone = document.getElementById('phoneInput').value.trim();
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    showHint('请输入正确的手机号');
    return;
  }
  if (countdown > 0) return;

  try {
    const res = await api.post('/user/code', null, { params: { phone } });
    if (res.data.code === 200) {
      showHint('验证码已发送');
      startCountdown();
    } else {
      showHint(res.data.msg || '发送失败');
    }
  } catch (e) {
    showHint('网络异常，请稍后再试');
  }
}

/** 登录 */
async function handleLogin() {
  const phone = document.getElementById('phoneInput').value.trim();
  const code = document.getElementById('codeInput').value.trim();

  if (!phone) { showHint('请输入手机号'); return; }
  if (!code) { showHint('请输入验证码'); return; }

  const btn = document.getElementById('btnLogin');
  btn.disabled = true;
  btn.textContent = '登录中...';

  try {
    const res = await api.post('/user/login', { phone, code });
    if (res.data.code === 200) {
      // Bug A 修复：登录成功存入 localStorage
      const token = res.data.data;
      localStorage.setItem('token', token);
      showHint('登录成功，跳转中...');
      setTimeout(() => { window.location.href = 'index.html'; }, 500);
    } else {
      showHint(res.data.msg || '登录失败');
    }
  } catch (e) {
    showHint('网络异常，请稍后再试');
  } finally {
    btn.disabled = false;
    btn.textContent = '登 录';
  }
}

/** 验证码倒计时 */
function startCountdown() {
  countdown = 60;
  const btn = document.getElementById('btnCode');
  btn.disabled = true;

  codeTimer = setInterval(() => {
    countdown--;
    if (countdown <= 0) {
      clearInterval(codeTimer);
      btn.textContent = '获取验证码';
      btn.disabled = false;
    } else {
      btn.textContent = countdown + 's';
    }
  }, 1000);
}

/** 轻提示 */
function showHint(msg) {
  const el = document.getElementById('codeHint');
  if (!el) return;
  el.textContent = msg;
  el.style.opacity = '1';
  setTimeout(() => { el.style.opacity = '0'; }, 2500);
}
