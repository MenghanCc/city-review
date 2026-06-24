/* ============================================================
   city-review 附近商户地图页
   高德地图 JS API v2.0 + Redis GEO
   ============================================================ */

// 城市→坐标映射（默认中心）
var CITY_COORDS = {
  '武汉': [114.305, 30.593],
  '北京': [116.407, 39.904],
  '上海': [121.473, 31.230],
  '杭州': [120.155, 30.274],
  '广州': [113.264, 23.129],
  '深圳': [114.058, 22.543],
  '成都': [104.066, 30.573],
  '南京': [118.797, 32.061],
  '重庆': [106.551, 29.563],
  '西安': [108.940, 34.260],
  '长沙': [112.939, 28.228],
  '苏州': [120.585, 31.299]
};

var DEFAULT_ZOOM = 14;

var map = null;
// 当前地图中心（经纬度）
var currentLng = null;
var currentLat = null;
// 当前显示的城市名
var currentCity = null;
var allMarkers = [];
var myLocationMarker = null;
var infoWindow = null;
var nearbyShops = [];

var CAT_COLORS = {
  '美食': '#FF6A00',
  'KTV': '#9B59B6',
  '丽人': '#FF6B81',
  '酒店': '#3498DB',
  '旅游': '#2ECC71',
  '休闲娱乐': '#F39C12',
  '购物': '#E74C3C',
  '运动健身': '#1ABC9C',
  '教育培训': '#2980B9',
  '生活服务': '#95A5A6'
};

(function () {
  if (typeof AMap === 'undefined') {
    document.getElementById('container').innerHTML =
      '<div style="text-align:center;padding:120px 20px;color:#999;">' +
      '<i class="fas fa-exclamation-triangle" style="font-size:40px;display:block;margin-bottom:12px;"></i>' +
      '<p style="margin-bottom:8px;">地图加载失败</p>' +
      '<p style="font-size:12px;">请检查 map.html 中高德地图 API Key 是否已配置</p>' +
      '</div>';
    return;
  }

  // 1. 首页选择的城市 → 永远作为兜底
  setCity(localStorage.getItem('city') || '武汉');

  // 2. 有 GPS 缓存且城市匹配 → 直接精确定位
  var cached = getCachedLocation();
  if (cached && cached.city === currentCity) {
    currentLng = cached.lng;
    currentLat = cached.lat;
    map = new AMap.Map('container', {
      center: [currentLng, currentLat],
      zoom: 15,
      resizeEnable: true
    });
    addMyLocationMarker(currentLng, currentLat);
    loadNearbyShops();
    return;
  }

  // 3. 删掉不匹配的旧缓存
  if (cached) {
    localStorage.removeItem('map_location');
  }

  // 4. 用城市坐标初始化地图（不调 GPS，只等手工点"重新定位"）
  map = new AMap.Map('container', {
    center: [currentLng, currentLat],
    zoom: DEFAULT_ZOOM,
    resizeEnable: true
  });
  loadNearbyShops();
})();

// ---- 切换到指定城市（核心兜底） ----
function setCity(city) {
  currentCity = city;
  var coords = CITY_COORDS[city] || CITY_COORDS['武汉'];
  currentLng = coords[0];
  currentLat = coords[1];
  document.getElementById('mapCity').textContent = city;
}

// ---- 读取缓存的定位 ----
function getCachedLocation() {
  try {
    var raw = localStorage.getItem('map_location');
    if (!raw) return null;
    var data = JSON.parse(raw);
    // 30 分钟内有效
    if (Date.now() - data.ts > 30 * 60 * 1000) return null;
    return data;
  } catch (e) { return null; }
}

// ---- 保存定位缓存 ----
function saveCachedLocation(lng, lat, city) {
  try {
    localStorage.setItem('map_location', JSON.stringify({
      lng: lng, lat: lat, city: city, ts: Date.now()
    }));
  } catch (e) { /* quota exceeded, ignore */ }
}

// ---- 浏览器定位 ----
function tryGeolocation() {
  if (!navigator.geolocation) return;

  navigator.geolocation.getCurrentPosition(
    function (pos) {
      var lng = pos.coords.longitude;
      var lat = pos.coords.latitude;
      updateCityName(lng, lat);
      saveCachedLocation(lng, lat, currentCity);

      currentLng = lng;
      currentLat = lat;
      addMyLocationMarker(lng, lat);
      map.setCenter([lng, lat]);
      map.setZoom(15);
      loadNearbyShops();
    },
    function () {
      // GPS 失败，保持当前城市不变
    },
    { timeout: 3000, enableHighAccuracy: false }
  );
}

// ---- 蓝色圆点标注 ----
function addMyLocationMarker(lng, lat) {
  if (myLocationMarker) { map.remove(myLocationMarker); }
  myLocationMarker = new AMap.Marker({
    position: [lng, lat],
    icon: new AMap.Icon({
      size: new AMap.Size(18, 18),
      image: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_b.png',
      imageSize: new AMap.Size(18, 18)
    }),
    zIndex: 200,
    anchor: 'center'
  });
  map.add(myLocationMarker);
}

// ---- 根据经纬度匹配最近城市 ----
function updateCityName(lng, lat) {
  var closest = null;
  var minDist = Infinity;
  for (var city in CITY_COORDS) {
    var c = CITY_COORDS[city];
    var d = Math.pow(c[0] - lng, 2) + Math.pow(c[1] - lat, 2);
    if (d < minDist) { minDist = d; closest = city; }
  }
  if (closest) {
    currentCity = closest;
    document.getElementById('mapCity').textContent = closest;
  }
}

// ---- 重新定位 ----
function relocate() {
  if (!navigator.geolocation) { showToast('浏览器不支持定位'); return; }
  showToast('正在定位...');
  navigator.geolocation.getCurrentPosition(
    function (pos) {
      currentLng = pos.coords.longitude;
      currentLat = pos.coords.latitude;
      updateCityName(currentLng, currentLat);
      saveCachedLocation(currentLng, currentLat, currentCity);
      addMyLocationMarker(currentLng, currentLat);
      map.setCenter([currentLng, currentLat]);
      map.setZoom(15);
      loadNearbyShops();
      showToast('定位成功');
    },
    function () { showToast('定位失败，请检查浏览器定位权限'); },
    { timeout: 8000, enableHighAccuracy: true }
  );
}

// ---- 加载附近商户 ----
function loadNearbyShops() {
  api.get('/shops/nearby', {
    params: { lng: currentLng, lat: currentLat, radius: 5000 }
  }).then(function (res) {
    if (res.data.code === 200 && res.data.data) {
      nearbyShops = res.data.data.list || [];
      var total = res.data.data.total || nearbyShops.length;
      document.getElementById('mapShopCount').textContent = '附近 ' + total + ' 家商户';
      renderMarkers(nearbyShops);
      renderShopList(nearbyShops);
    } else {
      nearbyShops = [];
      clearMarkers();
      document.getElementById('mapShopCount').textContent = '附近暂无商户';
      document.getElementById('mapShopList').innerHTML =
        '<p style="text-align:center;color:#BBB;padding:20px;">附近暂无商户</p>';
    }
  }).catch(function () {
    document.getElementById('mapShopCount').textContent = '加载失败';
  });
}

// ---- 渲染地图标注 ----
function renderMarkers(shops) {
  clearMarkers();

  shops.forEach(function (shop) {
    var catColor = CAT_COLORS[shop.categoryName] || '#FF6A00';

    var el = document.createElement('div');
    el.style.cssText =
      'width:32px;height:32px;border-radius:50%;' +
      'background:' + catColor + ';color:#FFF;' +
      'font-size:14px;font-weight:700;text-align:center;line-height:32px;' +
      'border:2px solid #FFF;box-shadow:0 2px 6px rgba(0,0,0,.3);cursor:pointer;';
    el.textContent = '¥';

    var marker = new AMap.Marker({
      position: [shop.x, shop.y],
      content: el,
      anchor: 'center',
      zIndex: 100,
      extData: shop
    });

    marker.on('click', function () {
      showInfoWindow(shop);
    });

    map.add(marker);
    allMarkers.push(marker);
  });

  if (allMarkers.length > 0) {
    map.setFitView(allMarkers, false, [60, 60, 200, 180]);
  }
}

// ---- 渲染底部商户列表 ----
function renderShopList(shops) {
  var container = document.getElementById('mapShopList');
  if (!shops || shops.length === 0) {
    container.innerHTML = '<p style="text-align:center;color:#BBB;padding:20px;">附近暂无商户</p>';
    return;
  }

  container.innerHTML = shops.map(function (shop) {
    var starCount = Math.round((shop.score || 0) / 10);
    var starsHtml = '';
    for (var i = 0; i < 5; i++) {
      starsHtml += i < starCount ? '<i class="fas fa-star"></i>' : '<i class="far fa-star"></i>';
    }

    var imgHtml = shop.coverImg
      ? '<img class="msl-thumb" src="' + shop.coverImg + '" onerror="this.style.display=\'none\'">'
      : '<div class="msl-thumb msl-thumb-placeholder"><i class="fas fa-store"></i></div>';

    return '<div class="map-shop-item" onclick="goShopDetail(' + shop.id + ')">' +
      imgHtml +
      '<div class="msl-info">' +
        '<div class="msl-name">' + escHtml(shop.name) + '</div>' +
        '<div class="msl-stars">' + starsHtml + ' <span>' + ((shop.score || 0) / 10).toFixed(1) + '</span></div>' +
        '<div class="msl-meta">' +
          '<span>📍 ' + (shop.distance || 0) + 'm</span>' +
          '<span>💰 ¥' + (shop.avgPrice || '-') + '/人</span>' +
        '</div>' +
      '</div>' +
      '<i class="fas fa-chevron-right msl-arrow"></i>' +
    '</div>';
  }).join('');
}

// ---- 信息窗体 ----
function showInfoWindow(shop) {
  if (infoWindow) infoWindow.close();

  var starCount = Math.round((shop.score || 0) / 10);
  var starsHtml = '';
  for (var i = 0; i < 5; i++) {
    starsHtml += i < starCount ? '<i class="fas fa-star"></i>' : '<i class="far fa-star"></i>';
  }

  var imgHtml = shop.coverImg
    ? '<img class="info-card-img" src="' + shop.coverImg + '" onerror="this.style.display=\'none\'">'
    : '';

  var html =
    '<div class="amap-info-content">' +
      imgHtml +
      '<div class="info-card-name">' + escHtml(shop.name) + '</div>' +
      '<div class="info-card-stars">' + starsHtml + ' ' + ((shop.score || 0) / 10).toFixed(1) + '</div>' +
      '<div class="info-card-meta">📍 ' + escHtml(shop.address || '') + '</div>' +
      '<div class="info-card-meta">💰 人均 ¥' + (shop.avgPrice || '-') + '</div>' +
      '<div class="info-card-distance">📏 ' + (shop.distance || 0) + 'm</div>' +
      '<span class="info-card-btn" onclick="goShopDetail(' + shop.id + ')">查看详情 ›</span>' +
    '</div>';

  infoWindow = new AMap.InfoWindow({
    content: html,
    offset: new AMap.Pixel(0, -40),
    autoMove: true
  });

  infoWindow.open(map, [shop.x, shop.y]);
}

function goShopDetail(id) {
  window.location.href = 'shop-detail.html?id=' + id;
}

function clearMarkers() {
  allMarkers.forEach(function (m) { map.remove(m); });
  allMarkers = [];
}

function escHtml(s) {
  return s ? s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
}
