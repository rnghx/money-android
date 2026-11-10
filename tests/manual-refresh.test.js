const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const calls=[];const handlers={};
const elements={app:{innerHTML:''},sheetContent:{innerHTML:''},file:{},toast:{style:{}}};
elements.connection={insertAdjacentHTML(where,html){elements.sheetContent.innerHTML+=html;}};
elements.sheet={open:false,showModal(){this.open=true;},close(){this.open=false;},addEventListener(){}};
const context={Ledger:require('../app/src/main/assets/ledger.js'),Money:require('../app/src/main/assets/core.js'),console,setTimeout(){},crypto:require('node:crypto'),Native:{
 load:()=>JSON.stringify({transactions:[],seen:[],schema:1}),save:()=>true,
 directStatus:()=>JSON.stringify({connected:true,busy:false,error:'',lastSync:1,month:'2020-01'}),
 assetsStatus:()=>JSON.stringify({okxConnected:true,okxBusy:false,fxBusy:false,fx:{rubPerUsd:'90',date:'2026-11-09'}}),
 checkAppUpdate:()=>calls.push('app-update'),syncBank:()=>calls.push('sber'),syncOkx:()=>calls.push('okx'),syncFx:()=>calls.push('fx'),connectBank:()=>calls.push('register'),connectOkx:()=>calls.push('okx-connect')},
 document:{querySelector(selector){const id=selector.slice(1);return elements[id]||null;},addEventListener(type,fn){(handlers[type]??=[]).push(fn);}},window:{}};
vm.createContext(context);vm.runInContext(fs.readFileSync('app/src/main/assets/app.js','utf8'),context);vm.runInContext(fs.readFileSync('app/src/main/assets/ui.js','utf8'),context);
assert.deepEqual(calls,[],'Launching a connected app with old data must not fetch anything');
assert.match(elements.app.innerHTML,/<span class="capital-label">Капитал<\/span>/);
assert.doesNotMatch(elements.app.innerHTML,/Загрузить выписку|Баланс OKX|Курс ЦБ|fx-caption|data-action="import"/);
vm.runInContext('refreshNative(); shiftMonth(-1); shiftMonth(1); actions.settings(); refreshNative();',context);
assert.match(elements.sheetContent.innerHTML,/Курс ЦБ/);
vm.runInContext('actions.sources()',context);assert.match(elements.sheetContent.innerHTML,/Источники/);
for(const fn of handlers.click)fn({target:{closest(){return {dataset:{currency:'USD'}};}}});
vm.runInContext("page='analytics';render();page='operations';render();actions.goal();actions.categories();",context);
assert.deepEqual(calls,[],'Resume, navigation, settings and currency switch must stay offline');
vm.runInContext('actions.sync()',context);assert.deepEqual(calls,['sber']);
calls.length=0;vm.runInContext('actions.syncOkx()',context);assert.deepEqual(calls,['okx','fx']);
calls.length=0;vm.runInContext('actions.refreshAll()',context);assert.deepEqual(calls,['sber','okx','fx']);
assert.equal(calls.includes('register'),false);
console.log('Manual-only regression passed: no requests on launch/resume/month/currency/settings; explicit refresh only.');

vm.runInContext('closeSheet();refreshNative()',context);assert.equal(elements.sheet.open,false,'Resume must not reopen retained sheet contents');
vm.runInContext("page='analytics';render()",context);assert.doesNotMatch(elements.app.innerHTML,/class="bars"|data-month=/);
vm.runInContext("page='accounts';render()",context);assert.doesNotMatch(elements.app.innerHTML,/source-mark/);

vm.runInContext("page='accounts';state.goal={name:'Private goal',targetMinor:1000000,currency:'RUB'};render()",context);assert.doesNotMatch(elements.app.innerHTML,/Private goal|data-currency=|Цель капитала<|>Счета<\/h1>/);assert.match(elements.app.innerHTML,/goal-minimal/);vm.runInContext('actions.settings()',context);assert.match(elements.sheetContent.innerHTML,/data-currency=/);

vm.runInContext("state.capitalHistory=[{date:month+'-01',timestamp:1,RUB:100000,USD:1000},{date:month+'-09',timestamp:2,RUB:150000,USD:1500}];page='analytics';render()",context);assert.match(elements.app.innerHTML,/capital-line/);assert.doesNotMatch(elements.app.innerHTML,/NaN|Infinity/);

calls.length=0;vm.runInContext('actions.appUpdate()',context);assert.deepEqual(calls,['app-update']);

vm.runInContext("page='accounts';render()",context);assert.doesNotMatch(elements.app.innerHTML,/capital-chart|capital-line/);vm.runInContext('actions.settings()',context);assert.doesNotMatch(elements.sheetContent.innerHTML,/class="primary"/);vm.runInContext("page='analytics';render()",context);assert.ok(elements.app.innerHTML.indexOf('capital-chart')<elements.app.innerHTML.indexOf('class="donut"'));assert.equal((elements.app.innerHTML.match(/class="period"/g)||[]).length,1);
