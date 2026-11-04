import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import vm from 'node:vm'
import { webcrypto } from 'node:crypto'
import { File } from 'node:buffer'
import ts from 'typescript'
import { ref, watch, reactive, effectScope, onScopeDispose } from 'vue'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const cases = []
const timers = new Map()
let timerId = 0
const storage = new Map()
const localStorage = { getItem: (key) => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) }
const clock = {
  setTimeout: (callback, delay) => { const id = ++timerId;timers.set(id, { callback, delay });return id },
  clearTimeout: (id) => timers.delete(id),
}
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
async function fire() { assert.equal(timers.size, 1);const [id, pending] = [...timers][0];timers.delete(id);pending.callback();await flush() }
async function load(relative, imports) {
  const javascript = ts.transpileModule(await readFile(path.join(root, relative), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
  const context = vm.createContext({ console, Date, crypto: webcrypto, FormData, AbortController, localStorage, ...clock })
  const module = new vm.SourceTextModule(javascript, { context, identifier: relative })
  await module.link((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Unexpected module ${specifier}`)
    const values = imports[specifier]
    return new vm.SyntheticModule(Object.keys(values), function () {
      for (const [name, value] of Object.entries(values)) this.setExport(name, value)
    }, { context })
  })
  await module.evaluate();return module.namespace
}
const a = '11111111-1111-4111-8111-111111111111'
const b = '22222222-2222-4222-8222-222222222222'
function task(id, status, completed = 0, marker = '') {
  return { task_id: id, status, image_count: 2, completed_images: completed, progress_percent: completed * 50,
    attempts: 1, max_attempts: 3, model: { id: 'model', name: 'V2', version: '1' }, requested_device: 'cpu', actual_device: 'cpu',
    fallback_reason: null, error_code: null, result: status === 'succeeded' ? { marker, results: [] } : null }
}
const response = (value) => ({ data: { success: true, data: value } })
const auth = reactive({ user: { id: 'owner-a' }, userRole: 'inspector' })
storage.set('ssdd:last-task:owner-a', a)
let replies = []
let queries = 0
let invalidations = 0
let held = null
let hold = false
let submitting = null
const stateModule = await load('src/composables/useAsyncDetection.ts', {
  vue: { ref, watch, onScopeDispose },
  '@/stores/auth': { useAuthStore: () => auth },
  '@/composables/useHomeData': { invalidateHomeData: () => invalidations++ },
  '@/api/tasks': {
    getTask: (_id, signal) => {
      queries++
      if (hold) return new Promise((resolve) => { held = { resolve, signal } })
      const value = replies.shift();assert.ok(value, 'A controlled reply must be present')
      return value instanceof Error ? Promise.reject(value) : Promise.resolve(response(value))
    },
    submitTask: (_files, options) => new Promise((resolve) => { submitting = { resolve, options } }),
  },
})
const scope = effectScope()
const state = scope.run(() => stateModule.useAsyncDetection())
assert.equal(state.taskId.value, a)
replies = [task(a, 'queued'), task(a, 'running', 1), task(a, 'succeeded', 2, 'fresh-a')]
await state.load();assert.equal(state.polling.value, true);assert.equal(state.task.value.status, 'queued')
await fire();assert.equal(state.task.value.completed_images, 1)
await fire();assert.equal(state.task.value.status, 'succeeded');assert.equal(timers.size, 0);assert.equal(invalidations, 1)
cases.push('sequential queued/running/succeeded polling and one result invalidation')

replies = [task(a, 'queued')];await state.load(a);state.stop();assert.equal(timers.size, 0);assert.equal(state.task.value.status, 'queued');assert.equal(state.polling.value, false)
cases.push('pause stops polling without claiming server task cancellation')

hold = true;const oldRequest = state.load(a);assert.ok(held)
storage.set('ssdd:last-task:owner-b', b);auth.user = { id: 'owner-b' }
assert.equal(held.signal.aborted, true);assert.equal(state.task.value, null);assert.equal(state.taskId.value, b)
held.resolve(response(task(a, 'succeeded', 2, 'private-old-owner')));await oldRequest;hold = false
assert.equal(state.task.value, null);assert.equal(invalidations, 1)
assert.equal(storage.get('ssdd:last-task:owner-b'), b)
cases.push('account switch clears state and rejects late previous-owner response')

replies = [new Error('offline-1'), new Error('offline-2'), new Error('offline-3')]
const before = queries;await state.load(b);await fire();await fire()
assert.equal(queries - before, 3);assert.equal(state.polling.value, false);assert.equal(timers.size, 0);assert.ok(state.error.value.includes('恢复查询'))
cases.push('network polling failures stop after three tries while retaining recovery id')
const malformedBefore = queries;await state.load('../../private');assert.equal(queries, malformedBefore);assert.ok(state.error.value)
cases.push('malformed task identifier never issues a request')

const submission = state.submit([new File(['a'], 'a.png')]);submitting.options.onProgress(80)
assert.equal(state.uploadProgress.value, 80);assert.equal(state.task.value, null)
replies = [task(b, 'running', 1)];submitting.resolve(response(task(b, 'queued')));await submission;await flush()
assert.equal(state.uploadProgress.value, null);assert.equal(state.task.value.completed_images, 1);assert.equal(state.task.value.progress_percent, 50)
state.stop();replies = [task(b, 'succeeded', 2, 'new-signed-urls')];await state.load(b)
assert.equal(state.task.value.result.marker, 'new-signed-urls');assert.equal(invalidations, 2)
replies = [task(b, 'succeeded', 2, 'refreshed-again')];await state.load(b);assert.equal(invalidations, 2)
cases.push('upload and processing progress stay separate and recovery refreshes terminal results')

replies = [task(b, 'running', 1)];await state.load(b);scope.stop();assert.equal(timers.size, 0)
cases.push('component scope disposal releases polling timers')

const submitted = []
let failNext = true
const api = await load('src/api/tasks.ts', {
  './index': { default: { post: (url, body, options) => {
    submitted.push({ url, key: options.headers['Idempotency-Key'], names: body.getAll('images').map((file) => file.name) })
    assert.equal(options.timeout, 60_000)
    if (failNext) { failNext = false;return Promise.reject(new Error('lost acknowledgement')) }
    return Promise.resolve(response(task(b, 'queued')))
  } } },
})
const first = new File(['a'], 'a.png'), second = new File(['b'], 'b.png')
await assert.rejects(api.submitTask([first, second]));await api.submitTask([first, second]);await api.submitTask([first, second])
assert.equal(submitted[0].key, submitted[1].key);assert.notEqual(submitted[1].key, submitted[2].key)
assert.equal(submitted[0].url, '/api/detect/tasks');assert.deepEqual(submitted[1].names, ['a.png', 'b.png'])
cases.push('lost asynchronous submission acknowledgement reuses key until accepted')

const destination = path.resolve(root, '../SSDD-backend/target/p55')
await mkdir(destination, { recursive: true })
await writeFile(path.join(destination, 'frontend-state.json'), JSON.stringify({ status: 'passed', tests: cases.length, cases, browser_e2e: false }, null, 2) + '\n')
console.log(JSON.stringify({ status: 'passed', frontend_state_tests: cases.length }))
