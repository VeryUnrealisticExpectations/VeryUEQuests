import test from 'node:test'
import assert from 'node:assert/strict'
import type { ChatComponent, ComponentTextContext } from '../src/types.ts'
import { ComponentHistory, componentInputChange, hasComponentContent, plainComponentText, projectComponent, replaceComponentRange, selectedComponent, styleComponentRange } from '../src/lib/components.ts'

const context: ComponentTextContext = { assetRevision: 'one', source: 'local-client', language: 'en_us', rightToLeft: false, fonts: [],
  translations: [{ key: 'test.key', value: '%2$s %% %s' }], keybinds: [{ key: 'key.jump', label: 'Jump', resolved: 'Space' }] }

test('string, tree, and array projections preserve inherited and explicit false styles', () => {
  const value: ChatComponent = [{ text: 'One', color: 'red', bold: true, extra: [{ text: 'Two', bold: false }] }, { text: 'Three' }]
  const result = projectComponent(value)
  assert.equal(result.text, 'OneTwoThree')
  assert.deepEqual(result.leaves.map(leaf => [leaf.style.color, leaf.style.bold]), [['red', true], ['red', false], ['red', true]])
  assert.deepEqual(replaceComponentRange(value, 2, 2, ''), value)
  assert.equal(hasComponentContent('  '), false)
  assert.equal(hasComponentContent({ keybind: 'key.jump' }), true)
})

test('partial formatting preserves ancestor styles and unselected extra children', () => {
  const value: ChatComponent = { text: 'Hello', color: 'red', bold: true, clickEvent: { action: 'suggest_command', value: '/hi' }, extra: [{ text: ' world' }] }
  const updated = styleComponentRange(value, 1, 4, { color: 'green', bold: false })
  const leaves = projectComponent(updated).leaves.filter(leaf => leaf.text)
  assert.deepEqual(leaves.map(leaf => [leaf.text, leaf.style.color, leaf.style.bold]), [['H', 'red', true], ['ell', 'green', false], ['o', 'red', true], [' world', 'red', true]])
  assert.ok(leaves.every(leaf => JSON.stringify(leaf.style.clickEvent) === JSON.stringify(value.clickEvent)))
  assert.equal(plainComponentText(updated), 'Hello world')
  assert.equal(value.text, 'Hello')
})

test('removing inherited hover only affects the selected text', () => {
  const hoverEvent = { action: 'show_text', contents: { text: 'Hint', color: 'gold' } }
  const updated = styleComponentRange({ text: 'ABC', hoverEvent, extra: [{ text: 'D' }] }, 1, 2, { hoverEvent: null })
  const leaves = projectComponent(updated).leaves.filter(leaf => leaf.text)
  assert.deepEqual(leaves.map(leaf => [leaf.text, leaf.style.hoverEvent]), [['A', hoverEvent], ['B', undefined], ['C', hoverEvent], ['D', hoverEvent]])
})

test('text edits preserve advanced components, translation arguments, and adjacent formatting', () => {
  const advanced = { selector: '@p', separator: { text: ', ', italic: true }, hoverEvent: { action: 'show_text', contents: { text: 'Player' } } }
  const value: ChatComponent = { text: 'aaa', color: 'gold', extra: [advanced, { translate: 'test.key', fallback: 'Fallback', with: ['A', { text: 'B', color: 'green' }] }] }
  const updated = replaceComponentRange(value, 1, 2, 'z')
  assert.equal(projectComponent(updated).text, 'aza\ufffc\ufffc')
  assert.deepEqual((updated as { extra: unknown[] }).extra, value.extra)
  const styled = styleComponentRange(value, 0, 1, { italic: true })
  const token = projectComponent(styled).leaves.find(leaf => leaf.kind === 'atomic')!
  assert.deepEqual(token.own, advanced)
  assert.equal(plainComponentText(value, context), 'aaa@pB % A')
  assert.equal(plainComponentText({ keybind: 'key.jump' }, context), 'Space')
})

test('selection-aware input differences do not move repeated-character edits across style boundaries', () => {
  assert.deepEqual(componentInputChange('aaaa', 'aaaaa', { start: 2, end: 2 }, { start: 3, end: 3 }), { start: 2, end: 2, text: 'a' })
  assert.deepEqual(componentInputChange('aaaa', 'aaa', { start: 2, end: 2 }, { start: 1, end: 1 }), { start: 1, end: 2, text: '' })
  assert.deepEqual(componentInputChange('aaaa', 'aaa', { start: 1, end: 2 }, { start: 1, end: 1 }), { start: 1, end: 2, text: '' })
  const value: ChatComponent = { text: 'aa', color: 'red', extra: [{ text: 'aa', color: 'green' }] }
  const updated = replaceComponentRange(value, 2, 2, 'a')
  assert.deepEqual(projectComponent(updated).leaves.map(leaf => [leaf.text, leaf.style.color]), [['aaa', 'red'], ['aa', 'green']])
})

test('UTF-16 selection supports surrogate characters and deleting atomic tokens', () => {
  const value: ChatComponent = { text: 'A😀B', extra: [{ keybind: 'key.jump' }, { text: 'C' }] }
  assert.equal(projectComponent(value).text.length, 6)
  assert.equal(plainComponentText(replaceComponentRange(value, 1, 3, 'x')), 'AxBkey.jumpC')
  assert.equal(plainComponentText(replaceComponentRange(value, 4, 5, '')), 'A😀BC')
  assert.equal(plainComponentText(selectedComponent(value, 1, 5), context), '😀BSpace')
})

test('range replacements across tree and token boundaries preserve the intended text exactly', () => {
  const fixture: ChatComponent = [{ text: 'ab', color: 'red', extra: [{ text: 'cd', bold: false }, { keybind: 'key.jump' }] },
    { text: 'ef', extra: [{ translate: 'test.key', with: ['A', 'B'] }, { text: 'gh' }] }]
  const plain = projectComponent(fixture).text
  for (let start = 0; start <= plain.length; start++) for (let end = start; end <= plain.length; end++) {
    const updated = replaceComponentRange(fixture, start, end, 'X')
    assert.equal(projectComponent(updated).text, `${plain.slice(0, start)}X${plain.slice(end)}`, `range ${start}:${end}`)
  }
  assert.equal(projectComponent(fixture).text, plain)
})

test('deleting lone atomic components produces valid empty text without empty extra or stale type', () => {
  const fixtures: ChatComponent[] = [{ keybind: 'key.jump' }, { translate: 'test.key', with: ['A', 'B'] }, { selector: '@p' },
    { type: 'keybind', keybind: 'key.jump' }, { type: 'translatable', translate: 'test.key', with: ['A', 'B'] },
    { type: 'score', score: { name: 'Player', objective: 'points' } }]
  for (const fixture of fixtures) {
    const updated = replaceComponentRange(fixture, 0, 1, '')
    assert.deepEqual(updated, { text: '' })
    assert.equal(projectComponent(updated).text, '')
    assert.equal(hasComponentContent(updated), false)
  }
  assert.equal(selectedComponent(fixtures[0], 0, 0), '')
})

test('clearing complex documents removes every event, style, and empty branch before new typing', () => {
  const hoverEvent = { action: 'show_entity', contents: { type: 'minecraft:pig', id: '00000000-0000-4000-8000-000000000001', name: { text: 'Pig', bold: true } } }
  const complex: ChatComponent = { text: 'A😀', color: 'gold', font: 'minecraft:uniform', bold: true, italic: true, underlined: true,
    hoverEvent, clickEvent: { action: 'suggest_command', value: '/quest' }, insertion: 'Old insertion',
    extra: [{ text: '', extra: [{ translate: 'test.key', with: [{ text: 'Argument', color: 'red' }], fallback: 'Fallback' }] }, { keybind: 'key.jump' }] }
  const fixtures: ChatComponent[] = [complex, [complex, { text: 'Tail', obfuscated: true }],
    { text: 'Styled', color: 'red', hoverEvent }, { type: 'translatable', translate: 'test.key', with: ['Old'], color: 'gold', hoverEvent }]
  for (const fixture of fixtures) {
    const cleared = replaceComponentRange(fixture, 0, projectComponent(fixture).text.length, '')
    assert.deepEqual(cleared, { text: '' })
    assert.deepEqual(replaceComponentRange(cleared, 0, 0, 'Fresh'), { text: 'Fresh' })
    let backspaced = fixture
    while (projectComponent(backspaced).text.length) {
      const end = projectComponent(backspaced).text.length
      backspaced = replaceComponentRange(backspaced, end - 1, end, '')
    }
    assert.deepEqual(backspaced, { text: '' }, 'deleting the last character also resets the document')
  }
})

test('deleting a nested complex component prunes its payload and leaves adjacent components unchanged', () => {
  const sibling = { text: 'Right', color: 'green', bold: false, clickEvent: { action: 'open_url', value: 'https://example.com' } }
  const complex = { text: '', font: 'minecraft:uniform', hoverEvent: { action: 'show_item', contents: { id: 'minecraft:diamond', count: 42 } },
    extra: [{ keybind: 'key.jump' }, { translate: 'test.key', with: ['Old argument'] }] }
  const fixture: ChatComponent = { text: 'Left', color: 'gold', extra: [complex, sibling] }
  const deleted = replaceComponentRange(fixture, 4, 6, '')
  assert.deepEqual(deleted, { text: 'Left', color: 'gold', extra: [sibling] })
  assert.deepEqual(fixture.extra, [complex, sibling], 'the original snapshot remains untouched for undo')
})

test('deleting an array style anchor preserves the inherited appearance of surviving siblings', () => {
  const hoverEvent = { action: 'show_text', contents: 'Inherited hint' }
  const fixture: ChatComponent = [{ keybind: 'key.jump', color: 'gold', bold: true, font: 'minecraft:uniform', hoverEvent },
    'Tail', { text: 'End', bold: false, font: 'minecraft:default' }]
  const before = projectComponent(fixture).leaves.slice(1).map(leaf => [leaf.text, leaf.style])
  const deleted = replaceComponentRange(fixture, 0, 1, '')
  const after = projectComponent(deleted).leaves.map(leaf => [leaf.text, leaf.style])
  assert.deepEqual(after, before)
  assert.equal(JSON.stringify(deleted).includes('keybind'), false)
  const parent: ChatComponent = { text: 'X', color: 'red', hoverEvent, extra: [{ text: 'Keep' }] }
  assert.deepEqual(replaceComponentRange(parent, 0, 1, ''), { text: '', color: 'red', hoverEvent, extra: [{ text: 'Keep' }] })
})

test('undo and redo preserve the full complex document and the clean deletion state', () => {
  const original: ChatComponent = { text: 'Text', font: 'minecraft:uniform', hoverEvent: { action: 'show_text', contents: { text: 'Hint', color: 'red' } }, extra: [{ keybind: 'key.jump' }] }
  const end = projectComponent(original).text.length
  const history = new ComponentHistory()
  history.record({ value: original, selection: { start: 0, end } })
  const deleted = { value: replaceComponentRange(original, 0, end, ''), selection: { start: 0, end: 0 } }
  const restored = history.undo(deleted)!
  assert.deepEqual(restored.value, original)
  assert.deepEqual(history.redo(restored), deleted)
})

test('typed token replacements strip the wrapper discriminator and preserve unchanged typed children', () => {
  const fixtures: ChatComponent[] = [{ type: 'keybind', keybind: 'key.jump' }, { type: 'translatable', translate: 'test.key', with: ['A', 'B'] },
    { type: 'score', score: { name: 'Player', objective: 'points' } }]
  for (const fixture of fixtures) {
    const replaced = replaceComponentRange(fixture, 0, 1, 'Replacement')
    assert.equal((replaced as { type?: string }).type, undefined)
    assert.equal(plainComponentText(replaced), 'Replacement')
    const styled = styleComponentRange(fixture, 0, 1, { color: 'red' })
    assert.equal((styled as { type?: string }).type, undefined)
    const atomic = projectComponent(styled).leaves.find(leaf => leaf.kind === 'atomic')!
    assert.deepEqual(atomic.own, { ...fixture as object, color: 'red' })
    const kept = projectComponent(replaceComponentRange(fixture, 0, 0, 'Prefix')).leaves.find(leaf => leaf.kind === 'atomic')!
    assert.deepEqual(kept.own, fixture)
  }
})

test('history combines typing, separates formatting, restores selections, and clears redo on new edits', () => {
  const history = new ComponentHistory()
  history.record({ value: '', selection: { start: 0, end: 0 } }, true, 1000)
  history.record({ value: 'a', selection: { start: 1, end: 1 } }, true, 1100)
  history.record({ value: 'ab', selection: { start: 2, end: 2 } }, false, 1200)
  const previous = history.undo({ value: { text: 'ab', bold: true }, selection: { start: 0, end: 2 } })!
  assert.deepEqual(previous, { value: 'ab', selection: { start: 2, end: 2 } })
  assert.deepEqual(history.undo(previous), { value: '', selection: { start: 0, end: 0 } })
  assert.deepEqual(history.redo({ value: '', selection: { start: 0, end: 0 } }), previous)
  history.record(previous)
  assert.equal(history.redo(previous), undefined)
})
