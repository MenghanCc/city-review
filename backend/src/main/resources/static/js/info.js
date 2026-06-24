/* ============================================================
   city-review 个人信息编辑页 — 可编辑保存
   ============================================================ */

let currentUserId = null;

// 头像上传 — 直接绑定 change 事件
document.addEventListener('DOMContentLoaded', function() {
  var input = document.getElementById('avatarInput');
  if (input) input.addEventListener('change', handleAvatarUpload);
});

(async function () {
  if (!requireAuth()) return;
  await initApp();

  var user = getUser();
  if (!user) {
    window.location.href = 'login.html';
    return;
  }

  currentUserId = user.id;

  // 从缓存渲染基础信息
  document.getElementById('infoId').textContent = user.id;
  renderAvatar(user.icon);
  document.getElementById('editNickname').value = user.nickName || '';

  // 从后端加载 UserInfo
  try {
    var res = await api.get('/user/info/' + user.id);
    if (res.data.code === 200 && res.data.data) {
      var info = res.data.data;
      document.getElementById('editGender').value =
        info.gender === true ? '1' : info.gender === false ? '0' : '';
      document.getElementById('editBirthday').value = info.birthday || '';
      document.getElementById('editCity').value = info.city || '';
      document.getElementById('editIntro').value = info.introduce || '';
    }
  } catch (e) {
    console.warn('加载用户详情失败', e);
  }
})();

function renderAvatar(icon) {
  var el = document.getElementById('infoAvatar');
  var src = icon || '/imgs/default-avatar.svg';
  el.style.backgroundImage = 'url(' + src + ')';
  el.style.backgroundSize = 'cover';
  el.style.backgroundPosition = 'center';
  el.textContent = '';
}

/** 上传头像 */
async function handleAvatarUpload(event) {
  const file = event.target.files[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) { showToast('头像不能超过2MB', 'error'); return; }

  const formData = new FormData();
  formData.append('file', file);

  try {
    const res = await api.post('/user/avatar', formData);
    if (res.data.code === 200) {
      renderAvatar(res.data.data);
      showToast('头像更新成功', 'success');
    } else {
      showToast(res.data.msg || '上传失败', 'error');
    }
  } catch (e) {
    showToast('上传失败', 'error');
  }
}

/** 保存 */
async function handleSave() {
  const btn = document.getElementById('btnSave');
  btn.classList.add('saving');
  btn.textContent = '保存中...';

  const body = {
    nickName: document.getElementById('editNickname').value.trim(),
    gender: document.getElementById('editGender').value,
    birthday: document.getElementById('editBirthday').value,
    city: document.getElementById('editCity').value.trim(),
    introduce: document.getElementById('editIntro').value.trim()
  };

  try {
    const res = await api.put('/user/profile', body);
    if (res.data.code === 200) {
      showToast('保存成功', 'success');
      setTimeout(() => { window.location.href = 'my.html'; }, 800);
    } else {
      showToast(res.data.msg || '保存失败', 'error');
    }
  } catch (e) {
    showToast('网络异常，请稍后再试', 'error');
  } finally {
    btn.classList.remove('saving');
    btn.textContent = '保存';
  }
}
