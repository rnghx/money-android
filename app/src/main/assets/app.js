'use strict';
const $=s=>document.querySelector(s), M=Money;
const paths={plus:'M12 5v14M5 12h14',left:'M15 6l-6 6 6 6',right:'M9 6l6 6-6 6',close:'M6 6l12 12M6 18L18 6',up:'M7 17L17 7M7 7h10v10',down:'M7 7l10 10M7 17h10V7',bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',upload:'M12 16V3M7 8l5-5 5 5M4 15v6h16v-6',check:'M5 12l4 4L19 6',transfer:'M4 7h15M15 3l4 4-4 4M20 17H5M9 13l-4 4 4 4'};
function icon(name){if(name==='settings')return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 3h5l.5 2.4 1.4.8 2.3-.8 2.5 4.3-1.8 1.6v1.5l1.8 1.6-2.5 4.3-2.3-.8-1.4.8-.5 2.3h-5L9 18.7l-1.4-.8-2.3.8-2.5-4.3 1.8-1.6v-1.5L2.8 9.7l2.5-4.3 2.3.8L9 5.4z"/><circle cx="12" cy="12" r="3"/></svg>';return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="'+(paths[name]||paths.transfer)+'"/></svg>';}
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const rub=(v,digits=Math.abs(v)%100?2:0)=>new Intl.NumberFormat('ru-RU',{minimumFractionDigits:digits,maximumFractionDigits:digits}).format(v/100);
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
let state={transactions:[],seen:[],schema:1};let month=today().slice(0,7),filter='expense',editId=null;
const native=typeof Native!=='undefined'; const demo=!native&&new URLSearchParams(location.search).has('demo');
let bank={connected:false,busy:false,error:'',lastSync:0,month:''};
let conversionError='';
let assets={okxConnected:false,okxBusy:false,fxBusy:false,okxError:'',fxError:''};
try{if(native)state={...state,...JSON.parse(Native.load())};}catch(e){}
let viewCurrency=state.currency==='USD'?'USD':'RUB';
if(demo){assets={...assets,okxConnected:true,okx:{usd:'2530.765',timestamp:Date.now(),details:{funding:'530.765',trading:'2000'}},fx:{rubPerUsd:'90.1234',rates:{USD:'90.1234',EUR:'99.5'},date:today(),fetchedAt:Date.now()}};bank={...bank,connected:true,configured:true,lastSync:Date.now(),capital:{items:[{id:'demo1',key:'demo1',amount:'172480.50',currency:'RUB',title:'Карта'},{id:'demo2',key:'demo2',amount:'25000',currency:'RUB',title:'Счёт'}],unsupported:[],timestamp:Date.now()}};}
if(demo){const samples=[['01','Зарплата',14800000,'income','Зарплата'],['02','ВкусВилл',185640,'expense','Продукты'],['03','Кофейня',42000,'expense','Кафе'],['04','Яндекс Go',68000,'expense','Транспорт'],['04','Ozon',729000,'expense','Покупки'],['05','Перекрёсток',316800,'expense','Продукты'],['06','Подписки',79900,'expense','Подписки'],['07','Аптека',126000,'expense','Здоровье'],['08','ВкусВилл',227500,'expense','Продукты']];state.transactions=samples.map((s,i)=>({id:'demo'+i,date:month+'-'+s[0],description:s[1],amount:s[2],type:s[3],category:s[4],source:'manual',review:false}));}
function save(){if(native&&!Native.save(JSON.stringify(state))){showToast('Не удалось сохранить');return false;}return true;}
function refreshNative(){
 if(!native)return;try{bank=JSON.parse(Native.directStatus());bank.error=bank.error||conversionError;assets=JSON.parse(Native.assetsStatus());render();refreshSheet();
 }catch(e){showToast('Не удалось прочитать состояние Сбера');}
}
function refreshSheet(){if(!$('#sheet').open)return;if($('#connection'))connection();else if($('#sources'))sources();else if($('#okxSettings'))okxSettings();else if($('#bankSettings'))bankSettings();}
function assetsUpdate(result){assets=result;render();refreshSheet();}
function shownMoney(value,from='RUB'){
 const converted=M.convertMinor(value,from,viewCurrency,assets.fx?.rubPerUsd);
 if(converted===null)return '—';
 return (from!==viewCurrency?'≈ ':'')+rub(converted)+(viewCurrency==='USD'?' $':' ₽');
}
function shownAsset(value,from='USD'){const converted=M.assetMinor(value,from,viewCurrency,assets.fx);return converted===null?'—':(M.currency(from)!==viewCurrency?'≈ ':'')+rub(converted)+(viewCurrency==='USD'?' $':' ₽');}
function currencySwitch(){return '<div class="currency-switch" role="group" aria-label="Валюта">'+[['RUB','₽'],['USD','$']].map(([id,label])=>'<button data-currency="'+id+'" aria-label="'+(id==='RUB'?'Рубли':'Доллары')+'" aria-pressed="'+(viewCurrency===id)+'" class="'+(viewCurrency===id?'active':'')+'">'+label+'</button>').join('')+'</div>';}


function header(title,extra=''){return `<header><h1>${title}</h1><div class="toolbar">${extra}</div></header>`;}
function btn(name,action,label,plain=false){return `<button class="icon ${plain?'plain':''}" data-action="${action}" aria-label="${label}">${icon(name)}</button>`;}
function period(){return `<div class="period">${btn('left','prev','Предыдущий месяц',true)}<span>${new Date(month+'-15').toLocaleDateString('ru-RU',{month:'long',...(month.slice(0,4)!==today().slice(0,4)?{year:'numeric'}:{})}).replace(' г.','')}</span>${btn('right','next','Следующий месяц',true)}</div>`;}
function connection(){
 const rate=assets.fx?('1 $ = '+assets.fx.rubPerUsd+' ₽ · '+new Date(assets.fx.date+'T12:00:00').toLocaleDateString('ru-RU',{day:'numeric',month:'short'})):'Не загружен';
 openSheet(sheethead('Настройки')+'<div id="connection"><button class="primary" data-action="refreshAll" '+(bank.busy||assets.okxBusy||assets.fxBusy?'disabled':'')+'>'+(bank.busy||assets.okxBusy||assets.fxBusy?'Обновление…':'Обновить данные')+'</button><button class="setting" data-action="sources"><span class="left">Источники</span><span class="right">Сбер · OKX'+icon('right')+'</span></button><button class="setting" data-action="fx"><span class="left"><span>Курс ЦБ<small>'+esc(rate)+'</small></span></span><span class="right">'+(assets.fxBusy?'Обновление…':'Обновить')+'</span></button>'+(assets.fxError?'<div class="error">'+esc(assets.fxError)+'</div>':'')+'</div>');
}
function sources(){const p=M.portfolio(bank,assets,viewCurrency,state.accountPreferences);openSheet(sheethead('Источники')+'<div id="sources">'+[['sber','Сбер','bankSettings',bank.connected],['okx','OKX','okxSettings',assets.okxConnected]].map(([id,label,action,connected])=>{const s=p.sources.find(s=>s.id===id);const value=s?.amount!==null&&s?.amount!==undefined?(s.approx?'≈ ':'')+rub(s.amount)+(viewCurrency==='USD'?' $':' ₽'):connected?'Нужны остатки':'Подключить';return '<button class="setting" data-action="'+action+'"><span class="left">'+label+'</span><span class="right">'+esc(value)+icon('right')+'</span></button>';}).join('')+'</div>');}
function bankSettings(){let items='',warning='';try{const snap=M.bankCapital(bank.capital);items=snap.items.map(item=>{const pref=state.accountPreferences?.[item.key]||{};return '<section class="account-options"><div class="setting"><span class="account-title">'+esc(item.title||'Счёт')+(item.suffix?' · '+esc(item.suffix):'')+'</span><span>'+esc(state.hideCapital?'••••':shownAsset(item.amount,item.currency))+'</span></div>'+[['visible','Показывать на главной'],['included','Учитывать в капитале и аналитике']].map(([field,label])=>'<label class="account-toggle"><span>'+label+'</span><input type="checkbox" data-account-key="'+esc(item.key)+'" data-account-field="'+field+'" '+(pref[field]!==false?'checked':'')+'></label>').join('')+'</section>';}).join('')+'<p class="small">Выбор сохраняется на телефоне. Скрытие счёта не исключает его из расчётов. Для привязки прежних операций к счетам нажмите «Обновить данные». Операции без определённого счёта остаются в аналитике.</p>';if(snap.unsupported.length)warning='<div class="small">Не все типы счетов поддерживаются</div>';}catch(e){}openSheet(sheethead('Сбер')+'<div id="bankSettings">'+(bank.connected?'<button class="primary" data-action="sync" '+(bank.busy?'disabled':'')+'>'+(bank.busy?'Обновление…':'Обновить данные')+'</button>'+items+warning+'<button class="textbutton" data-action="disconnect">Отключить Сбер</button>':'<button class="primary" data-action="connect">Подключить Сбер</button>')+(bank.error?'<div class="error">'+esc(bank.error)+'</div>':'')+'</div>');}
function okxSettings(){
 const names={funding:'Основной',trading:'Торговый',earn:'Earn',classic:'Classic'};
 const details=Object.entries(assets.okx?.details||{}).map(([key,v])=>{const n=M.decimalMinor(v);return n===null?'':'<div class="setting"><span>'+esc(names[key]||key)+'</span><span>'+esc(shownAsset(v))+'</span></div>';}).join('');
 openSheet(sheethead('OKX')+'<div id="okxSettings">'+(assets.okxConnected?'<button class="primary" data-action="syncOkx" '+(assets.okxBusy?'disabled':'')+'>'+(assets.okxBusy?'Обновление…':'Обновить баланс')+'</button>':'<button class="primary" data-action="connectOkx">Подключить · Read only</button>')+details+(assets.okxConnected?'<button class="textbutton" data-action="disconnectOkx">Отключить OKX</button>':'')+(assets.okxError?'<div class="error">'+esc(assets.okxError)+'</div>':'')+'</div>');
}

function showToast(text){$('#toast').textContent=text;$('#toast').style.display='block';setTimeout(()=>$('#toast').style.display='none',3000);}
function openSheet(html){$('#sheetContent').innerHTML=html;if(!$('#sheet').open)$('#sheet').showModal();}
function closeSheet(){ $('#sheet').close(); }
function sheethead(title){return `<div class="sheethead"><h2>${title}</h2>${btn('close','close','Закрыть',true)}</div>`;}
function goBack(){if($('#sheet').open)closeSheet();else if(native)Native.exit();}
const actions={close:closeSheet,prev:()=>shiftMonth(-1),next:()=>shiftMonth(1),settings:connection,sources,bankSettings,okxSettings,connect:()=>{if(native){closeSheet();Native.connectBank(month);}else showToast('Доступно в Android-приложении');},sync:()=>{if(native&&bank.connected&&!bank.busy)Native.syncBank(month);},disconnect:()=>{if(native)Native.disconnectBank();},connectOkx:()=>{if(native){closeSheet();Native.connectOkx();}else showToast('Доступно в Android-приложении');},syncOkx:()=>{if(native&&!assets.okxBusy){Native.syncOkx();Native.syncFx();}},disconnectOkx:()=>{if(native)Native.disconnectOkx();},fx:()=>{if(native&&!assets.fxBusy)Native.syncFx();},refreshAll:()=>{if(!native)return;if(bank.connected&&!bank.busy)Native.syncBank(month);if(assets.okxConnected&&!assets.okxBusy)Native.syncOkx();if(!assets.fxBusy)Native.syncFx();}};
function shiftMonth(n){const d=new Date(month+'-15');d.setMonth(d.getMonth()+n);month=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');render();}
document.addEventListener('click',e=>{const el=e.target.closest('button');if(!el)return;if(el.dataset.currency){viewCurrency=el.dataset.currency==='USD'?'USD':'RUB';state.currency=viewCurrency;save();render();refreshSheet();if(native&&viewCurrency==='USD'&&!assets.fx&&!assets.fxBusy)Native.syncFx();}else if(el.dataset.action)actions[el.dataset.action]?.();else if(el.dataset.edit)edit(el.dataset.edit);else if(el.dataset.filter){filter=el.dataset.filter;render();}else if(el.dataset.editType){$('#editType').value=el.dataset.editType;el.parentElement.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b===el));}});
document.addEventListener('submit',e=>{if(e.target.id==='editForm'){e.preventDefault();saveEdit();}});
$('#sheet').addEventListener('click',e=>{if(e.target===$('#sheet')){const r=$('#sheet').getBoundingClientRect();if(e.clientY<r.top||e.clientX<r.left||e.clientX>r.right)closeSheet();}});

