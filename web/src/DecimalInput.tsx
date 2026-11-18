import { useEffect, useRef, useState, type InputHTMLAttributes } from 'react'
import { normalizeDecimalDraft, isDecimalDraft } from './workspace-utils'

/** Keep decimal editing as text so typing 0.59 or 4.99 works in every browser locale.
 * Native numeric constraints are checked using a separate number input.
 */
export default function DecimalInput({type: _type, value, defaultValue, min, max, step, onChange, onBlur, onFocus, ...props}:InputHTMLAttributes<HTMLInputElement>) {
 void _type
 const initial = String(value ?? defaultValue ?? '')
 const [draft,setDraft]=useState(initial)
 const input=useRef<HTMLInputElement>(null)
 const dirty=useRef(false)
 function validate(node:HTMLInputElement,raw:string) {
  node.setCustomValidity('')
  if(raw==='')return
  if(!isDecimalDraft(raw)||!Number.isFinite(Number(raw))){
   node.setCustomValidity('Enter a number with a decimal point, for example 4.99.');return
  }
  const check=document.createElement('input')
  check.type='number'
  if(min!==undefined)check.min=String(min)
  if(max!==undefined)check.max=String(max)
  if(step!==undefined)check.step=String(step)
  if(defaultValue!==undefined)check.defaultValue=String(defaultValue)
  check.value=String(Number(raw))
  if(!check.validity.valid)node.setCustomValidity(check.validationMessage||'Enter a number within the allowed range.')
 }
 useEffect(()=>{
  if(value!==undefined){
   setDraft(current=>Number(current)===Number(value)&&isDecimalDraft(current)||current===''&&Number(value)===0?current:String(value))
  } else if(!dirty.current) setDraft(String(defaultValue??''))
 },[value,defaultValue])
 useEffect(()=>{if(input.current)validate(input.current,draft)},[draft,min,max,step,defaultValue])
 return <input {...props} ref={input} type="text" inputMode="decimal" lang="en-US" value={draft}
  onFocus={event=>{if(event.currentTarget.value==='0')event.currentTarget.select();onFocus?.(event)}}
  onChange={event=>{
   dirty.current=true
   const raw=normalizeDecimalDraft(event.currentTarget.value)
   event.currentTarget.value=raw
   setDraft(raw)
   validate(event.currentTarget,raw)
   if(raw===''||isDecimalDraft(raw)&&Number.isFinite(Number(raw)))onChange?.(event)
  }}
  onBlur={event=>{
   const raw=draft.endsWith('.')&&isDecimalDraft(draft)?draft.slice(0,-1):draft
   if(raw!==draft){setDraft(raw);event.currentTarget.value=raw}
   validate(event.currentTarget,raw)
   onBlur?.(event)
  }}/>
}
