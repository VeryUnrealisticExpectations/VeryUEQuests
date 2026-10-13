import assert from 'node:assert/strict'
import test from 'node:test'
import { BoundedCache } from '../src/lib/boundedCache.ts'
import { ThumbnailQueueFullError, ThumbnailScheduler } from '../src/lib/thumbnailScheduler.ts'

test('cache evicts by both bytes and count, preserves recently used entries, and rejects oversize entries', () => {
  const cache = new BoundedCache<string, string>(100, 3)
  cache.set('a', 'A', 40); cache.set('b', 'B', 40); assert.equal(cache.get('a'), 'A')
  cache.set('c', 'C', 40); assert.equal(cache.get('b'), undefined)
  for (let i = 0; i < 10000; i++) cache.set(String(i), 'small', 1)
  assert.equal(cache.size, 3); assert.equal(cache.bytes, 3)
  cache.set('large', 'large', 101); assert.equal(cache.get('large'), undefined)
  cache.set('9999', 'updated', 60); assert.ok(cache.bytes <= 100)
  cache.clear(); assert.equal(cache.bytes, 0); assert.equal(cache.size, 0)
})

test('rapidly cancelled autocomplete requests never enter the renderer and release their queue entries', async () => {
  const scheduler = new ThumbnailScheduler<string>()
  let renders = 0
  const requests: Promise<string>[] = []
  for (let i = 0; i < 1000; i++) {
    const controller = new AbortController()
    requests.push(scheduler.request(String(i), async () => { renders++; return 'icon' }, controller.signal))
    controller.abort()
  }
  const results = await Promise.allSettled(requests)
  assert.equal(renders, 0); assert.equal(scheduler.size, 0)
  assert.ok(results.every(result => result.status === 'rejected' && result.reason.name === 'AbortError'))
})

test('duplicate icons share a render; cancelling one consumer preserves the remaining consumer', async () => {
  const scheduler = new ThumbnailScheduler<string>()
  const first = new AbortController(), second = new AbortController()
  let renders = 0
  const render = async () => { renders++; return 'png' }
  const one = scheduler.request('same', render, first.signal), two = scheduler.request('same', render, second.signal)
  first.abort()
  await assert.rejects(one, { name: 'AbortError' }); assert.equal(await two, 'png')
  assert.equal(renders, 1); assert.equal(scheduler.size, 0)
})

test('render work is serialized and clearing a session cancels waiting jobs', async () => {
  const scheduler = new ThumbnailScheduler<number>()
  let active = 0, peak = 0
  const jobs = Array.from({ length: 50 }, (_, i) => scheduler.request(String(i), async () => {
    active++; peak = Math.max(peak, active); await Promise.resolve(); active--; return i
  }, new AbortController().signal))
  assert.equal((await Promise.all(jobs)).length, 50); assert.equal(peak, 1)
  const pending = scheduler.request('cancel', async () => 1, new AbortController().signal)
  scheduler.clear(); await assert.rejects(pending, { name: 'AbortError' }); assert.equal(scheduler.size, 0)
})

test('the pending icon queue has a fixed limit and can accept work after cancelled jobs are released', async () => {
  const scheduler = new ThumbnailScheduler<number>()
  const pending = Array.from({ length: 256 }, (_, i) => scheduler.request(String(i), async () => i, new AbortController().signal))
  const results = Promise.allSettled(pending)
  await assert.rejects(scheduler.request('overflow', async () => 0, new AbortController().signal), ThumbnailQueueFullError)
  scheduler.clear(); await results
  assert.equal(await scheduler.request('new-session', async () => 42, new AbortController().signal), 42)
})

test('cache disposal waits for an active render after its consumers have been cancelled', async () => {
  const scheduler = new ThumbnailScheduler<string>()
  let finish!: () => void
  const job = scheduler.request('old-session', async () => {
    await new Promise<void>(resolve => { finish = resolve })
    return 'icon'
  }, new AbortController().signal)
  await Promise.resolve()
  scheduler.clear()
  await assert.rejects(job, { name: 'AbortError' })
  let disposed = false
  const disposal = scheduler.idle().then(() => { disposed = true })
  await Promise.resolve()
  assert.equal(disposed, false)
  finish()
  await disposal
  assert.equal(disposed, true)
})
