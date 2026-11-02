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

// Exercise the real signup/login submit handlers without creating hosted accounts.
function authScreenHarness(search,metadataFailure=false){
 const states=[],writes=[],updates=[],busy=[];let cursor=0;
 const react={useState(initial){const i=cursor++;states[i]=typeof initial==='function'?initial():initial;return [states[i],value=>{states[i]=value;}];},useEffect(){}};
 const module={exports:{}},platformModule={exports:{}};
 const platformSource=fs.readFileSync(path.join(__dirname,'../web/src/accountPlatform.ts'),'utf8');
 vm.runInNewContext(ts.transpileModule(platformSource,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module:platformModule,exports:platformModule.exports,URLSearchParams});
 const fallback=new Proxy(function(){},{get:()=>fallback});
 const requireFake=name=>{
  if(name==='react')return react;
  if(name==='react/jsx-runtime')return {jsx:(type,props)=>({type,props}),jsxs:(type,props)=>({type,props})};
  if(name==='./accountPlatform')return platformModule.exports;
  if(name==='./firebase')return {auth:{},db:{},firebaseConfigured:true};
  if(name==='firebase/auth')return {createUserWithEmailAndPassword:async()=>({user:{uid:'fixture-user'}}),signInWithEmailAndPassword:async()=>({user:{uid:'fixture-user'}})};
  if(name==='firebase/firestore')return {
   doc:(base,...parts)=>({path:parts.length?parts.join('/'):(base.path+'/fixture-bakery')}),
   collection:(_db,...parts)=>({path:parts.join('/')}),serverTimestamp:()=>({serverTime:true}),
   writeBatch:()=>({set:(ref,data)=>writes.push({ref,data}),commit:async()=>{}}),
   updateDoc:async(ref,data)=>{updates.push({ref,data});if(metadataFailure)throw Error('Synthetic metadata denial');}
  };
  return fallback;
 };
 const source=fs.readFileSync(path.join(__dirname,'../web/src/App.tsx'),'utf8')+'\nexport { AuthScreen as __AuthScreen };';
 const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
 class TestFormData{constructor(values){this.values=values}get(key){return this.values[key]??null}}
 vm.runInNewContext(code,{require:requireFake,module,exports:module.exports,console,Intl,Date,URLSearchParams,location:{search},FormData:TestFormData});
 const tree=module.exports.__AuthScreen({onBusyChange:value=>busy.push(value)});
 function find(node){if(!node||typeof node!=='object')return null;if(node.type==='form')return node;const children=node.props?.children;for(const child of Array.isArray(children)?children:[children]){const result=find(child);if(result)return result}return null}
 const form=find(tree);assert.ok(form);
 return {writes,updates,busy,states,submit:()=>form.props.onSubmit({preventDefault(){},currentTarget:{email:'baker@example.test',password:'synthetic-password',firstName:'Test',surname:'Baker',bakeryName:'Fixture Bakery'}})};
}
test('Android registration link creates a shared Firebase profile with its original route recorded',async()=>{
 const h=authScreenHarness('?signup=1&from=android');await h.submit();
 const profile=h.writes.find(write=>write.ref.path==='users/fixture-user');assert.ok(profile);
 assert.equal(profile.data.registrationPlatform,'android');assert.equal(profile.data.lastLoginPlatform,'web');
 assert.equal(profile.data.subscriptionPlan,'free');assert.equal(profile.data.role,'owner');
 assert.deepEqual(h.busy,[true,false]);
});
test('direct web signup records web; web login never replaces registration history',async()=>{
 const signup=authScreenHarness('?signup=1');await signup.submit();
 assert.equal(signup.writes.find(write=>write.ref.path==='users/fixture-user').data.registrationPlatform,'web');
 const login=authScreenHarness('');await login.submit();
 assert.equal(login.updates.length,1);assert.equal(login.updates[0].data.lastLoginPlatform,'web');
 assert.equal('registrationPlatform' in login.updates[0].data,false);
});
test('platform metadata failure does not report a successful web login as failed',async()=>{
 const h=authScreenHarness('',true);await h.submit();assert.deepEqual(h.busy,[true,false]);assert.equal(h.states[2],'');
});
