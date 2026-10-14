import test from 'node:test'
import assert from 'node:assert/strict'
import { projectComponent } from '../src/lib/components.ts'
import { componentAnnotations } from '../src/lib/componentAnnotations.ts'

test('selected Components expose translation, keybind, and inherited hover annotations without changing the document', () => {
  const value = { text: 'Hello ', hoverEvent: { action: 'show_text', contents: 'Hint' }, extra: [
    { translate: 'item.minecraft.diamond' }, { keybind: 'key.jump' }, { text: '😀 end' },
  ] }
  const before = structuredClone(value)
  const projection = projectComponent(value)
  const annotations = componentAnnotations(projection, { start: 0, end: projection.text.length })
  assert.deepEqual(annotations.map(entry => entry.letter).sort(), ['H', 'K', 'T'])
  assert.equal(annotations.find(entry => entry.kind === 'translate')?.title, 'Translation: item.minecraft.diamond')
  assert.equal(annotations.find(entry => entry.kind === 'keybind')?.title, 'Keybind: key.jump')
  assert.deepEqual(componentAnnotations(projection, { start: 1, end: 4 }).map(entry => entry.letter), ['H'])
  assert.deepEqual(value, before)
})

test('caret annotations identify atomic tokens and plain text selections omit unrelated Components', () => {
  const projection = projectComponent({ text: 'A', extra: [{ translate: 'test.key' }, { keybind: 'key.jump' }, { text: 'B' }] })
  assert.deepEqual(componentAnnotations(projection, { start: 0, end: 1 }), [])
  assert.deepEqual(componentAnnotations(projection, { start: 2, end: 2 }).map(entry => entry.letter), ['T'])
  assert.deepEqual(componentAnnotations(projection, { start: 2, end: 3 }).map(entry => entry.letter), ['K'])
  assert.deepEqual(componentAnnotations(projection, { start: 3, end: 4 }), [])
})
