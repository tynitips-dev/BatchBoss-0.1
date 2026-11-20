const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('../web/node_modules/typescript');
const jspdf=require('../web/node_modules/jspdf');
const transpiled=ts.transpileModule(fs.readFileSync('web/src/invoicePdf.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const workspaceSource=ts.transpileModule(fs.readFileSync('web/src/workspace-utils.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const workspace={exports:{}};new Function('exports','module',workspaceSource)(workspace.exports,workspace);
const mod={exports:{}};
new Function('require','exports','module',transpiled)(name=>{if(name==='jspdf')return jspdf;if(name==='./workspace-utils')return workspace.exports;throw Error(name)},mod.exports,mod);
const {generateInvoicePdf,invoicePdfName}=mod.exports;
const business={name:'Test Bakery',email:'test@example.com',bankName:'Test bank',accountNumber:'1234'};
test('invoice produces valid PDF bytes and sanitized filename',()=>{
 const invoice={invoiceNumber:'INV/001',clientName:'Example customer',subtotal:80,discount:5,vat:0,total:75,items:[{name:'Cake',quantity:2,unitPrice:40,lineTotal:80}]};
 const doc=generateInvoicePdf(invoice,business);
 const bytes=Buffer.from(doc.output('arraybuffer'));
 assert.equal(bytes.subarray(0,5).toString(),'%PDF-');
 assert.ok(bytes.toString('latin1').includes('%%EOF'));
 assert.equal(doc.getNumberOfPages(),1);
 assert.equal(invoicePdfName(invoice),'INV-001.pdf');
});
test('long invoices paginate, missing or malformed logo does not block export',()=>{
 const items=Array.from({length:150},(_,i)=>({name:'Decorated cake order '+i+' '+ 'long description '.repeat(5),quantity:1,lineTotal:10}));
 const doc=generateInvoicePdf({invoiceNumber:'INV-002',clientName:'Example',items,total:1500},{...business,logoDataUrl:'data:image/png;base64,invalid'});
 assert.ok(doc.getNumberOfPages()>1);
 assert.equal(Buffer.from(doc.output('arraybuffer')).subarray(0,5).toString(),'%PDF-');
});
test('legacy amount and summary records can still be exported',()=>{
 const doc=generateInvoicePdf({id:'legacy-123',clientName:'Legacy customer',amount:100,orderDescription:'Cake'},business);
 assert.ok(doc.getNumberOfPages()>=1);
});

test('PDF currency reflects the saved invoice, not the bakery current currency',()=>{
 const {invoicePdfAmount}=mod.exports;
 assert.equal(invoicePdfAmount(4.99,{}),'R 4.99');
 assert.equal(invoicePdfAmount(4.99,{currency:'GBP'}),'GBP 4.99');
 assert.equal(invoicePdfAmount(0.59,{currency:'USD'}),'USD 0.59');
 assert.throws(()=>invoicePdfAmount(4.99,{currency:null}));
 assert.ok(generateInvoicePdf({currency:'USD',total:4.99},business).getNumberOfPages()>0);
});
