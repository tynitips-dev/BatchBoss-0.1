import test from 'node:test'
import assert from 'node:assert/strict'
import {recipePhoto} from '../web/src/recipe-photo.ts'
test('Android HTTPS and web data photos render across cards, reader and editor',()=>{
 assert.equal(recipePhoto({photoUri:'https://example.test/cake.jpg'}),'https://example.test/cake.jpg')
 assert.equal(recipePhoto({photoDataUrl:'data:image/jpeg;base64,YQ=='}),'data:image/jpeg;base64,YQ==')
 assert.equal(recipePhoto({photoDataUrl:'',photoUri:'https://example.test/old.jpg'}),'')
 assert.equal(recipePhoto({photoUri:'content://phone/image/1'}),'')
 assert.equal(recipePhoto({photoUri:'file:///phone/image.jpg'}),'')
 assert.equal(recipePhoto({photoUri:'javascript:alert(1)'}),'')
})
