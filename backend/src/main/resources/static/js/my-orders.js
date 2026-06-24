var oType = null;
(function(){if(!localStorage.getItem('token')){window.location.href='login.html';return;}loadOrders();})();

function switchOTab(t){oType = t;
  document.querySelectorAll('.tab-item-v').forEach(function(e,i){
    e.classList.toggle('active', (t===null&&i===0)||(t===1&&i===1)||(t===2&&i===2));
  });
  loadOrders();}
function loadOrders(){
  var p = '?page=1&size=20';
  if (oType !== null) p += '&orderType=' + oType;
  api.get('/orders/my' + p).then(function(r){
    if(r.data.code===200) { render(r.data.data||[]); }
    else { render([]); }
  }).catch(function(){ render([]); });
}
function render(list){
  var c=document.getElementById('oList');
  if(!list.length){c.innerHTML='<p style="text-align:center;padding:80px 0;color:#BBB;"><i class="far fa-receipt" style="font-size:40px;display:block;margin-bottom:10px;"></i>暂无订单</p>';return;}
  c.innerHTML=list.map(function(o){
    var typeCls=o.orderType===1?'voucher':'product';
    return '<div class="ocard"><div class="ocard-top"><span class="ocard-type '+typeCls+'">'+(o.orderType===1?'优惠券':'商品')+'</span><span class="ocard-amount">¥'+(o.amount*1).toFixed(2)+'</span></div><div class="ocard-name">'+esc(o.targetName||'')+'</div><div class="ocard-shop">🏪 '+esc(o.shopName||'')+'</div><div class="ocard-time">'+(o.createdAt||'').substring(0,16).replace('T',' ')+' · '+(o.status===1?'已支付':'待支付')+'</div></div>';
  }).join('');
}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'):'';}
