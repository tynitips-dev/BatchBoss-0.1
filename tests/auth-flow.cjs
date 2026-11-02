"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const path=require("node:path"),fs=require("node:fs"),vm=require("node:vm");
const {createRequire}=require("node:module");
const sdk=createRequire(path.join(__dirname,"../functions/package.json"));
const ts=sdk("typescript");
function appHarness(){
 const states=[],effects=[],listeners=[];let stateIndex=0,authListener,stopped=false;
 const react={
  useState(initial){const i=stateIndex++;if(!(i in states))states[i]=typeof initial==="function"?initial():initial;return [states[i],value=>{states[i]=typeof value==="function"?value(states[i]):value;}];},
  useEffect(callback){if(!effects.length)effects.push(callback);}
 };
 const fallback=new Proxy(function(){},{get:()=>fallback});
 const module={exports:{}};
 const source=fs.readFileSync(path.join(__dirname,"../web/src/App.tsx"),"utf8");
 const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
 const requireFake=name=>{
  if(name==="react")return react;
  if(name==="react/jsx-runtime")return {jsx:(type,props)=>({type,props}),jsxs:(type,props)=>({type,props})};
  if(name==="firebase/auth")return {onAuthStateChanged:(_,callback)=>{authListener=callback;return()=>{stopped=true;};}};
  if(name==="firebase/firestore")return {doc:(_db,_collection,uid)=>({uid}),onSnapshot:(ref,success,error)=>{const listener={ref,success,error,stopped:false};listeners.push(listener);return()=>{listener.stopped=true;};}};
  if(name==="./firebase")return {auth:{},db:{},firebaseConfigured:true,referralPilot:false,bakerFunctions:{}};
  return fallback;
 };
 vm.runInNewContext(code,{require:requireFake,module,exports:module.exports,console,Intl,Date,URLSearchParams,location:{search:""}},{filename:"App.tsx"});
 const render=()=>{stateIndex=0;return module.exports.default();};
 render();const cleanup=effects[0]();
 return {states,listeners,render,auth:user=>authListener(user),cleanup,isStopped:()=>stopped};
}
const snapshot=data=>({exists:()=>true,data:()=>data});
test("signup stays mounted through auth loading and a setup failure",()=>{
 const h=appHarness();h.auth(null);
 const form=h.render();
 assert.equal(typeof form.type,"function");
 form.props.onBusyChange(true);
 h.auth({uid:"new-baker"});
 assert.equal(h.render().type,form.type);
 // The callable fails before the profile arrives: keep the same component and its input/error state.
 h.render().props.onBusyChange(false);
 assert.equal(h.render().type,form.type);
 h.listeners[0].success(snapshot({uid:"new-baker",bakeryId:"new-bakery"}));
 assert.notEqual(h.render().type,form.type);
 h.cleanup();
});
test("account changes clear old data and ignore stale profile/error callbacks",()=>{
 const h=appHarness();h.auth({uid:"baker-a"});
 const old=h.listeners[0];old.success(snapshot({uid:"baker-a",bakeryId:"bakery-a"}));
 assert.equal(h.states[1].bakeryId,"bakery-a");
 h.auth({uid:"baker-b"});
 assert.equal(h.states[1],null);
 assert.equal(old.stopped,true);
 old.success(snapshot({uid:"baker-a",bakeryId:"stale-a"}));old.error();
 assert.equal(h.states[1],null);
 assert.equal(h.states[2],true);
 const current=h.listeners[1];current.success(snapshot({uid:"baker-b",bakeryId:"bakery-b"}));
 assert.equal(h.states[1].bakeryId,"bakery-b");
 h.cleanup();current.success(snapshot({uid:"baker-b",bakeryId:"after-unmount"}));
 assert.equal(h.states[1].bakeryId,"bakery-b");
 assert.equal(h.isStopped(),true);
});
