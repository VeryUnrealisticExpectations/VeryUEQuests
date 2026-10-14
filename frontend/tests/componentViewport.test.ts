import test from 'node:test'
import assert from 'node:assert/strict'
import type { ComponentTextContext } from '../src/types.ts'
import { COMPONENT_MAX_HEIGHT, componentPreviewHeight, componentPreviewMode, fittedComponentHeight, resizedComponentHeight } from '../src/lib/componentViewport.ts'

const context: ComponentTextContext = { assetRevision: 'one', source: 'local-client', language: 'en_us', rightToLeft: false, fonts: [], translations: [], keybinds: [] }

test('native text previews never select approximate fonts while resources load or text changes', () => {
  assert.equal(componentPreviewMode({ ...context, pending: true }), 'pending')
  assert.equal(componentPreviewMode(context), 'native')
  assert.equal(componentPreviewMode({ ...context, source: 'server-assets' }), 'approximate')
  assert.equal(componentPreviewMode(context, true), 'approximate')
  assert.equal(componentPreviewMode(context, false), 'native')
})

test('pointer resizing follows pixels continuously in both directions and respects preview bounds', () => {
  const heights = Array.from({ length: 54 }, (_, delta) => resizedComponentHeight(96, delta))
  assert.deepEqual(heights, Array.from({ length: 54 }, (_, delta) => 96 + delta))
  assert.equal(resizedComponentHeight(132, -28), 104)
  assert.equal(resizedComponentHeight(96, -1000), 40)
  assert.equal(resizedComponentHeight(96, 10000), COMPONENT_MAX_HEIGHT)
})

test('preview buffering covers gradual resizing with bounded render requests', () => {
  const heights = Array.from({ length: 300 }, (_, index) => 1 + index)
  for (const height of heights) assert.ok(componentPreviewHeight(height) >= height)
  assert.equal(new Set(heights.map(componentPreviewHeight)).size, 3)
  assert.equal(componentPreviewHeight(1024), 1024)
})

test('fit text includes native glyph gutters and borders without exceeding bounded rendering dimensions', () => {
  assert.equal(fittedComponentHeight(12), 40)
  assert.equal(fittedComponentHeight(183), 370)
  assert.equal(fittedComponentHeight(10000), COMPONENT_MAX_HEIGHT)
  assert.equal((COMPONENT_MAX_HEIGHT - 4) / 2, 1024)
})
