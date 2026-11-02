export type RecipeCostInputs = {
 ingredients:number; labourHours:number; labourRate:number;
 overheads:number; utilities:number; packaging:number; yieldCount:number; servingsPerUnit:number; marginPercent:number
}
const safe=(value:number)=>Number.isFinite(value)&&value>=0?value:0
/** Gross margin is profit as a percentage of selling price, not markup on cost. */
export function priceRecipe(inputs:RecipeCostInputs){
 const ingredients=safe(inputs.ingredients)
 const labour=safe(inputs.labourHours)*safe(inputs.labourRate)
 const overheads=safe(inputs.overheads),utilities=safe(inputs.utilities),packaging=safe(inputs.packaging)
 const total=ingredients+labour+overheads+utilities+packaging
 const yieldCount=safe(inputs.yieldCount)
 const perUnit=yieldCount>0?total/yieldCount:null
 const servingsPerUnit=safe(inputs.servingsPerUnit)
 const perServing=perUnit!==null&&servingsPerUnit>0?perUnit/servingsPerUnit:null
 const margin=inputs.marginPercent
 const validMargin=Number.isFinite(margin)&&margin>=0&&margin<100
 const sellingBatch=validMargin?total/(1-margin/100):null
 const sellingUnit=validMargin&&yieldCount>0?sellingBatch!/yieldCount:null
 return {ingredients,labour,overheads,utilities,packaging,total,perUnit,perServing,sellingBatch,sellingUnit,validMargin}
}
export const sampleRecipeCosts=[
 {name:'12 Vanilla cupcakes',ingredients:72,labourHours:0.5,labourRate:120,overheads:12,utilities:8,packaging:24,yieldCount:12,servingsPerUnit:1,marginPercent:40},
 {name:'1 Celebration cake',ingredients:130,labourHours:1,labourRate:120,overheads:25,utilities:15,packaging:35,yieldCount:1,servingsPerUnit:12,marginPercent:35},
 {name:'20 Chocolate cookies',ingredients:90,labourHours:0.75,labourRate:100,overheads:15,utilities:10,packaging:20,yieldCount:20,servingsPerUnit:1,marginPercent:30},
] as const satisfies ReadonlyArray<RecipeCostInputs&{name:string}>
