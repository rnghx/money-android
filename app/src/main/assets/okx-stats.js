(function(root){
'use strict';
const S=10n**18n,START=Date.parse('2026-11-01T00:00:00+03:00');
function decimal(v){const m=String(v??'').match(/^(-?)(\d{1,30})(?:\.(\d{1,18}))?$/);if(!m)throw Error('Неизвестная сумма OKX');return (BigInt(m[2])*S+BigInt((m[3]||'').padEnd(18,'0')))*(m[1]?-1n:1n);}
function text(n){const sign=n<0n?'−':'';n=n<0n?-n:n;const fraction=(n%S).toString().padStart(18,'0').replace(/0+$/,'');return sign+(n/S).toString().replace(/\B(?=(\d{3})+(?!\d))/g,' ')+(fraction?','+fraction:'');}
function month(ts){const n=Number(ts);if(!Number.isSafeInteger(n)||n<0)throw Error('Неизвестная дата OKX');return new Date(n+10800000).toISOString().slice(0,7);}
function unique(rows){const map=new Map();for(const r of rows||[]){if(!r||!/^\d+$/.test(String(r.billId)))throw Error('Неизвестное исполнение OKX');if(map.has(r.billId)){if(JSON.stringify(map.get(r.billId))!==JSON.stringify(r))throw Error('Противоречивые записи OKX');continue;}map.set(r.billId,r);}return [...map.values()];}
function summary(history,selected){
 const fills=unique(history.fills),bills=unique(history.bills),groups=new Map(),counts={SWAP:0,FUTURES:0},billIds=new Set(bills.map(b=>String(b.billId)));let invalid=0;
 const inMonth=r=>Number(r.ts)>=START&&month(r.ts)===selected;
 for(const f of fills)try{if(inMonth(f)&&Object.prototype.hasOwnProperty.call(counts,f.instType)){counts[f.instType]++;if(!billIds.has(String(f.billId)))invalid++;}}catch(e){invalid++;}
 for(const b of bills){try{
   if(!['SWAP','FUTURES'].includes(b.instType)||!['2','3','5','8','9','10'].includes(String(b.type))||!inMonth(b))continue;
   let value;if(['8','10'].includes(String(b.type))){value=b.balChg===undefined||b.balChg===''?decimal(b.posBalChg):decimal(b.balChg);if(!value&&b.posBalChg!==undefined&&b.posBalChg!=='')value=decimal(b.posBalChg);}else value=decimal(b.pnl)+decimal(b.fee);
   if(!/^[A-Z0-9]{2,16}$/.test(b.ccy))throw Error('Неизвестная валюта OKX');const key=b.instType+':'+b.ccy;if(!groups.has(key))groups.set(key,{kind:b.instType,ccy:b.ccy,profit:0n,loss:0n,net:0n});const g=groups.get(key);g.net+=value;if(value>0n)g.profit+=value;else g.loss-=value;
  }catch(e){invalid++;}
 }
 const start=Date.parse(selected+'-01T00:00:00+03:00'),next=Date.UTC(Number(selected.slice(0,4)),Number(selected.slice(5)),1)-10800000;
 const until=Math.min(next,history.until||0);let edge=start;for(const r of (history.ranges||[]).slice().sort((a,b)=>a.from-b.from)){if(r.from>edge)break;if(r.until>edge)edge=r.until;}
 return {groups:[...groups.values()],counts,invalid,complete:start>=START&&until>start&&edge>=until,from:START,until:history.until};
}
const api={decimal,text,month,summary,START};root.OkxStats=api;if(typeof module!=='undefined')module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:this);
