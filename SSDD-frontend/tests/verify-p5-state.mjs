// Exercise the real TS modules with Vue reactivity and controlled network responses.
// No browser or test framework install is required; imports use existing dependencies.
import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import vm from 'node:vm'
import { webcrypto } from 'node:crypto'
import { File } from 'node:buffer'
import ts from 'typescript'
import { ref, watch, reactive, effectScope, nextTick } from 'vue'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const cases = []
const pass = (name) => cases.push(name)

async function load(relative, imports) {
  const source = await readFile(path.join(root, relative), 'utf8')
  const javascript = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
  const context = vm.createContext({ console, Date, crypto: webcrypto, FormData })
  const module = new vm.SourceTextModule(javascript, { context, identifier: relative })
  await module.link((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Unexpected module dependency: ${specifier}`)
    const values = imports[specifier]
    return new vm.SyntheticModule(Object.keys(values), function () {
      for (const [key, value] of Object.entries(values)) this.setExport(key, value)
    }, { context })
  })
  await module.evaluate()
  return module.namespace
}

const auth = reactive({ user: { id: 'admin-a' }, userRole: 'admin', isAdmin: true })
let networkCalls = 0
let heldActor = null
const deferred = []
const reply = (id) => ({ data: { success: true, data: { items: [{ id: `private:${id}` }], total: 1 } } })
const homeModule = await load('src/composables/useHomeData.ts', {
  vue: { ref, watch },
  '@/stores/auth': { useAuthStore: () => auth },
  '@/api/detect': { getRecords: () => {
    networkCalls++
    const id = auth.user.id
    if (id === heldActor) return new Promise((resolve) => deferred.push({ id, resolve }))
    return Promise.resolve(reply(id))
  } },
  '@/api/admin': { getAdminStats: () => Promise.resolve({ data: { success: true, data: { scope: auth.user.id } } }) },
  '@/api/models': { getModels: () => Promise.resolve({ data: { success: true, data: [] } }) },
})
const scope = effectScope()
const home = scope.run(() => homeModule.useHomeData())
try {
  await home.refresh()
  const firstCalls = networkCalls
  await home.refresh()
  assert.equal(networkCalls, firstCalls)
  assert.equal(home.adminStats.value.scope, 'admin-a')
  homeModule.invalidateHomeData()
  await home.refresh()
  assert.ok(networkCalls > firstCalls)
  pass('same scope caches; committed mutation invalidation refetches')

  auth.user = { id: 'inspector-b' };auth.userRole = 'inspector';auth.isAdmin = false
  await nextTick()
  assert.equal(home.chartRecords.value.length, 0)
  assert.equal(home.adminStats.value, null)
  await home.refresh()
  assert.deepEqual(Array.from(home.chartRecords.value, (record) => record.id), ['private:inspector-b'])
  assert.equal(home.adminStats.value, null)
  pass('account and role changes clear private records and administrator summary')

  auth.user = { id: 'admin-old' };auth.userRole = 'admin';auth.isAdmin = true
  await nextTick();heldActor = 'admin-old'
  const oldRequest = home.refresh(true)
  assert.equal(deferred.length, 2)
  auth.user = { id: 'inspector-new' };auth.userRole = 'inspector';auth.isAdmin = false
  await nextTick();heldActor = null
  await home.refresh(true)
  for (const pending of deferred.splice(0)) pending.resolve(reply(pending.id))
  await oldRequest
  assert.deepEqual(Array.from(home.chartRecords.value, (record) => record.id), ['private:inspector-new'])
  assert.equal(home.adminStats.value, null)
  assert.equal(home.loading.value, false)
  pass('late previous-account responses cannot republish private data')
} finally {
  scope.stop()
}

const submissions = []
let failNext = true
const apiModule = await load('src/api/detect.ts', {
  './index': { default: { post: (_url, body, options) => {
    submissions.push({ key: options.headers['Idempotency-Key'], names: body.getAll('images').map((file) => file.name) })
    if (failNext) { failNext = false;return Promise.reject(new Error('simulated lost response')) }
    return Promise.resolve({ data: { success: true, data: {} } })
  } } },
})
const a = new File(['a'], 'a.png');const b = new File(['b'], 'b.png')
await assert.rejects(apiModule.detectBatch([a, b]))
await apiModule.detectBatch([a, b])
assert.equal(submissions[0].key, submissions[1].key)
assert.deepEqual(submissions[1].names, ['a.png', 'b.png'])
pass('ordered batch retry retains the same idempotency key after lost response')
await apiModule.detectBatch([a, b])
assert.notEqual(submissions[1].key, submissions[2].key)
await apiModule.detectBatch([b, a])
assert.notEqual(submissions[2].key, submissions[3].key)
assert.deepEqual(submissions[3].names, ['b.png', 'a.png'])
pass('confirmed success and changed file order start new attempts')

const destination = path.resolve(root, '../SSDD-backend/target/p5')
await mkdir(destination, { recursive: true })
await writeFile(path.join(destination, 'frontend-state.json'), JSON.stringify({ status: 'passed', cases, tests: cases.length, browser_e2e: false }, null, 2) + '\n')
console.log(JSON.stringify({ status: 'passed', frontend_state_tests: cases.length }))
