/* ============================================================
   city-review 写点评页
   图片上传仿照 info.js 头像上传模式：
   - opacity:0 覆盖层触发文件选择
   - 不显式设置 Content-Type，让浏览器自动带 boundary
   - 单文件逐一上传
   ============================================================ */

var params = new URLSearchParams(window.location.search);
var shopId = params.get('shopId');

var currentScore = 0;        // 当前评分 0.5 ~ 5.0
var uploadedImages = [];     // 已上传图片 URL 列表

// ---- 初始化 ----
(function () {
  if (!shopId) {
    showToast('商户信息缺失');
    setTimeout(function () { history.back(); }, 1500);
    return;
  }

  if (!localStorage.getItem('token')) {
    showToast('请先登录');
    setTimeout(function () { window.location.href = 'login.html'; }, 1500);
    return;
  }

  loadShopInfo();
  bindStarEvents();

  // 仿头像上传：addEventListener('change') 绑定文件选择
  var fileInput = document.getElementById('wbFileInput');
  if (fileInput) {
    fileInput.addEventListener('change', handleImagesUpload);
  }
})();

// ---- 加载商户信息 ----
function loadShopInfo() {
  api.get('/shop/' + shopId).then(function (res) {
    if (res.data.code === 200) {
      var shop = res.data.data;
      document.getElementById('wbShopName').textContent = shop.name || '未知商户';
      document.getElementById('wbShopAddr').textContent = (shop.area || '') + ' ' + (shop.address || '');
      var img = document.getElementById('wbShopImg');
      if (shop.coverImg) {
        img.src = shop.coverImg;
      } else if (shop.images) {
        var firstImg = shop.images.split(',')[0];
        img.src = firstImg || '/imgs/default-avatar.svg';
      } else {
        img.src = '/imgs/default-avatar.svg';
      }
    }
  }).catch(function () {
    document.getElementById('wbShopName').textContent = '加载失败';
  });
}

// ---- 星级评分 ----
function bindStarEvents() {
  var stars = document.querySelectorAll('#wbStars .star-item');
  var scoreText = document.getElementById('wbScoreText');
  var scoreLabels = ['', '很差', '一般', '还行', '推荐', '力荐'];

  stars.forEach(function (star) {
    star.addEventListener('mousemove', function (e) {
      var rect = star.getBoundingClientRect();
      var half = e.clientX - rect.left < rect.width / 2;
      var base = parseInt(star.dataset.star);
      var hoverScore = half ? base - 0.5 : base;
      highlightStars(hoverScore);
    });

    star.addEventListener('click', function (e) {
      var rect = star.getBoundingClientRect();
      var half = e.clientX - rect.left < rect.width / 2;
      var base = parseInt(star.dataset.star);
      currentScore = half ? base - 0.5 : base;

      highlightStars(currentScore);
      var labelIdx = Math.ceil(currentScore);
      scoreText.textContent = currentScore + '分 · ' + scoreLabels[labelIdx];
    });

    star.addEventListener('mouseleave', function () {
      highlightStars(currentScore);
      if (currentScore > 0) {
        var labelIdx = Math.ceil(currentScore);
        scoreText.textContent = currentScore + '分 · ' + scoreLabels[labelIdx];
      } else {
        scoreText.textContent = '轻点星星来评分';
      }
    });
  });
}

function highlightStars(score) {
  var stars = document.querySelectorAll('#wbStars .star-item');
  stars.forEach(function (star) {
    var base = parseInt(star.dataset.star);
    if (base <= score) {
      star.className = 'star-item fas fa-star';
    } else if (base - 0.5 === score || (score % 1 !== 0 && base === Math.ceil(score))) {
      star.className = 'star-item fas fa-star-half-alt';
    } else {
      star.className = 'star-item far fa-star';
    }
  });
}

// ---- 图片上传（仿头像上传模式） ----
async function handleImagesUpload(event) {
  var files = Array.from(event.target.files);
  if (!files || files.length === 0) return;

  var remaining = 9 - uploadedImages.length;
  if (files.length > remaining) {
    showToast('最多上传9张图片，还可上传' + remaining + '张');
    event.target.value = '';
    return;
  }

  // 逐一上传（单文件接口，与头像上传一致）
  for (var i = 0; i < files.length; i++) {
    var file = files[i];

    // 大小校验：10MB
    if (file.size > 10 * 1024 * 1024) {
      showToast(file.name + ' 超过10MB，已跳过', 'error');
      continue;
    }

    // 类型校验
    if (!file.type.startsWith('image/')) {
      showToast(file.name + ' 不是图片文件，已跳过', 'error');
      continue;
    }

    // FormData（不设 Content-Type，浏览器自动带 boundary）
    var formData = new FormData();
    formData.append('file', file);

    try {
      var res = await api.post('/upload/image', formData);
      if (res.data.code === 200 && res.data.data) {
        uploadedImages.push(res.data.data);
        renderImagePreviews();
      } else {
        showToast(res.data.msg || file.name + ' 上传失败');
      }
    } catch (e) {
      showToast(file.name + ' 上传失败，请重试');
    }
  }

  event.target.value = '';
}

function renderImagePreviews() {
  var container = document.getElementById('wbImages');

  // 清空预览区，保留添加按钮
  var previews = container.querySelectorAll('.wb-img-preview');
  previews.forEach(function (el) { el.remove(); });

  // 渲染缩略图
  uploadedImages.forEach(function (url, idx) {
    var div = document.createElement('div');
    div.className = 'wb-img-preview';
    div.innerHTML =
      '<img src="' + url + '" alt="">' +
      '<span class="wb-img-del" onclick="removeImage(' + idx + ', event)">×</span>';
    // 插入到添加按钮之前
    var addWrap = container.querySelector('.wb-img-add-wrap');
    container.insertBefore(div, addWrap);
  });

  // 超过9张隐藏添加按钮
  var addWrap = container.querySelector('.wb-img-add-wrap');
  if (addWrap) {
    addWrap.style.display = uploadedImages.length >= 9 ? 'none' : '';
  }
}

function removeImage(idx, event) {
  if (event) event.stopPropagation();
  uploadedImages.splice(idx, 1);
  renderImagePreviews();
}

// ---- 发布点评 ----
function publishBlog() {
  if (!currentScore) {
    showToast('请先评分');
    return;
  }

  var title = document.getElementById('wbTitle').value.trim();
  var content = document.getElementById('wbContent').value.trim();

  if (!content) {
    showToast('请输入点评内容');
    return;
  }

  var btn = document.getElementById('btnPublish');
  var oldText = btn.textContent;
  btn.textContent = '发布中...';
  btn.style.pointerEvents = 'none';

  var data = {
    shopId: Number(shopId),
    score: currentScore,
    title: title || '',
    content: content,
    images: uploadedImages.join(',')
  };

  api.post('/blog', data).then(function (res) {
    if (res.data.code === 200) {
      showToast('发布成功！');
      setTimeout(function () {
        window.location.href = 'shop-detail.html?id=' + shopId;
      }, 800);
    } else if (res.data.code === 401) {
      showToast('请先登录');
      btn.textContent = oldText;
      btn.style.pointerEvents = 'auto';
    } else {
      showToast(res.data.msg || '发布失败');
      btn.textContent = oldText;
      btn.style.pointerEvents = 'auto';
    }
  }).catch(function () {
    showToast('发布失败，请重试');
    btn.textContent = oldText;
    btn.style.pointerEvents = 'auto';
  });
}
