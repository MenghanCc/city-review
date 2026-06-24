/* ============================================================
   city-review 店铺列表页
   ============================================================ */

const params = new URLSearchParams(window.location.search);
const city = params.get('city') || localStorage.getItem('city') || '武汉';
localStorage.setItem('city', city);
const category = params.get('category') || '';

document.addEventListener('DOMContentLoaded', async () => {
  // 同步城市到 localStorage
  localStorage.setItem('city', city);

  document.getElementById('headerCity').textContent = '📍 ' + city;
  document.getElementById('headerCategory').textContent = category || '全部商户';
  document.getElementById('pageTitle').textContent = (category || '全部') + '商户';

  await loadShops();
});

async function loadShops() {
  const container = document.getElementById('shopList');
  try {
    const res = await api.get('/shop/list', {
      params: { city: city, category: category || undefined }
    });
    if (res.data.code !== 200 || !res.data.data) {
      container.innerHTML = '<p style="text-align:center;padding:40px;color:#999;">暂无数据</p>';
      return;
    }
    const shops = res.data.data;
    if (shops.length === 0) {
      container.innerHTML = '<p style="text-align:center;padding:40px;color:#999;">该分类暂无商户</p>';
      return;
    }

    container.innerHTML = shops.map(s => {
      const firstImg = (s.images || '').split(',')[0] || '';
      return `
        <div class="shop-item" onclick="goShop(${s.id})">
          ${firstImg ? '<img class="shop-thumb" src="' + firstImg + '" onerror="this.src=\'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2280%22 height=%2280%22%3E%3Crect fill=%22%23F5F5F5%22 width=%2280%22 height=%2280%22/%3E%3Ctext x=%2240%22 y=%2250%22 text-anchor=%22middle%22 font-size=%2230%22%3E🏪%3C/text%3E%3C/svg%3E\'" alt="">' : '<div class="shop-thumb fallback-thumb">🏪</div>'}
          <div class="shop-info">
            <div class="shop-name">${escHtml(s.name)}</div>
            <div class="shop-addr">📍 ${escHtml(s.address||'')}</div>
            <div class="shop-meta">
              <span class="score">⭐ ${((s.score||0)/10).toFixed(1)}</span>
              <span>💰 ¥${s.avgPrice||'-'}/人</span>
              <span>📦 ${s.sold||0}</span>
            </div>
            ${s.area ? '<span class="shop-area">' + escHtml(s.area) + '</span>' : ''}
          </div>
        </div>
      `;
    }).join('');
  } catch (e) {
    container.innerHTML = '<p style="text-align:center;padding:40px;color:#999;">加载失败</p>';
  }
}

function goShop(id) {
  window.location.href = 'shop-detail.html?id=' + id;
}

function escHtml(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') : ''; }
