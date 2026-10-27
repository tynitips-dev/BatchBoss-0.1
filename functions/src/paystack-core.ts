import {createHmac,timingSafeEqual} from 'node:crypto';
export function validSignature(body:Buffer,signature:string,secret:string){
 if(!/^[a-f0-9]{128}$/i.test(signature))return false;
 const expected=createHmac('sha512',secret).update(body).digest();
 return timingSafeEqual(expected,Buffer.from(signature,'hex'));
}
// Do not trust Date.parse alone: JavaScript normalizes impossible calendar
// dates such as February 31. Paystack settlement needs a real ISO instant.
function validPaidInstant(value:unknown):value is string{
 if(typeof value!=='string')return false;
 const m=/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|([+-])(\d{2}):(\d{2}))$/.exec(value);
 if(!m)return false;
 const [,year,month,day,hour,minute,second,zone,,offsetHours,offsetMinutes]=m;
 const y=Number(year),mo=Number(month),d=Number(day);
 if(mo<1||mo>12||d<1||d>new Date(Date.UTC(y,mo,0)).getUTCDate()||
    Number(hour)>23||Number(minute)>59||Number(second)>59)return false;
 if(zone!=='Z'&&(Number(offsetHours)>14||Number(offsetMinutes)>59||
    (Number(offsetHours)===14&&Number(offsetMinutes)!==0)))return false;
 return Number.isFinite(Date.parse(value));
}
export function periodEnd(paidAt:string,interval:'monthly'|'annual'){
 if(!validPaidInstant(paidAt))throw new Error('Invalid paid date');
 const paid=new Date(paidAt);
 const y=paid.getUTCFullYear()+(interval==='annual'?1:0),m=paid.getUTCMonth()+(interval==='monthly'?1:0);
 const day=Math.min(paid.getUTCDate(),interval==='monthly'?28:new Date(Date.UTC(y,m+1,0)).getUTCDate());
 return new Date(Date.UTC(y,m,day,paid.getUTCHours(),paid.getUTCMinutes(),paid.getUTCSeconds()));
}
export function matchesPayment(tx:any,expected:{amount:number;currency:string;customer:string;planCode:string;domain:string}){
 const code=typeof tx.plan==='string'?tx.plan:tx.plan?.plan_code||tx.plan_object?.plan_code;
 return tx.status==='success'&&tx.amount===expected.amount&&tx.currency===expected.currency&&tx.domain===expected.domain&&tx.customer?.customer_code===expected.customer&&code===expected.planCode&&typeof tx.reference==='string'&&/^[a-zA-Z0-9_.-]{1,100}$/.test(tx.reference)&&validPaidInstant(tx.paid_at);
}
