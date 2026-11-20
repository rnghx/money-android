(function(root){
'use strict';
const categories=['Продукты','Кафе','Транспорт','Покупки','Дом','Здоровье','Подписки','Переводы','Зарплата','Прочее'];
function amount(s){
 const raw=String(s??'').trim().replace(/[\s\u00a0\u202f]/g,'').replace(/(?:₽|руб\.?|RUB|р\.)/gi,'').replace(/[−–]/g,'-');
 if(!/^[+-]?\d+(?:[.,]\d{1,2})?$/.test(raw))return null;
 const [whole,frac='']=raw.replace(/^[+-]/,'').split(/[.,]/); const n=Number(whole)*100+Number(frac.padEnd(2,'0'));
 return Number.isSafeInteger(n)?(raw.startsWith('-')?-n:n):null;
}
function date(s){
 const v=String(s??'').trim(); let m=v.match(/^(\d{2})[./](\d{2})[./](\d{4})(?:\D|$)/), out;
 if(m)out=`${m[3]}-${m[2]}-${m[1]}`;
 else {m=v.match(/^(\d{4}-\d{2}-\d{2})(?:\D|$)/);if(m)out=m[1];}
 if(!out)return null; const d=new Date(out+'T12:00:00Z');return !isNaN(d)&&d.toISOString().slice(0,10)===out?out:null;
}
function category(desc,type){
 const s=desc.toLowerCase(); if(type==='transfer')return 'Переводы';
 if(/зарплат|salary/.test(s))return 'Зарплата';
 if(/пят[её]роч|магнит|перекр[её]ст|вкусвилл|ашан|лента|продукт/.test(s))return 'Продукты';
 if(/коф|кафе|ресторан|coffee|burger|вкусно/.test(s))return 'Кафе';
 if(/такси|taxi|метро|транспорт|азс|бензин/.test(s))return 'Транспорт';
 if(/аптек|врач|клиник|здоров/.test(s))return 'Здоровье';
 if(/ozon|wildberries|wb\b|маркет/.test(s))return 'Покупки';
 if(/подписк|плюс|premium|netflix/.test(s))return 'Подписки';
 if(/жкх|квартплат|аренд|коммунал/.test(s))return 'Дом'; return 'Прочее';
}
function bankCategory(raw,type){
 const named=category([raw.correspondent,raw.description].filter(Boolean).join(' '),type);if(named!=='Прочее'||type!=='expense')return named;
 const mcc=Number(raw.classificationCode);
 const groups={'Продукты':[5411,5422,5441,5451,5462,5499],'Кафе':[5811,5812,5813,5814],'Транспорт':[4111,4121,4131,4789,5541,5542,5533],'Здоровье':[5912,8011,8021,8031,8041,8062,8099],'Дом':[4900,6513],'Покупки':[5262,5310,5311,5331,5399,5651,5661,5691]};
 return Object.keys(groups).find(c=>groups[c].includes(mcc))||'Прочее';
}
function csv(text){
 text=text.replace(/^\uFEFF/,'');const first=text.split(/\r?\n/).find(x=>x.trim())||'';
 const delimiters=[';','\t',','];const delimiter=delimiters.sort((a,b)=>first.split(b).length-first.split(a).length)[0];
 const rows=[];let row=[],cell='',quoted=false;
 for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;}
 else if(c===delimiter&&!quoted){row.push(cell.trim());cell='';}
 else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell.trim());if(row.some(Boolean))rows.push(row);row=[];cell='';}
 else cell+=c;}
 if(quoted)throw Error('Незакрытые кавычки в CSV');row.push(cell.trim());if(row.some(Boolean))rows.push(row);
 return rows;
}
function mapping(header){
 const find=re=>header.findIndex(x=>re.test(x.toLowerCase()));
 return {date:find(/дата|date/),description:find(/описан|назначен|детали|description|merchant/),amount:find(/сумма операции|сумма$|amount/),expense:find(/расход|списан|дебет/),income:find(/приход|зачислен|кредит/),type:find(/тип|направлен|type/)};
}
function direction(s){if(/доход|приход|зачисл|income|credit|возврат/i.test(s))return 'income';if(/расход|списан|expense|debit|покупк|оплат/i.test(s))return 'expense';if(/свои|между|transfer/i.test(s))return 'transfer';return '';}
function csvRows(rows,map){return rows.map((r,i)=>{
 const desc=r[map.description]||'';let n=amount(r[map.amount]);let type=direction(r[map.type]||'');
 const debit=map.expense>=0?amount(r[map.expense]):null, credit=map.income>=0?amount(r[map.income]):null;
 if(debit && credit){n=null;type='';}
 else if(debit){n=Math.abs(debit);type='expense';}else if(credit){n=Math.abs(credit);type='income';}
 else if(n!==null&&n<0)type='expense';
 // A positive unsigned amount alone does not establish an incoming transaction.
 else if(n!==null&&/^\s*\+/.test(r[map.amount]||''))type=type||'income';
 const result={date:date(r[map.date]),amount:n===null?null:Math.abs(n),type,description:desc,source:'statement',selected:true,index:i};
 result.category=category(desc,type);return result;
});}
function pdfRows(text){
 const re=/(?:^|\n)\s*(\d{2}[./]\d{2}[./]\d{4})\b/g;const matches=[...text.matchAll(re)];
 return matches.map((m,i)=>{
 const chunk=text.slice(m.index, matches[i+1]?.index??text.length).trim();
 const rest=chunk.replace(/^\d{2}[./]\d{2}[./]\d{4}/,'').replace(/^\s*\d{2}:\d{2}(?::\d{2})?/,'').trim();
 const sums=[...rest.matchAll(/([+−–-]?\s*\d+(?:[ \u00a0\u202f]\d{3})*[,.]\d{2})(?:\s*(?:₽|руб\.?|RUB))?/gi)];
 // Multiple amounts may contain balances or foreign currency: require manual choice.
 const n=sums.length===1?amount(sums[0][1]):null;
 let type=direction(rest);if(n!==null&&n<0)type='expense';else if(sums.length===1&&/^\s*\+/.test(sums[0][1]))type='income';
 const description=rest.slice(0,220);return {date:date(m[1]),amount:n===null?null:Math.abs(n),type,description,category:category(description,type),source:'statement',selected:true,index:i};
 });
}
function valid(t){return !!(date(t.date)&&Number.isSafeInteger(t.amount)&&t.amount>0&&['income','expense','transfer'].includes(t.type)&&typeof t.description==='string'&&t.description.trim());}
function totals(rows){return rows.filter(t=>!t.review).reduce((a,t)=>{if(t.type==='income')a.income+=t.amount;if(t.type==='expense')a.expense+=t.amount;a.net=a.income-a.expense;return a;},{income:0,expense:0,net:0});}
function bankRows(records){
 if(!Array.isArray(records))throw Error('Неизвестный формат истории Сбера');
 const seen=new Set(),out=[];
 const money=(o)=>{if(!o||!['RUB','810','643'].includes(String(o.currencyCode)))throw Error('Не подтверждена сумма операции в рублях');const n=amount(String(o.amount));if(n===null)throw Error('Неверная сумма Сбера');return n;};
 for(const raw of records){
  if(raw.isFinancial===false||raw.state?.category==='cancel')continue;
  if(raw.isFinancial!==true||raw.state?.category!=='executed'||!['AUTHORIZATION','FINANCIAL','EXECUTED','SUCCESSED'].includes(raw.state?.name))throw Error('Неизвестный статус операции Сбера');
  if(typeof raw.uohId!=='string'||!raw.uohId||seen.has(raw.uohId))throw Error('Повтор операции Сбера');seen.add(raw.uohId);
  const parsedDate=String(raw.date||'').match(/^(\d{2})\.(\d{2})\.(\d{4})T(\d{2}:\d{2}:\d{2})$/);
  const d=parsedDate?date(`${parsedDate[3]}-${parsedDate[2]}-${parsedDate[1]}`):null;if(!d)throw Error('Неверная дата Сбера');
  const nativeRub=['RUB','810','643'].includes(String(raw.operationAmount?.currencyCode));
  const signed=money(nativeRub?raw.operationAmount:raw.nationalAmount);
  const fee=raw.commission?money(raw.commission):0;if(fee<0)throw Error('Неизвестная комиссия Сбера');
  const own=raw.ownTransfer===true||['UfsTransferSelf','UfsDepositOpen','UfsDepositClose'].includes(raw.form)||/перевод себе|между своими|на свой сч[её]т/i.test(raw.description||'');
  const text=String(raw.correspondent||raw.description||'Операция Сбера').replace(/\s+/g,' ').trim().slice(0,240);
  const common={date:d,timestamp:Date.parse(`${d}T${parsedDate[4]}+03:00`),description:text,source:'bank',accountKey:typeof raw.accountKey==='string'?raw.accountKey:null,review:false,hold:raw.state.name==='AUTHORIZATION'};
  if(signed) {const type=own?'transfer':signed<0?'expense':'income';out.push({...common,id:'bank:'+raw.uohId,amount:Math.abs(signed),type,category:bankCategory(raw,type)});}
  if(fee)out.push({...common,id:'bank:'+raw.uohId+':fee',accountKey:raw.feeAccountKey||common.accountKey,amount:fee,type:'expense',description:'Комиссия · '+text,category:'Прочее'});
 }
 return out;
}
function mergeBank(existing,incoming,month){
 if(!/^\d{4}-\d{2}$/.test(month)||incoming.some(t=>!valid(t)||t.source!=='bank'||!t.date.startsWith(month)))throw Error('Сбер вернул операции другого периода');
 const ids=new Set();for(const t of incoming){if(ids.has(t.id))throw Error('Повтор операции Сбера');ids.add(t.id);}
 const old=new Map(existing.filter(t=>t.source==='bank').map(t=>[t.id,t]));
 const snapshot=incoming.map(t=>{const saved=old.get(t.id);let item=saved?.userType?{...t,type:saved.userType,userType:saved.userType,category:category(t.description,saved.userType)}:t;return saved?.userCategory&&item.type==='expense'?{...item,category:saved.userCategory,userCategory:saved.userCategory}:item;});
 return existing.filter(t=>t.source!=='bank'||!t.date.startsWith(month)).concat(snapshot);
}
function decimalMinor(value){
 const match=String(value??'').match(/^(-?)(\d+)(?:\.(\d+))?$/);if(!match)return null;
 const fraction=match[3]||'';let n=Number(match[2])*100+Number(fraction.slice(0,2).padEnd(2,'0'))+(fraction.length>2&&Number(fraction[2])>=5?1:0);
 if(!Number.isSafeInteger(n))return null;return match[1]&&n?-n:n;
}
function convertMinor(value,from,to,rubPerUsd){
 if(!Number.isSafeInteger(value)||!['RUB','USD'].includes(from)||!['RUB','USD'].includes(to))return null;
 if(from===to)return value;
 const rate=Number(rubPerUsd);if(!Number.isFinite(rate)||rate<=0||rate>10000)return null;
 const converted=from==='USD'?value*rate:value/rate;const rounded=Math.sign(converted)*Math.round(Math.abs(converted));return Number.isSafeInteger(rounded)?rounded:null;
}
function currency(code){return ({RUR:'RUB',AUR:'XAU',ARG:'XAG',PTR:'XPT',PDR:'XPD',643:'RUB',810:'RUB'})[String(code)]||String(code);}
function assetMinor(value,from,to,fx){
 const fraction=s=>{const m=String(s??'').match(/^(-?)(\d{1,30})(?:\.(\d{1,24}))?$/);if(!m)return null;const tail=m[3]||'';return {n:BigInt((m[2]+tail).replace(/^0+(?=\d)/,''))*(m[1]?-1n:1n),d:10n**BigInt(tail.length)};};
 from=currency(from);to=currency(to);if(!['RUB','USD'].includes(to))return null;
 const v=fraction(value);if(!v)return null;let n=v.n*100n,d=v.d;
 if(from!==to){const rate=code=>code==='RUB'?'1':code==='USD'?fx?.rubPerUsd:fx?.rates?.[code];const a=fraction(rate(from)),b=fraction(rate(to));if(!a||!b||a.n<=0n||b.n<=0n||Number(rate(from))>10000||Number(rate(to))>10000)return null;n*=a.n*b.d;d*=a.d*b.n;}
 const negative=n<0n;const a=negative?-n:n;let rounded=a/d;if((a%d)*2n>=d)rounded+=1n;if(rounded>BigInt(Number.MAX_SAFE_INTEGER))return null;return Number(negative?-rounded:rounded);
}
function convertDecimalMinor(value,from,to,rubPerUsd){if(!['RUB','USD'].includes(from)||!['RUB','USD'].includes(to))return null;return assetMinor(value,from,to,{rubPerUsd});}
function bankCapital(snapshot){
 if(!snapshot||!Array.isArray(snapshot.items))throw Error('Нет остатков Сбера');
 const items=[],ids=new Map(),keys=new Map();
 const normalized=value=>{const [whole,fraction='']=String(value).split('.');const f=fraction.replace(/0+$/,'');const out=whole.replace(/^(-?)0+(?=\d)/,'$1')+(f?'.'+f:'');return out==='-0'?'0':out;};
 for(const raw of snapshot.items){
  if(!raw||typeof raw.id!=='string'||typeof raw.key!=='string'||decimalMinor(raw.amount)===null||!/^[A-Z0-9]{3}$/.test(currency(raw.currency)))throw Error('Неизвестный остаток Сбера');
  const item={...raw,currency:currency(raw.currency)},same=ids.get(item.id)||keys.get(item.key);
  if(same){if(same.currency!==item.currency||normalized(same.amount)!==normalized(item.amount))throw Error('Неоднозначный остаток Сбера');ids.set(item.id,same);keys.set(item.key,same);continue;}
  ids.set(item.id,item);keys.set(item.key,item);items.push(item);
 }
 return {items,unsupported:Array.isArray(snapshot.unsupported)?snapshot.unsupported:[],timestamp:snapshot.timestamp};
}
function portfolio(bank,assets,to,preferences={}){
 const sources=[];
 if(bank.connected||bank.configured||bank.capital){
  let result={id:'sber',title:'Сбер',amount:null,complete:false,approx:false,timestamp:bank.capital?.timestamp||0,stale:!!bank.error,unpriced:0};
  try{const snap=bankCapital(bank.capital);let sum=0,valued=0;result.complete=!snap.unsupported.length;for(const item of snap.items.filter(item=>preferences[item.key]?.included!==false)){const n=assetMinor(item.amount,item.currency,to,assets.fx);if(n===null){result.complete=false;result.unpriced++;continue;}sum+=n;valued++;result.approx=result.approx||item.currency!==to;}result.amount=Number.isSafeInteger(sum)&&(valued||(!snap.items.some(item=>preferences[item.key]?.included!==false)&&!snap.unsupported.length))?sum:null;}catch(e){result.error=e.message;}
  sources.push(result);
 }
 if(assets.okxConnected||assets.okx){const amount=assets.okx?assetMinor(assets.okx.usd,'USD',to,assets.fx):null;sources.push({id:'okx',title:'OKX',amount,complete:amount!==null,approx:to!=='USD',timestamp:assets.okx?.timestamp||0,stale:!!assets.okxError});}
 const known=sources.filter(s=>s.amount!==null),sum=known.reduce((v,s)=>v+s.amount,0),times=sources.map(s=>s.timestamp).filter(Boolean);
 return {amount:known.length&&Number.isSafeInteger(sum)?sum:null,complete:sources.length>0&&sources.every(s=>s.complete),approx:known.some(s=>s.approx),count:sources.length,known:known.length,stale:sources.some(s=>s.stale)||(known.some(s=>s.approx)&&!!assets.fxError),timestamp:times.length?Math.min(...times):0,sources};
}
function breakdown(rows,type,limit=4){
 const groups=new Map();let total=0;
 for(const row of rows){if(row.review||row.type!==type)continue;const label=type==='income'?(row.description.trim()||'Прочие поступления'):(categories.includes(row.category)?row.category:'Прочее');const key=label.toLowerCase();const item=groups.get(key)||{label,amount:0};item.amount+=row.amount;groups.set(key,item);total+=row.amount;}
 const sorted=[...groups.values()].sort((a,b)=>b.amount-a.amount);let result=sorted.slice(0,limit);if(sorted.length>limit)result.push({label:type==='income'?'Другие поступления':'Остальное',amount:sorted.slice(limit).reduce((v,s)=>v+s.amount,0)});
 return result.map(item=>({...item,share:total?item.amount/total:0}));
}
const api={categories,amount,date,category,bankCategory,csv,mapping,csvRows,pdfRows,valid,totals,bankRows,mergeBank,decimalMinor,convertMinor,convertDecimalMinor,currency,assetMinor,bankCapital,portfolio,breakdown};root.Money=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
