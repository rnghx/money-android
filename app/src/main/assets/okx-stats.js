(function(root){
'use strict';
const S=10n**18n,START=Date.parse('2026-11-26T00:00:00+03:00');
function decimal(v){const m=String(v??'').match(/^(-?)(\d{1,30})(?:\.(\d{1,18}))?$/);if(!m)throw Error('Неизвестная сумма OKX');return (BigInt(m[2])*S+BigInt((m[3]||'').padEnd(18,'0')))*(m[1]?-1n:1n);}
function text(n){const sign=n<0n?'−':'';n=n<0n?-n:n;const fraction=(n%S).toString().padStart(18,'0').replace(/0+$/,'');return sign+(n/S).toString().replace(/\B(?=(\d{3})+(?!\d))/g,' ')+(fraction?','+fraction:'');}
function month(ts){const n=Number(ts);if(!Number.isSafeInteger(n)||n<0)throw Error('Неизвестная дата OKX');return new Date(n+10800000).toISOString().slice(0,7);}
function identifier(value){const s=String(value||'');return /^\d{1,40}$/.test(s)?s:'';}
function numberField(value){const s=String(value||'');return /^\d{1,30}(?:\.\d{1,18})?$/.test(s)?s:'';}
function unique(rows){const map=new Map();for(const r of rows||[]){if(!r||!/^\d+$/.test(String(r.billId)))throw Error('Неизвестное исполнение OKX');if(map.has(r.billId)){if(JSON.stringify(map.get(r.billId))!==JSON.stringify(r))throw Error('Противоречивые записи OKX');continue;}map.set(r.billId,r);}return [...map.values()];}
function summary(history,selected){
 const fills=unique(history.fills),bills=unique(history.bills),groups=new Map(),counts={SWAP:0,FUTURES:0},records=[],fillMap=new Map(fills.map(f=>[String(f.billId),f])),billIds=new Set(bills.map(b=>String(b.billId)));let invalid=0;
 const inMonth=r=>Number(r.ts)>=START&&month(r.ts)===selected;
 for(const f of fills)try{if(inMonth(f)&&Object.prototype.hasOwnProperty.call(counts,f.instType)){counts[f.instType]++;if(!billIds.has(String(f.billId)))invalid++;}}catch(e){invalid++;}
 for(const b of bills){try{
   if(!['SWAP','FUTURES'].includes(b.instType)||!['2','3','5','8','9','10'].includes(String(b.type))||!inMonth(b))continue;
   let value;if(['8','10'].includes(String(b.type))){value=b.balChg===undefined||b.balChg===''?decimal(b.posBalChg):decimal(b.balChg);if(!value&&b.posBalChg!==undefined&&b.posBalChg!=='')value=decimal(b.posBalChg);}else value=decimal(b.pnl)+decimal(b.fee);
   if(!/^[A-Z0-9]{2,16}$/.test(b.ccy))throw Error('Неизвестная валюта OKX');const key=b.instType+':'+b.ccy;if(!groups.has(key))groups.set(key,{kind:b.instType,ccy:b.ccy,profit:0n,loss:0n,net:0n});const g=groups.get(key);g.net+=value;if(value>0n)g.profit+=value;else g.loss-=value;const fill=fillMap.get(String(b.billId))||{};const settlement=['8','10'].includes(String(b.type));records.push({id:String(b.billId),tradeId:identifier(b.tradeId||fill.tradeId),ts:Number(b.ts),kind:b.instType,ccy:b.ccy,instId:/^[A-Z0-9._-]{1,80}$/.test(String(b.instId||fill.instId||''))?String(b.instId||fill.instId):'',type:String(b.type),value,pnl:settlement?0n:decimal(b.pnl),fee:settlement?0n:decimal(b.fee),adjustment:settlement?value:0n,side:fill.side||'',size:numberField(fill.fillSz),price:numberField(fill.fillPx)});
  }catch(e){invalid++;}
 }
 const start=Math.max(START,Date.parse(selected+'-01T00:00:00+03:00')),next=Date.UTC(Number(selected.slice(0,4)),Number(selected.slice(5)),1)-10800000;
 const until=Math.min(next,history.until||0);let edge=start;for(const r of (history.ranges||[]).slice().sort((a,b)=>a.from-b.from)){if(r.from>edge)break;if(r.until>edge)edge=r.until;}
 return {month:selected,records:records.sort((a,b)=>b.ts-a.ts||(BigInt(a.id)>BigInt(b.id)?-1:1)),groups:[...groups.values()],counts,invalid,complete:start>=START&&until>start&&edge>=until,from:START,until:history.until};
}
function select(result,ccy,filter='net',kind='all'){return result.records.filter(r=>(!ccy||r.ccy===ccy)&&(kind==='all'||r.kind===kind)&&(filter==='net'||filter==='positive'&&r.value>0n||filter==='negative'&&r.value<0n));}
function cashflow(result,type){
 const filter=type==='income'?'positive':type==='expense'?'negative':null;if(!filter)throw Error('Неизвестный тип результата');
 const totals=new Map();for(const r of select(result,null,filter)){const value=r.value<0n?-r.value:r.value;totals.set(r.ccy,(totals.get(r.ccy)||0n)+value);}
 return [...totals].map(([ccy,amount])=>({ccy,amount}));
}
function accounting(result,rubPerUsd){
 let rate=null;try{rate=decimal(rubPerUsd);if(rate<=0n||rate>10000n*S)rate=null;}catch(e){}
 const out={income:0,expense:0,remaining:{income:[],expense:[]},approx:false,stablecoin:false};
 for(const type of ['income','expense'])for(const row of cashflow(result,type)){
  const supported=['RUB','USD','USDT'].includes(row.ccy),factor=row.ccy==='RUB'?S:rate;
  if(!supported||factor===null){out.remaining[type].push(row);continue;}
  // Round only after summing each currency; do not round away small fills.
  const n=row.amount*factor*100n,d=S*S,minor=(n+d/2n)/d;
  if(minor>BigInt(Number.MAX_SAFE_INTEGER)||!Number.isSafeInteger(out[type]+Number(minor))){out.remaining[type].push(row);continue;}
  out[type]+=Number(minor);out.approx=out.approx||row.ccy!=='RUB';out.stablecoin=out.stablecoin||row.ccy==='USDT';
 }
 return out;
}
function label(r){return ({'2':r.pnl===0n&&r.fee!==0n?'Комиссия':'Сделка','3':'Расчёт контракта','5':'Ликвидация','8':'Финансирование','9':'ADL','10':'Корректировка'})[r.type]||'Запись';}
function date(ts){return new Date(ts).toLocaleString('ru-RU',{timeZone:'Europe/Moscow',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit'});}
function report(result,ccy,filter='net',kind='all'){
 const rows=select(result,ccy,filter,kind),totals=new Map();for(const r of rows)totals.set(r.ccy,(totals.get(r.ccy)||0n)+r.value);
 const lines=['OKX · Фьючерсы · '+result.month+' · МСК',({net:'Итог',positive:'Положительные записи',negative:'Отрицательные записи'})[filter]||'Итог','Данные по: '+date(result.until)+' МСК','Записей: '+rows.length,'Контракты: '+(kind==='SWAP'?'бессрочные':kind==='FUTURES'?'срочные':'все')];
 for(const [unit,total] of totals)lines.push('Сумма: '+text(total)+' '+unit);
 for(const r of rows){lines.push('',date(r.ts)+' · '+(r.instId||'Контракт не указан')+' · '+label(r)+' · '+text(r.value)+' '+r.ccy);if(r.type!=='8'&&r.type!=='10')lines.push('P&L: '+text(r.pnl)+'; комиссия: '+text(r.fee));if(r.side||r.size||r.price)lines.push((r.side==='buy'?'Покупка':r.side==='sell'?'Продажа':'Исполнение')+'; количество: '+r.size+'; цена: '+r.price);lines.push('billId: '+r.id+(r.tradeId?'; tradeId: '+r.tradeId:''));}
 if(!result.complete||result.invalid)lines.push('Внимание: данные неполные.');return lines.join('\n');
}
const api={decimal,text,month,summary,START,select,cashflow,accounting,label,date,report};root.OkxStats=api;if(typeof module!=='undefined')module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:this);
