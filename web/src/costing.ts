export type IngredientUsage={name:string;quantity:number;unit:string;cost:number}
type StockItem=Record<string,unknown>
export function calculateIngredientUsageCost(row:IngredientUsage,inventory:StockItem[]):number|null{
 const stock=inventory.find(item=>String(item.name||'').trim().toLowerCase()===row.name.trim().toLowerCase())
 if(!stock||row.quantity<=0)return null
 const stockUnit=String(stock.unit||'g')
 const packPrice=Number(stock.packagePrice||0)
 const packQuantity=Number(stock.gramsPerUnit||stock.packageQuantity||0)
 const savedUnitPrice=Number(stock.unitPrice||0)
 const unitPrice=savedUnitPrice>0?savedUnitPrice:packQuantity>0?packPrice/packQuantity:0
 if(unitPrice<=0)return null
 const mass=stockUnit==='g'||stockUnit==='kg'
 const volume=stockUnit==='ml'||stockUnit==='L'
 let used:number|null=null
 if(row.unit==='g'&&mass)used=row.quantity
 if(row.unit==='kg'&&mass)used=row.quantity*1000
 if(row.unit==='ml'&&volume)used=row.quantity
 if(row.unit==='L'&&volume)used=row.quantity*1000
 if(row.unit==='tsp'&&volume)used=row.quantity*5
 if(row.unit==='tbsp'&&volume)used=row.quantity*15
 if(row.unit==='unit'&&!mass&&!volume)used=row.quantity
 return used===null?null:Number((used*unitPrice).toFixed(2))
}
