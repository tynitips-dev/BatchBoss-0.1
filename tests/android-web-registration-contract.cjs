"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
const read = relative => fs.readFileSync(path.join(root, relative), "utf8");

test("Android registration opens the working website signup with an Android source marker", () => {
  const android = read("app/src/main/java/com/example/ui/screens/WebsiteRegistrationScreen.kt");
  const platform = read("web/src/accountPlatform.ts");
  assert.match(android, /https:\/\/app\.batchboss\.co\.za\/\?signup=1&from=android/);
  assert.match(platform, /get\('from'\) === 'android'/);
});

test("Web registration creates one Firebase account with a bakery and matching UID profile", () => {
  const web = read("web/src/App.tsx");
  assert.match(web, /createUserWithEmailAndPassword\(auth, email, password\)/);
  assert.match(web, /const uid = credential\.user\.uid/);
  assert.match(web, /batch\.set\(bakeryRef,/);
  assert.match(web, /batch\.set\(doc\(db, 'users', uid\),/);
  assert.match(web, /bakeryId: bakeryRef\.id/);
  assert.match(web, /registrationPlatform: signupPlatform\(location\.search\)/);
  assert.match(web, /await batch\.commit\(\)/);
});

test("Android logs into the same Firebase UID and retrieves its existing bakery ID", () => {
  const firebase = read("app/src/main/java/com/example/data/remote/FirebaseService.kt");
  assert.match(firebase, /signInWithEmailAndPassword\(cleanEmail, cleanPass\)/);
  assert.match(firebase, /collection\("users"\)\.document\(user\.uid\)/);
  assert.match(firebase, /userDoc\.getString\("bakeryId"\)/);
  const viewmodel = read("app/src/main/java/com/example/ui/BatchBossViewModel.kt");
  assert.match(viewmodel, /hasVerifiedFirebaseWorkspace\(/);
  assert.match(viewmodel, /legacyBakeryIdConflicts\(/);
  assert.doesNotMatch(viewmodel, /fun createAccountWithDetails\(/);
});
