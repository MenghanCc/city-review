var vStatus = null;
(function(){if(!localStorage.getItem('token')){window.location.href='login.html';return;}loadVouchers();})();

function switchVTab(s){vStatus = s;
  document.querySelectorAll('.tab-item-v').forEach(function(e,i){
    e.classList.toggle('active', (s===null&&i===0)||(s===0&&i===1)||(s===1&&i===2)||(s===2&&i===3));
  });
  loadVouchers();}
function loadVouchers(){
  var p = vStatus !== null ? '?status=' + vStatus : '';
  api.get('/user/vouchers' + p).then(function(r){
    if(r.data.code===200) render(r.data.data||[]);
    else document.getElementById('vList').innerHTML='<p style="text-align:center;padding:60px 0;color:#999;">加载失败</p>';
  }).catch(function(){document.getElementById('vList').innerHTML='<p style="text-align:center;padding:60px 0;color:#999;">加载失败</p>';});
}
function render(list){
  var c=document.getElementById('vList');
  if(!list.length){c.innerHTML='<p style="text-align:center;padding:80px 0;color:#BBB;"><i class="far fa-ticket-alt" style="font-size:40px;display:block;margin-bottom:10px;"></i>暂无卡券</p>';return;}
  c.innerHTML=list.map(function(v){
    var statusHtml='';var useBtn='';
    if(v.status===0){var exp=v.expireTime?new Date(v.expireTime)>new Date():true;if(exp){useBtn='<span class="vcard-use-btn" onclick="useVoucher('+v.id+','+v.shopId+')">去使用</span>';}else{statusHtml='<span class="vcard-status expired">已过期</span>';}}
    else if(v.status===1){statusHtml='<span class="vcard-status used">已使用</span>';}
    else{statusHtml='<span class="vcard-status expired">已过期</span>';}
    var expStr=v.expireTime?'有效期至 '+(v.expireTime+'').substring(0,10):'';
    var typeLabel = v.type === 1 ? '<span style="font-size:10px;background:#FFF0F0;color:#FF3B30;padding:1px 6px;border-radius:8px;margin-left:4px;">兑换券</span>' : '<span style="font-size:10px;background:#E8F5E9;color:#4CAF50;padding:1px 6px;border-radius:8px;margin-left:4px;">代金券</span>';
    var displayValue = v.type === 1 ? (v.payValue || 0) / 100 : (v.faceValue || 0) / 100;
    var valueLabel = v.type === 1 ? '实付' : '面额';
    return '<div class="vcard"><div class="vcard-left"><div class="vcard-value"><span>¥</span>'+displayValue.toFixed(v.type===1?1:0)+'</div><div class="vcard-discount-label">'+valueLabel+'</div></div><div class="vcard-main"><div class="vcard-name">'+esc(v.voucherTitle)+typeLabel+'</div><div class="vcard-shop" onclick="window.location.href=\'shop-detail.html?id='+v.shopId+'\'" style="cursor:pointer;">🏪 '+esc(v.shopName)+'</div><div class="vcard-expire">'+expStr+'</div>'+statusHtml+useBtn+'</div></div>';
  }).join('');
}
function useVoucher(id, shopId){window.location.href='shop-detail.html?id='+shopId+'&useVoucherId='+id;}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'):'';}
