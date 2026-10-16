export type IngredientUsage={name:string;quantity:number;unit:string;cost:number}
type StockItem=Record<string,unknown>
const norm=(s:unknown)=>String(s??'').toLowerCase().replace(/\([^)]*\)/g,'').replace(/[^a-z0-9]+/g,' ').trim().replace(/\s+/g,' ')
const qty=(v:unknown)=>{const n=Number(v);return Number.isFinite(n)&&n>0?n:0}
const baseUnit=(s:unknown)=>{
 const x=String(s??'').trim().toLowerCase()
 if(['g','gram','grams'].includes(x))return 'g'
 if(['kg','kilogram','kilograms'].includes(x))return 'kg'
 if(['ml','millilitre','milliliter','millilitres','milliliters'].includes(x))return 'ml'
 if(['l','litre','liter','litres','liters'].includes(x))return 'L'
 if(['tsp','teaspoon','teaspoons'].includes(x))return 'tsp'
 if(['tbsp','tablespoon','tablespoons'].includes(x))return 'tbsp'
 if(['cup','cups'].includes(x))return 'cup'
 if(['unit','units','pcs','pieces','piece','each','egg','eggs'].includes(x))return 'unit'
 return x
}
const dimension=(u:string)=>['g','kg'].includes(u)?'mass':['ml','L','tsp','tbsp','cup'].includes(u)?'volume':u==='unit'?'count':'unknown'
const toBase=(quantity:number,unit:string)=>quantity*({kg:1000,L:1000,tsp:5,tbsp:15,cup:250}[unit]??1)
/**
 * Returns null for missing prices or incompatible measures. No guessed
 * conversions from volume to mass (e.g. flour cups to grams).
 */
export function calculateIngredientUsageCost(row:IngredientUsage,inventory:StockItem[]):number|null{
 const name=norm(row.name)
 const stock=inventory.find(item=>norm(item.name)===name&&!item.archived)
 if(!stock||qty(row.quantity)===0)return null
 const stockUnit=baseUnit(stock.unit||'unit'),recipeUnit=baseUnit(row.unit)
 if(dimension(stockUnit)!==dimension(recipeUnit)||dimension(stockUnit)==='unknown')return null
 const packPrice=qty(stock.packagePrice)
 const packQuantity=qty(stock.packageQuantity)
 const savedUnitPrice=qty(stock.unitPrice)
 // Inventory's unitPrice is stored per base unit (g, ml or item).
 const pricePerBase=savedUnitPrice|| (packPrice&&packQuantity?packPrice/toBase(packQuantity,stockUnit):0)
 if(!pricePerBase)return null
 return Number((toBase(row.quantity,recipeUnit)*pricePerBase).toFixed(2))
}
