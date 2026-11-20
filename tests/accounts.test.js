const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const M=require('../app/src/main/assets/core.js'),L=require('../app/src/main/assets/ledger.js');
const capital={timestamp:Date.now(),unsupported:[],items:[{id:'a',key:'account-a',title:'Текущий',currency:'RUB',amount:'100',suffix:'1111'},{id:'alias',key:'account-a',title:'Дубль',currency:'RUB',amount:'100'},{id:'b',key:'account-b',title:'Карта',currency:'RUB',amount:'200',suffix:'2222'}]};
const expense=(id,accountKey,amount=100)=>({id,date:'2026-11-10',description:'Магазин',amount,type:'expense',category:'Продукты',source:'bank',accountKey});
let saved=JSON.stringify({schema:1,transactions:[expense('one','account-a'),expense('two','account-b'),expense('legacy',undefined)],capitalHistory:[],operationEdits:{one:{description:'Правка'}},splits:{two:{total:100,parts:[{amount:60,categoryId:'food',description:'Еда'},{amount:40,categoryId:'cafe',description:'Кофе'}]}},goal:{name:'Цель',targetMinor:100000,currency:'RUB'}});
let failSave=false;const calls=[];
function boot(){const elements={app:{},sheetContent:{},toast:{style:{}},sheet:{open:false,showModal(){this.open=true;},close(){this.open=false;},addEventListener(){}}};const handlers={};const c={Money:M,Ledger:L,console,setTimeout(){},Native:{load:()=>saved,save:s=>{if(failSave)return false;saved=s;return true;},directStatus:()=>JSON.stringify({connected:true,capital}),assetsStatus:()=>JSON.stringify({}),saveWidget(){},syncBank:()=>calls.push('sync')},document:{querySelector:s=>elements[s.slice(1)]||null,addEventListener:(t,f)=>(handlers[t]??=[]).push(f)},window:{}};vm.createContext(c);for(const f of ['app','ui'])vm.runInContext(fs.readFileSync('app/src/main/assets/'+f+'.js','utf8'),c);return {c,elements,handlers};}
let {c,elements,handlers}=boot();
assert.doesNotMatch(elements.app.innerHTML,/Текущий|Карта|bank-accounts/);assert.match(elements.app.innerHTML,/data-action="bankAccounts"/);vm.runInContext('actions.bankAccounts()',c);assert.match(elements.sheetContent.innerHTML,/Текущий/);assert.match(elements.sheetContent.innerHTML,/Карта/);assert.doesNotMatch(elements.sheetContent.innerHTML,/Дубль/);
vm.runInContext('actions.bankSettings()',c);assert.doesNotMatch(elements.sheetContent.innerHTML,/Показывать на главной/);assert.match(elements.sheetContent.innerHTML,/Показывать в источнике/);assert.equal((elements.sheetContent.innerHTML.match(/data-account-field=/g)||[]).length,4);
handlers.change[0]({target:{dataset:{accountKey:'account-a',accountField:'visible'},checked:false}});
vm.runInContext('actions.bankAccounts()',c);assert.doesNotMatch(elements.sheetContent.innerHTML,/Текущий/);assert.match(elements.sheetContent.innerHTML,/Карта/);assert.equal(vm.runInContext("M.portfolio(bank,assets,'RUB',state.accountPreferences).amount",c),30000,'Visibility does not change totals');
vm.runInContext("setAccountPreference('account-b','included',false)",c);
assert.equal(vm.runInContext("M.portfolio(bank,assets,'RUB',state.accountPreferences).amount",c),10000);
assert.equal(vm.runInContext("L.groups(state,L.rows(state),'expense').reduce((s,g)=>s+g.amount,0)",c),200,'Split parts excluded; unknown legacy account retained');
assert.equal(vm.runInContext('L.rows(state).length',c),4,'Transactions and split parts remain available');
assert.equal(vm.runInContext('L.monthly(state,\'2026-11\',\'expense\').at(-1).amount',c),200);
assert.equal(vm.runInContext('state.operationEdits.one.description',c),'Правка');assert.equal(vm.runInContext('state.goal.name',c),'Цель');
const history=JSON.parse(saved).capitalHistory;assert.ok(history.some(p=>(p.scope||'[]')==='[]'));assert.ok(history.some(p=>p.scope==='["account-b"]'),'Account selection does not overwrite original history');
({c,elements}=boot());assert.equal(vm.runInContext('state.accountPreferences["account-a"].visible',c),false);assert.equal(vm.runInContext('state.accountPreferences["account-b"].included',c),false);
failSave=true;vm.runInContext("setAccountPreference('account-b','included',true)",c);assert.equal(vm.runInContext('state.accountPreferences["account-b"].included',c),false);failSave=false;
vm.runInContext("setAccountPreference('account-b','included',true)",c);assert.equal(vm.runInContext("M.portfolio(bank,assets,'RUB',state.accountPreferences).amount",c),30000);
assert.deepEqual(calls,[],'Preference changes and restart must not access the bank');
const raw={uohId:'x',date:'10.11.2026T12:00:00',isFinancial:true,state:{category:'executed',name:'FINANCIAL'},operationAmount:{amount:'-10',currencyCode:'RUB'},commission:{amount:'1',currencyCode:'RUB'},accountKey:'account-a',feeAccountKey:'account-b'};
const converted=M.bankRows([raw]);assert.equal(converted[0].accountKey,'account-a');assert.equal(converted[1].accountKey,'account-b');
assert.equal(M.portfolio({connected:true,capital},{},'RUB',{'account-a':{included:false},'account-b':{included:false}}).amount,0);
console.log('Account selection: independent switches, saved restart, safe rollback, aliases, splits, totals, history and legacy data passed.');

capital.items[0].previousKey='previous-card';saved=JSON.stringify({schema:1,transactions:[],accountPreferences:{'previous-card':{visible:false,included:false}}});({c,elements}=boot());assert.equal(vm.runInContext('state.accountPreferences["account-a"].included',c),false);assert.equal(vm.runInContext('state.accountPreferences["previous-card"].included',c),false);assert.doesNotMatch(elements.app.innerHTML,/Текущий/);
