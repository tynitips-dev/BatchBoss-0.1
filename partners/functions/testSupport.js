"use strict";
const { createRequire } = require("node:module");
const vm = require("node:vm");
const fs = require("node:fs");
function memoryDb() {
  let records = new Map(), queue = Promise.resolve();
  const snap = value => ({ exists:value !== undefined, get:k=>value?.[k], data:()=>value });
  const db = {
    collection: name => ({ doc: id => {
      const key = name + "/" + id;
      return { key, id, get:async()=>snap(records.get(key)), create:async v=>{
        if(records.has(key)) throw new Error("exists"); records.set(key,structuredClone(v));
      }, update:async v=>records.set(key,{...records.get(key),...v}) };
    }}),
    runTransaction: fn => {
      const run = queue.then(async()=>{
        const next = structuredClone(records);
        let writing = false;
        const t = {
          get:async ref=>{if(writing) throw new Error("Read after write"); return snap(next.get(ref.key));},
          create:(ref,v)=>{writing=true;if(next.has(ref.key))throw new Error("exists");next.set(ref.key,structuredClone(v));},
          update:(ref,v)=>{writing=true;if(!next.has(ref.key))throw new Error("missing");next.set(ref.key,{...next.get(ref.key),...v});}
        };
        const result = await fn(t); records = next; return result;
      }); queue = run.catch(()=>{}); return run;
    },
    entries:()=>[...records.entries()],
    seed:(key,value)=>records.set(key,structuredClone(value))
  }; return db;
}
function loadFunctions(file,db,env={},fetch=async()=>{throw new Error("Network forbidden in tests");}) {
  const nativeRequire = createRequire(file);
  const module = {exports:{}};
  class HttpsError extends Error {constructor(code,message){super(message);this.code=code;}}
  const requireMock = name => {
    if(name==="firebase-functions/v2/https")return {
      HttpsError, onCall:(...args)=>args.at(-1), onRequest:(...args)=>args.at(-1)
    };
    if(name==="firebase-admin/app")return {initializeApp:()=>{},getApps:()=>[]};
    if(name==="firebase-admin/firestore")return {getFirestore:()=>db,FieldValue:{serverTimestamp:()=>123}};
    if(name.startsWith("./") && !name.endsWith(".json")) {
      const target=nativeRequire.resolve(name);
      // All local modules use the same restricted test runtime.
      return loadFunctions(target,db,env,fetch);
    }
    return nativeRequire(name);
  };
  vm.runInNewContext(fs.readFileSync(file,"utf8"),{
    require:requireMock,module,exports:module.exports,process:{env},fetch,URL,AbortSignal,Buffer,console
  },{filename:file});
  return module.exports;
}
module.exports={memoryDb,loadFunctions};
