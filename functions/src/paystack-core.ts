import {createHmac,timingSafeEqual} from 'node:crypto';
export function validSignature(body:Buffer,signature:string,secret:string){
 if(!/^[a-f0-9]{128}$/i.test(signature))return false;
 const expected=createHmac('sha512',secret).update(body).digest();
 return timingSafeEqual(expected,Buffer.from(signature,'hex'));
}
export function periodEnd(paidAt:string,interval:'monthly'|'annual'){
 const paid=new Date(paidAt);if(!Number.isFinite(paid.getTime()))throw new Error('Invalid paid date');
 const y=paid.getUTCFullYear()+(interval==='annual'?1:0),m=paid.getUTCMonth()+(interval==='monthly'?1:0);
 const day=Math.min(paid.getUTCDate(),interval==='monthly'?28:new Date(Date.UTC(y,m+1,0)).getUTCDate());
 return new Date(Date.UTC(y,m,day,paid.getUTCHours(),paid.getUTCMinutes(),paid.getUTCSeconds()));
}
export function matchesPayment(tx:any,expected:{amount:number;currency:string;customer:string;planCode:string;domain:string}){
 const code=typeof tx.plan==='string'?tx.plan:tx.plan?.plan_code||tx.plan_object?.plan_code;
 return tx.status==='success'&&tx.amount===expected.amount&&tx.currency===expected.currency&&tx.domain===expected.domain&&tx.customer?.customer_code===expected.customer&&code===expected.planCode&&typeof tx.reference==='string'&&/^[a-zA-Z0-9_.-]{1,100}$/.test(tx.reference)&&Number.isFinite(new Date(tx.paid_at).getTime());
}
