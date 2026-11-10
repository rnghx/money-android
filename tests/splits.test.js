const assert=require('node:assert/strict'),M=require('../app/src/main/assets/core.js'),L=require('../app/src/main/assets/ledger.js');
const raw={id:'bank:purchase',date:'2026-11-09',description:'Самокат',amount:55000,type:'expense',category:'Прочее',source:'bank',review:false};
let state=L.init({transactions:[raw]});
state.merchantRules[L.merchant('Самокат')]='food';
assert.equal(L.rows(state)[0].categoryId,'food');
assert.equal(L.merchant('SAMOKAT 123 MOSCOW'),L.merchant('Самокат'));
state.splits[raw.id]={total:55000,parts:[{description:'Кофе',amount:35000,categoryId:'cafe'},{description:'Печенье',amount:20000,categoryId:'food'}]};
assert.ok(L.validSplit(state.splits[raw.id]));
const ids=L.rows(state).map(t=>t.id);
for(let i=0;i<5;i++){state=JSON.parse(JSON.stringify(state));L.init(state);state.transactions=M.mergeBank(state.transactions,[{...raw,description:'SAMOKAT 123 MOSCOW'}],'2026-11');assert.equal(L.rows(state).length,2);assert.deepEqual(L.rows(state).map(t=>t.id),ids);assert.equal(M.totals(L.rows(state)).expense,55000);assert.equal(L.groups(state,L.rows(state),'expense').find(c=>c.id==='cafe').amount,35000);}
state.transactions=M.mergeBank(state.transactions,[{...raw,amount:60000}],'2026-11');assert.equal(M.totals(L.rows(state)).expense,55000,'A changed source amount must not silently rewrite split amounts');
state.transactions=M.mergeBank(state.transactions,[],'2026-11');assert.equal(L.rows(state).length,0,'Cancelled source must not leave orphan split expenses');assert.ok(state.splits[raw.id],'Keep custom allocation if source returns');
state.transactions=M.mergeBank(state.transactions,[raw,{...raw,id:'bank:new',amount:10000},{...raw,id:'bank:other',description:'Другой магазин',amount:5000}],'2026-11');assert.equal(L.rows(state).length,4);assert.equal(L.rows(state).find(t=>t.id==='bank:new').categoryId,'food');assert.equal(L.rows(state).find(t=>t.id==='bank:other').categoryId,'other');
assert.equal(L.merchants(state,L.rows(state),'expense','food')[0].amount,30000,'Merchant totals include only the category portion, not original total');
delete state.splits[raw.id];assert.equal(L.rows(state).length,3);assert.equal(M.totals(L.rows(state)).expense,70000);
assert.equal(L.validSplit({total:100,parts:[{amount:80,description:'a',categoryId:'food'},{amount:30,description:'b',categoryId:'cafe'}]}),false);
assert.equal(L.validSplit({total:100,parts:[{amount:100,description:'a',categoryId:'food'},{amount:0,description:'b',categoryId:'cafe'}]}),false);
console.log('Split and merchant checks passed: repeated sync, restart, stable IDs, category totals, cancellation, return, merge and validation.');
