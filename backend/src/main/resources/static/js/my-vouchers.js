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
    return '<div class="vcard"><div class="vcard-left"><div class="vcard-value"><span>¥</span>'+(v.faceValue/100).toFixed(0)+'</div></div><div class="vcard-main"><div class="vcard-name">'+esc(v.voucherTitle)+'</div><div class="vcard-shop" onclick="window.location.href=\'shop-detail.html?id='+v.shopId+'\'" style="cursor:pointer;">🏪 '+esc(v.shopName)+'</div><div class="vcard-expire">'+expStr+'</div>'+statusHtml+useBtn+'</div></div>';
  }).join('');
}
function useVoucher(id, shopId){window.location.href='shop-detail.html?id='+shopId+'&useVoucherId='+id;}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'):'';}
