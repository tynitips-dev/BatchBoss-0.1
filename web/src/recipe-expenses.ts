const positive=(n:number)=>Number.isFinite(n)&&n>=0?n:0
/** Estimated appliance usage; duty cycle is 0..100 percent. */
export function applianceElectricityCost(watts:number,hours:number,randPerKwh:number,usagePercent=100){
 return positive(watts)/1000*positive(hours)*positive(randPerKwh)*Math.min(100,positive(usagePercent))/100
}
/** Monthly indirect business costs spread over productive hours. */
export function allocatedOverhead(monthlyOverheads:number,monthlyProductionHours:number,batchHours:number):number|null{
 if(!Number.isFinite(monthlyProductionHours)||monthlyProductionHours<=0)return null
 return positive(monthlyOverheads)/monthlyProductionHours*positive(batchHours)
}
