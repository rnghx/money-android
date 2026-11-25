const assert=require('node:assert/strict'),O=require('../app/src/main/assets/okx-stats.js');
const ts=d=>String(Date.parse(d+'T12:00:00+03:00')),start=O.START,until=Number(ts('2026-11-26'));
const bill=(id,date,type,pnl='0',fee='0',ccy='USDT',instType='SWAP')=>({billId:String(id),ts:ts(date),instType,type,ccy,pnl,fee});
const funding={...bill(3,'2026-11-06','8'),balChg:'-3',posBalChg:'0'};
const history={from:start,until,ranges:[{from:start,until}],fills:[{billId:'1',ts:ts('2026-11-05'),instType:'SWAP',fee:'-999'}],bills:[bill(1,'2026-11-05','2','50','-2'),bill(2,'2026-11-05','2','0','-1'),funding,{...bill(4,'2026-11-06','1'),balChg:'1000'},bill(5,'2026-11-07','2','-0.01','-0.0001','BTC','FUTURES'),bill(6,'2026-10-31','2','999'),bill(7,'2026-11-07','2','999','0','USDT','SPOT')]};
let r=O.summary(history,'2026-11');assert.equal(r.groups.find(g=>g.kind==='SWAP').net,O.decimal('44'));assert.equal(r.groups.find(g=>g.kind==='SWAP').profit,O.decimal('48'));assert.equal(r.groups.find(g=>g.kind==='SWAP').loss,O.decimal('4'));assert.equal(r.groups.find(g=>g.kind==='FUTURES').net,O.decimal('-0.0101'));assert.equal(r.counts.SWAP,1);assert.ok(r.complete);assert.equal(r.invalid,0);assert.ok(!r.groups.some(g=>g.kind==='SPOT'));
assert.equal(O.summary(history,'2026-10').groups.length,0,'No results before requested start month');assert.equal(O.summary(history,'2026-10').counts.SWAP,0);
const many={...history,fills:Array.from({length:250},(_,i)=>({billId:String(i+1000),ts:ts('2026-11-10'),instType:'FUTURES'})),bills:Array.from({length:250},(_,i)=>bill(i+1000,'2026-11-10','2','1','-0.01','USDT','FUTURES'))};r=O.summary(many,'2026-11');assert.equal(r.counts.FUTURES,250);assert.equal(r.groups[0].net,O.decimal('247.5'));assert.equal(r.invalid,0);
assert.equal(O.summary({...many,fills:[...many.fills,many.fills[0]],bills:[...many.bills,many.bills[0]]},'2026-11').counts.FUTURES,250);assert.throws(()=>O.summary({...history,bills:[...history.bills,{...history.bills[0],fee:'-10'}]},'2026-11'));
assert.equal(O.summary({...many,bills:[]},'2026-11').invalid,250,'Missing settlements must not imply zero profit');assert.equal(O.summary({...history,ranges:[{from:until-1000,until}]},'2026-11').complete,false);
assert.equal(O.month(String(Date.parse('2026-10-31T21:00:00Z'))),'2026-11');assert.equal(O.month(String(Date.parse('2026-10-31T20:59:59Z'))),'2026-10');assert.equal(O.text(O.decimal('-0.000001')),'−0,000001');assert.equal(O.text(O.decimal('1000.001')),'1 000,001');
const fundingPos={...history,fills:[],bills:[{...funding,balChg:'0',posBalChg:'4'}]};assert.equal(O.summary(fundingPos,'2026-11').groups[0].net,O.decimal('4'));
console.log('OKX futures monthly P&L passed: 250 fills, fees once, funding, currencies, duplicate protection, start month and Moscow boundaries.');

assert.equal(O.summary({...fundingPos,bills:[{...funding,balChg:'',posBalChg:'-2'}]},'2026-11').groups[0].net,O.decimal('-2'));
assert.equal(O.summary({...fundingPos,bills:[{...funding,type:'10',balChg:'-5'}]},'2026-11').groups[0].net,O.decimal('-5'));

assert.equal(O.text(O.decimal('-0.00000001')),'−0,00000001');assert.equal(O.text(O.decimal('0.000000000000000001')),'0,000000000000000001');

assert.equal(r.records.reduce((sum,row)=>sum+row.value,0n),r.groups.reduce((sum,g)=>sum+g.net,0n));
const explain={...history,fills:[],bills:[bill(100,'2026-11-05','2','7','-1'),{...bill(101,'2026-11-06','8'),balChg:'3'},bill(102,'2026-11-07','2','-4','0')]};
const explained=O.summary(explain,'2026-11');const positive=O.select(explained,'USDT','positive');assert.equal(positive.reduce((sum,row)=>sum+row.value,0n),O.decimal('9'));assert.equal(explained.groups[0].net,O.decimal('5'));
const report=O.report(explained,'USDT','positive');assert.match(report,/Сумма: 9 USDT/);assert.match(report,/billId: 100/);assert.match(report,/billId: 101/);assert.doesNotMatch(report,/billId: 102/);assert.match(report,/Финансирование/);assert.match(report,/P&L: 7; комиссия: −1/);assert.equal(O.select(explained,'BTC','positive').length,0);assert.equal(O.select(explained,'USDT','negative')[0].id,'102');
assert.ok(!O.report(O.summary(history,'2026-11'),'USDT').includes('billId: 6\n'),'Report excludes pre-November records');

const audit=O.summary(history,'2026-11');for(const g of audit.groups)assert.equal(audit.records.filter(r=>r.kind===g.kind&&r.ccy===g.ccy).reduce((sum,row)=>sum+row.value,0n),g.net);
