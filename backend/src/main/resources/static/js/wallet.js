(function(){if(!localStorage.getItem('token')){window.location.href='login.html';return;}loadAll();})();
function loadAll(){loadBalance();loadTransactions();}
function loadBalance(){api.get('/wallet/balance').then(function(r){if(r.data.code===200){document.getElementById('wbBalance').textContent='¥'+(r.data.data.balance||0).toFixed(2);}});}
function loadTransactions(){api.get('/wallet/transactions').then(function(r){if(r.data.code===200)renderTx(r.data.data||[]);});}
function renderTx(list){var c=document.getElementById('txList');if(!list.length){c.innerHTML='<p style="text-align:center;padding:40px 0;color:#BBB;">暂无交易记录</p>';return;}
c.innerHTML=list.map(function(t){var cls=t.transactionType===1?'recharge':t.transactionType===3?'refund':'consume';var icon=cls==='recharge'?'<i class="fas fa-arrow-down"></i>':cls==='refund'?'<i class="fas fa-undo"></i>':'<i class="fas fa-arrow-up"></i>';
return '<div class="tx-item"><div class="tx-icon '+cls+'">'+icon+'</div><div class="tx-info"><div class="tx-note">'+esc(t.note)+'</div><div class="tx-time">'+(t.createdAt||'').substring(0,16).replace('T',' ')+'</div></div><div class="tx-amount '+(t.transactionType===1?'plus':'minus')+'">'+(t.transactionType===1?'+':'')+(t.amount*1).toFixed(2)+'</div></div>';}).join('');}
function doRecharge(){var a=prompt('请输入充值金额：');if(!a||isNaN(a)||a*1<=0)return;api.post('/wallet/recharge',{amount:parseFloat(a)}).then(function(r){if(r.data.code===200){showToast('充值成功');loadAll();}else{showToast(r.data.msg);}});}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'):'';}
