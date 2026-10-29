import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {subscriptionAccess} from '../web/src/subscriptionAccess.ts'
const cases=JSON.parse(readFileSync(new URL('../app/src/test/resources/bakery-subscription-cases.json',import.meta.url),'utf8'))
for(const row of cases)test(`shared Android/web subscription contract: ${row.name}`,()=>assert.equal(subscriptionAccess(row.status,row.plan,row.source,row.expiry,row.now),row.expected))
test('malformed expiry cannot grant access',()=>assert.equal(subscriptionAccess('active','monthly','admin',NaN,1000),false))
