import test from 'node:test'
import assert from 'node:assert/strict'
import {applianceElectricityCost,allocatedOverhead} from '../web/src/recipe-expenses.ts'
test('electricity cost uses watts, hours, tariff and duty cycle',()=>{
 assert.equal(applianceElectricityCost(2000,1,3.5),7)
 assert.equal(applianceElectricityCost(2000,1,3.5,50),3.5)
 assert.equal(applianceElectricityCost(1000,2,4,25),2)
})
test('overhead calculation allocates monthly costs by productive batch hours',()=>{
 assert.equal(allocatedOverhead(1500,100,2),30)
 assert.equal(allocatedOverhead(1500,0,2),null)
 assert.equal(allocatedOverhead(1500,100,0),0)
})
