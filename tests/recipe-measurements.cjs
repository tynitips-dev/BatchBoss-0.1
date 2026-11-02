const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('../web/node_modules/typescript');
function load(path, dependencies={}) {
 const source=ts.transpileModule(fs.readFileSync(path,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
 const mod={exports:{}};
 new Function('require','exports','module',source)(name=>dependencies[name],mod.exports,mod);
 return mod.exports;
}
const costing=load('web/src/costing.ts');
const flour={name:'Flour',unit:'kg',packageQuantity:1,packagePrice:30};
test('cake flour matches flour, with exact names preferred and ambiguous stock rejected',()=>{
 assert.equal(costing.findMatchingStock('Cake flour',[flour]),flour);
 const cake={...flour,name:'Cake flour'};
 assert.equal(costing.findMatchingStock('Cake flour',[flour,cake]),cake);
 assert.equal(costing.findMatchingStock('Cake flour',[flour,{...flour}]),null);
 assert.equal(costing.findMatchingStock('Cake flour',[{...flour,archived:true}]),null);
 for(const name of ['Self-raising flour','Bread flour','Almond flour','Gluten-free flour']) assert.equal(costing.findMatchingStock(name,[flour]),null);
});
test('recipe costing converts requested units without guessing density',()=>{
 const cost=(name,quantity,unit,stock)=>costing.calculateIngredientUsageCost({name,quantity,unit,cost:0},[stock]);
 assert.equal(cost('Cake flour',500,'g',flour),15);
 assert.equal(cost('Cake flour',0.5,'kg',flour),15);
 const milk={name:'Milk',unit:'L',packageQuantity:1,packagePrice:20};
 for(const [quantity,unit,expected] of [[500,'ml',10],[0.5,'L',10],[2,'tsp',0.2],[2,'tablespoon',0.6],[2,'tablespoons',0.6]]) assert.equal(cost('Milk',quantity,unit,milk),expected);
 assert.equal(cost('Cake flour',1,'tablespoon',flour),null);
});
test('PDF ingredient extraction preserves plural measurements and matches flour for costing',async()=>{
 const lines=['Cake','Ingredients','500 g cake flour','1 kg flour','100 ml milk','1 L milk','2 teaspoons milk','2 tablespoons milk','1 teaspoon milk','1 tablespoon milk','2 millilitres milk','Method','Mix.'];
 const parser=load('web/src/recipePdfImport.ts',{
  './costing':costing,
  'pdfjs-dist/build/pdf.worker.min.mjs?url':{default:'worker'},
  'pdfjs-dist':{GlobalWorkerOptions:{},getDocument:()=>({promise:Promise.resolve({numPages:1,getPage:async()=>({getTextContent:async()=>({items:lines.map(str=>({str,hasEOL:true}))})})})})}
 });
 const recipe=await parser.extractRecipePdf({type:'application/pdf',name:'cake.pdf',size:10,arrayBuffer:async()=>new ArrayBuffer(0)});
 assert.deepEqual(recipe.ingredients.map(row=>row.unit),['g','kg','ml','L','tsp','tbsp','tsp','tbsp','ml']);
 assert.equal(costing.calculateIngredientUsageCost(recipe.ingredients[0],[flour]),15);
});
