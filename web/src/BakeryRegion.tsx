import {createContext,useContext,useEffect,useState,type ReactNode} from 'react'
import {doc,onSnapshot} from 'firebase/firestore'
import {auth,db} from './firebase'
import {signOut} from 'firebase/auth'
import {bakeryAccountingRegion,formatRegionalMoney} from './workspace-utils'
type Region=NonNullable<ReturnType<typeof bakeryAccountingRegion>>
const RegionContext=createContext<Region|null>(null)
export function useBakeryRegion(){
 const region=useContext(RegionContext)
 if(!region)throw new Error('Bakery regional settings are unavailable.')
 return {...region,format:(value:number)=>Number.isFinite(value)?formatRegionalMoney(value,region.currency,region.locale):'Invalid amount'}
}
export default function BakeryRegion({bakeryId,children}:{bakeryId:string;children:ReactNode}){
 const [loaded,setLoaded]=useState<{id:string;region:Region|null}|null>(null)
 useEffect(()=>onSnapshot(doc(db,'bakeries',bakeryId),snap=>{
 const region=snap.exists()?bakeryAccountingRegion(snap.data()):null
 setLoaded({id:bakeryId,region})
 },()=>setLoaded({id:bakeryId,region:null})),[bakeryId])
 if(!loaded||loaded.id!==bakeryId)return <div className="centre-screen"><p>Loading bakery accounting settings…</p></div>
 if(!loaded.region)return <div className="centre-screen"><p>Bakery accounting settings could not be verified. Refresh or contact support before entering financial data.</p><button onClick={()=>location.reload()}>Retry</button><button onClick={()=>signOut(auth)}>Sign out</button></div>
 return <RegionContext.Provider key={bakeryId+':'+loaded.region.currency} value={loaded.region}>{children}</RegionContext.Provider>
}
