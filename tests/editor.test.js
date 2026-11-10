const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
let stored=JSON.stringify({transactions:[{id:'bank:one',date:'2026-11-02',description:'BANK NAME',amount:50000,type:'expense',category:'Продукты',source:'bank',review:false}],syncedMonths:['2026-11']});
function boot(){const fields={app:{innerHTML:''},sheetContent:{innerHTML:''},toast:{style:{}},sheet:{open:false,addEventListener(){},showModal(){this.open=true;},close(){this.open=false;}}};const calls=[];const context={Money:require('../app/src/main/assets/core.js'),Ledger:require('../app/src/main/assets/ledger.js'),console,setTimeout(){},document:{querySelector:s=>fields[s.slice(1)]||null,addEventListener(){}},window:{},Native:{load:()=>stored,save:s=>{stored=s;return true;},directStatus:()=>'{"connected":true}',assetsStatus:()=>'{"okxConnected":false}',syncBank:()=>calls.push('sber')}};vm.createContext(context);for(const file of ['app','ui'])vm.runInContext(fs.readFileSync('app/src/main/assets/'+file+'.js','utf8'),context);return {context,fields,calls};}
let {context,fields,calls}=boot();
vm.runInContext("edit('bank:one')",context);
assert.doesNotMatch(fields.sheetContent.innerHTML,/readonly|disabled/i,'All operation fields must be editable');
Object.assign(fields,{editForm:{},editDescription:{value:'Продукты домой'},editAmount:{value:'720.50'},editType:{value:'expense'},editDate:{value:'2026-11-03'},editError:{textContent:''}});
vm.runInContext("draft.categoryId='services';saveEdit()",context);
let saved=JSON.parse(stored);assert.equal(saved.transactions[0].description,'BANK NAME');assert.equal(saved.operationEdits['bank:one'].description,'Продукты домой');assert.equal(saved.operationEdits['bank:one'].amount,72050);
({context,fields,calls}=boot());
vm.runInContext(`directUpdate({connected:true,snapshotMonth:'2026-11',snapshotMonths:['2026-10','2026-11'],operations:[{uohId:'one',date:'02.11.2026T12:00:00',state:{category:'executed',name:'FINANCIAL'},isFinancial:true,description:'NEW BANK TITLE',operationAmount:{amount:'-600.00',currencyCode:'RUB'}}]})`,context);
assert.equal(vm.runInContext("L.rows(state)[0].description",context),'Продукты домой','Edits must survive restart and native history refresh');
assert.equal(vm.runInContext("L.rows(state)[0].categoryId",context),'services');
assert.equal(vm.runInContext("state.syncedMonths.includes('2026-10')",context),true,'Empty month from full snapshot must be known');
assert.deepEqual(calls,[],'Editing and applying returned data never sign in');
vm.runInContext("edit('bank:one');actions.resetOperation()",context);
assert.equal(vm.runInContext("L.rows(state)[0].description",context),'NEW BANK TITLE');
const before=stored;vm.runInContext(`directUpdate({snapshotMonths:['2026-10'],operations:[{uohId:'bad',date:'02.11.2026T12:00:00',state:{category:'executed',name:'FINANCIAL'},isFinancial:true,description:'BAD',operationAmount:{amount:'-10',currencyCode:'RUB'}}]})`,context);assert.equal(stored,before,'Reject mismatched history atomically');
console.log('Editor integration passed: editable bank records, persistence across restart/sync, reset and atomic multi-month snapshots.');

({context,fields,calls}=boot());vm.runInContext("edit('bank:one');openSplit()",context);for(const [key,value] of Object.entries({splitName0:'Кофе',splitName1:'Печенье',splitAmount0:'400',splitAmount1:'200',splitCategory0:'cafe',splitCategory1:'food'}))fields[key]={value};fields.splitError={};vm.runInContext('saveSplit()',context);assert.equal(JSON.parse(stored).splits['bank:one'].parts.length,2);({context,fields,calls}=boot());assert.equal(vm.runInContext('L.rows(state).length',context),2);assert.equal(vm.runInContext('M.totals(L.rows(state)).expense',context),60000);vm.runInContext("edit(L.rows(state)[1].id)",context);assert.match(fields.sheetContent.innerHTML,/splitForm/);assert.match(fields.sheetContent.innerHTML,/Объединить обратно/);vm.runInContext('actions.unsplit()',context);assert.equal(vm.runInContext('L.rows(state).length',context),1);
