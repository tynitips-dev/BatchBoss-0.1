import assert from 'node:assert/strict'
import test from 'node:test'
import {priceRecipe,sampleRecipeCosts} from '../web/src/recipe-pricing.ts'
const near=(actual,expected)=>assert.ok(actual!==null&&Math.abs(actual-expected)<0.000001,`Expected ${expected}, got ${actual}`)
test('three worked recipe examples correctly sum all five cost components',()=>{
 const expected=[{total:176,unit:176/12,selling:176/12/0.6},{total:325,unit:325,selling:500},{total:210,unit:10.5,selling:15}]
 for(let i=0;i<sampleRecipeCosts.length;i++){
  const actual=priceRecipe(sampleRecipeCosts[i]),want=expected[i]
  near(actual.total,want.total);near(actual.perUnit,want.unit);near(actual.perServing,want.unit/sampleRecipeCosts[i].servingsPerUnit);near(actual.sellingUnit,want.selling)
  near(actual.sellingBatch, want.selling*sampleRecipeCosts[i].yieldCount)
 }
})
test('invalid batch size and margin never result in misleading price',()=>{
 assert.equal(priceRecipe({...sampleRecipeCosts[0],yieldCount:0}).sellingUnit,null)
 assert.equal(priceRecipe({...sampleRecipeCosts[0],servingsPerUnit:0}).perServing,null)
 assert.equal(priceRecipe({...sampleRecipeCosts[0],marginPercent:100}).sellingBatch,null)
 assert.equal(priceRecipe({...sampleRecipeCosts[0],marginPercent:-1}).sellingBatch,null)
})
