const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'), ts=require('../web/node_modules/typescript');
function load(path,dependencies={}){
 const source=ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
 const mod={exports:{}}; new Function('require','exports','module',source)(name=>{if(!(name in dependencies))throw Error(name);return dependencies[name]},mod.exports,mod); return mod.exports;
}
const u=load('web/src/workspace-utils.ts'), c=load('web/src/costing.ts',{'./workspace-utils':u});
const close=(a,b)=>assert.ok(Math.abs(a-b)<1e-9, a+' != '+b);
test('regional defaults and currency formatting preserve South African legacy',()=>{
 assert.equal(u.regionDefaults('ZA').currency,'ZAR');
 assert.equal(u.regionDefaults('US').currency,'USD');
 assert.equal(u.regionDefaults('GB').currency,'GBP');
 assert.equal(u.regionDefaults('AU').currency,'AUD');
 assert.equal(u.regionDefaults('CA').currency,'CAD');
 assert.equal(u.regionDefaults('NZ').currency,'NZD');
 assert.equal(u.regionDefaults('XX'),null);
 assert.equal(u.formatRegionalMoney(1234.5,'ZAR','en-ZA'),'R 1 234.50');
 assert.match(u.formatRegionalMoney(42,'USD','en-US'),/USD.*42\.00/);
 assert.match(u.formatRegionalMoney(42,'JPY','ja-JP'),/JPY.*42/);
 assert.throws(()=>u.formatRegionalMoney(NaN,'USD','en-US'));
});
test('explicit measures avoid ambiguous units and preserve legacy cup',()=>{
 assert.equal(u.convertUnits(1,'cup','ml'),250);
 close(u.convertUnits(1,'lb','g'),453.59237);
 close(u.convertUnits(16,'oz','lb'),1);
 close(u.convertUnits(1,'cup_us','ml'),236.5882365);
 close(u.convertUnits(1,'cup_metric','ml'),250);
 close(u.convertUnits(1,'fl_oz_uk','ml'),28.4130625);
 close(u.convertUnits(1,'fl_oz_us','ml'),29.5735295625);
 close(u.convertUnits(1,'tbsp_au','ml'),20);
 assert.equal(u.convertUnits(1,'oz','fl_oz_us'),null);
 assert.equal(u.convertUnits(1,'cup_us','g'),null);
 assert.equal(u.convertTemperature(180,'C','F'),356);
 assert.equal(u.convertTemperature(32,'F','C'),0);
});
test('ingredient costing respects explicit units and does not guess density',()=>{
 const calc=(quantity,unit,stock)=>c.calculateIngredientUsageCost({name:stock.name,quantity,unit,cost:0},[stock]);
 const flour={name:'Flour',unit:'lb',packageQuantity:1,packagePrice:32};
 assert.equal(calc(8,'oz',flour),16);
 assert.equal(calc(1,'cup_us',flour),null);
 const milk={name:'Milk',unit:'L',packageQuantity:1,packagePrice:100};
 assert.equal(calc(1,'cup_us',milk),23.66);
 assert.equal(calc(1,'cup',milk),25);
 assert.equal(calc(1,'tbsp_au',milk),2);
});

test('regional signup is gated before account creation and writes explicit South African defaults',()=>{
 assert.equal(u.launchCountries.length,6);
 assert.deepEqual(u.regionalSignupFields('ZA'),{
 countryCode:'ZA',currency:'ZAR',locale:'en-ZA',measurementSystem:'metric',temperatureUnit:'C',regionalVersion:1
 });
 for(const country of ['GB','US','CA','AU','NZ']){
  assert.equal(u.regionalRegistration(country).registrationEnabled,false);
  assert.throws(()=>u.regionalSignupFields(country),/not available/);
 }
 assert.equal(u.regionalRegistration('XX'),null);
 assert.throws(()=>u.regionalSignupFields('XX'));
});
test('workspace summary never relabels legacy account balances from a country selection',()=>{
 const legacy=u.regionalWorkspaceSummary({});
 assert.equal(legacy.currency,'ZAR (legacy default)');
 assert.equal(legacy.measurement,'Existing recipe units');
 const mismatched=u.regionalWorkspaceSummary({countryCode:'US',currency:'ZAR'});
 assert.equal(mismatched.currency,'ZAR');
 const created=u.regionalWorkspaceSummary(u.regionalSignupFields('ZA'));
 assert.equal(created.country,'South Africa');
 assert.equal(created.currency,'ZAR');
 assert.equal(created.temperature,'Celsius');
});

test('decimal keyboard input keeps fractional zeros and removes redundant leading zeros',()=>{
 for(const [raw,expected] of [['04','4'],['00049','49'],['0.59','0.59'],['00.59','0.59'],['.59','0.59'],['4,99','4.99'],['0,59','0.59'],['4.','4.'],['',''],['-0.59','-0.59'],['4.990','4.990']]){
  assert.equal(u.normalizeDecimalDraft(raw),expected,raw)
 }
 for(const raw of ['4.99','0.59','4.','-4.99'])assert.equal(u.isDecimalDraft(raw),true)
 for(const raw of ['1.2.3','-','abc','1e3','1 234'])assert.equal(u.isDecimalDraft(raw),false)
 assert.match(u.formatRegionalMoney(4.99,'EUR','de-DE'),/4\.99/)
 assert.match(u.formatRegionalMoney(1234.59,'EUR','de-DE'),/1 234\.59/)
});

test('record currency is explicit and legacy ZAR is not relabelled',()=>{
 assert.equal(u.recordCurrency({}), 'ZAR');
 assert.equal(u.recordCurrency({currency:'USD'}), 'USD');
 assert.equal(u.recordCurrency({currency:null}), null);
 assert.equal(u.recordCurrency({currency:'oops'}), null);
 assert.equal(u.formatRecordMoney(4.99,{}),'R 4.99');
 assert.match(u.formatRecordMoney(4.99,{currency:'USD'}),/USD.*4\.99/);
 assert.equal(u.formatRecordMoney(4.99,{currency:null}),'Amount or currency unavailable');
 const totals=u.formatRecordTotals([{total:5},{total:10,currency:'ZAR'},{total:2,currency:'USD'}],'total');
 assert.match(totals,/R 15\.00/);assert.match(totals,/USD.*2\.00/);
 assert.match(u.formatRecordTotals([{total:1,currency:null}],'total'),/unavailable/);
});
test('costing does not mix differently denominated stock',()=>{
 const row={name:'Flour',quantity:500,unit:'g',cost:0};
 const stock={name:'Flour',unit:'kg',packageQuantity:1,packagePrice:20,currency:'USD'};
 assert.equal(c.calculateIngredientUsageCost(row,[stock]),null);
 assert.equal(c.calculateIngredientUsageCost(row,[stock],'USD'),10);
 assert.equal(c.calculateIngredientUsageCost(row,[{...stock,currency:undefined}]),10);
});
