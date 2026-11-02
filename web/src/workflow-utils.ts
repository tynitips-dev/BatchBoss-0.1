export type RecordItem={id:string;[key:string]:unknown}
export const roundMoney=(n:number)=>Math.round((n+Number.EPSILON)*100)/100
export function pricedQuantity(quantity:number,price:number){
 if(!Number.isFinite(quantity)||quantity<=0||!Number.isFinite(price)||price<0)throw new Error('Enter a quantity above zero and a valid unit price.')
 return roundMoney(quantity*price)
}
const text=(v:unknown)=>String(v??'').trim().toLowerCase()
const phone=(v:unknown)=>String(v??'').replace(/\D/g,'').replace(/^0/,'27')
export function belongsToCustomer(record:RecordItem,customer:RecordItem,customers:RecordItem[]){
 if(record.customerId)return record.customerId===customer.id
 const email=text(record.clientEmail||record.email),tel=phone(record.phone)
 // Legacy documents only match unique contact details, never a name alone.
 if(email&&email===text(customer.email)&&customers.filter(c=>text(c.email)===email).length===1)return true
 return !!tel&&tel===phone(customer.phone)&&customers.filter(c=>phone(c.phone)===tel).length===1
}
export function recordDate(record:RecordItem){
 const value=record.createdAt as {toDate?:()=>Date}|number|string|undefined
 const d=typeof value==='object'?value?.toDate?.():value?new Date(value):null
 return d&&!Number.isNaN(d.getTime())?d.toLocaleDateString('en-ZA'):'Date not recorded'
}
