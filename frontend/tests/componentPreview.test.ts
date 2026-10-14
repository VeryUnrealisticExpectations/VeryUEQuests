import test from 'node:test'
import assert from 'node:assert/strict'
import { decodeComponentPreview, paintComponentPreview } from '../src/lib/componentPreview.ts'

test('preview decoding is shared across caret and selection updates', async () => {
  const original = globalThis.Image
  const finish: (() => void)[] = []
  let constructed = 0
  class PreviewImage {
    src = ''
    complete = false
    naturalWidth = 24
    naturalHeight = 18
    constructor() { constructed++ }
    decode(): Promise<void> { return new Promise(resolve => finish.push(() => { this.complete = true; resolve() })) }
  }
  globalThis.Image = PreviewImage as unknown as typeof Image
  try {
    const first = decodeComponentPreview('fixture-decoding-cache')
    const second = decodeComponentPreview('fixture-decoding-cache')
    assert.equal(first, second)
    assert.equal(constructed, 1)
    finish[0]()
    const image = await first
    assert.equal(await second, image)
    assert.equal(await decodeComponentPreview('fixture-decoding-cache'), image)
    assert.equal(constructed, 1)
  } finally { globalThis.Image = original }
})

test('pending preview images do not resize, clear, or erase the displayed frame', () => {
  const events: string[] = []
  const target = {
    get width() { return 40 }, set width(_value: number) { events.push('width') },
    get height() { return 20 }, set height(_value: number) { events.push('height') },
    getContext() { events.push('context'); return null },
  } as unknown as HTMLCanvasElement
  const pending = { complete: false, naturalWidth: 0 } as HTMLImageElement
  assert.equal(paintComponentPreview(target, pending, 30, 16, 2, 12, 9), false)
  assert.deepEqual(events, [])
})

test('caret repaints keep canvas dimensions and draw decoded text in every frame', () => {
  const events: string[] = []
  const graphics = { imageSmoothingEnabled: true, setTransform() { events.push('transform') }, clearRect() { events.push('clear') },
    drawImage() { events.push('text') } }
  let width = 40, height = 20
  const target = {
    get width() { return width }, set width(value: number) { events.push('width'); width = value },
    get height() { return height }, set height(value: number) { events.push('height'); height = value },
    getContext() { return graphics },
  } as unknown as HTMLCanvasElement
  const ready = { complete: true, naturalWidth: 24 } as HTMLImageElement
  assert.equal(paintComponentPreview(target, ready, 20, 10, 2, 12, 9, () => events.push('selection'), () => events.push('caret')), true)
  assert.deepEqual(events, ['transform', 'clear', 'selection', 'text', 'caret'])
  events.length = 0
  assert.equal(paintComponentPreview(target, ready, 20, 10, 2, 12, 9), true)
  assert.deepEqual(events, ['transform', 'clear', 'text'])
})
