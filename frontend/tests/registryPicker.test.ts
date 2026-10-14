import assert from 'node:assert/strict'
import test from 'node:test'
import { filterRegistry, inventoryColumns, inventoryWindow, INVENTORY_ROW_HEIGHT, isRegistryId,
  moveInventorySelection, revealInventorySelection } from '../src/lib/registryPicker.ts'

const items = Array.from({ length: 500 }, (_, index) => ({ id: `example:ore_${index}`, name: `Ore ${index}` }))

test('inventory search matches names and registry IDs without truncating or altering registry entries', () => {
  assert.equal(filterRegistry(items, 'ore').length, 500)
  assert.equal(filterRegistry(items, '  EXAMPLE:ORE_  ').length, 500)
  assert.deepEqual(filterRegistry(items, 'Ore 499'), [items[499]])
  assert.strictEqual(filterRegistry(items, ''), items)
  assert.equal(filterRegistry(items, 'ore')[450].id, 'example:ore_450')
  assert.equal(filterRegistry(items, 'diamond').length, 0)
  assert.equal(items[0].id, 'example:ore_0')
})

test('explicit raw registry IDs accept modded paths and reject partial search text', () => {
  assert.equal(isRegistryId('minecraft:cobblestone'), true)
  assert.equal(isRegistryId('mod-name:tools/iron.hammer'), true)
  for (const value of ['', 'Cobblestone', 'minecraft:', ':stone', 'Minecraft:stone', 'minecraft:stone extra', ' minecraft:stone']) {
    assert.equal(isRegistryId(value), false, value)
  }
})

test('inventory rows adjust columns to available space and mount bounded rows for large catalogs', () => {
  assert.equal(inventoryColumns(340), 9)
  assert.equal(inventoryColumns(200), 5)
  assert.equal(inventoryColumns(10), 1)
  assert.equal(inventoryColumns(800), 9)
  const count = 100000
  const window = inventoryWindow(count, 9, 100 * INVENTORY_ROW_HEIGHT, INVENTORY_ROW_HEIGHT)
  assert.equal(window.startRow, 99)
  assert.equal(window.endRow, 107)
  assert.equal(window.before + (window.endRow - window.startRow) * INVENTORY_ROW_HEIGHT + window.after,
    Math.ceil(count / 9) * INVENTORY_ROW_HEIGHT)
  assert.ok((window.endRow - window.startRow) * 9 <= 81)
  const last = inventoryWindow(count, 9, 1000000, INVENTORY_ROW_HEIGHT)
  assert.equal(last.endRow, Math.ceil(count / 9))
  assert.deepEqual(inventoryWindow(0, 9, 0, INVENTORY_ROW_HEIGHT), { startRow: 0, endRow: 0, before: 0, after: 0 })
})

test('spatial and paging navigation reaches all matches and clamps the first and last slots', () => {
  assert.equal(moveInventorySelection(0, 'ArrowUp', 500, 9), 0)
  assert.equal(moveInventorySelection(0, 'ArrowLeft', 500, 9), 0)
  assert.equal(moveInventorySelection(0, 'ArrowDown', 500, 9), 9)
  assert.equal(moveInventorySelection(9, 'ArrowRight', 500, 9), 10)
  assert.equal(moveInventorySelection(10, 'PageDown', 500, 9), 64)
  assert.equal(moveInventorySelection(64, 'PageUp', 500, 9), 10)
  assert.equal(moveInventorySelection(0, 'End', 500, 9), 499)
  assert.equal(moveInventorySelection(499, 'ArrowDown', 500, 9), 499)
  assert.equal(moveInventorySelection(499, 'Home', 500, 9), 0)
  assert.equal(moveInventorySelection(0, 'ArrowDown', 0, 9), 0)
})

test('keyboard navigation scrolls the active slot into the mounted inventory window', () => {
  assert.equal(revealInventorySelection(0, 9, 100, INVENTORY_ROW_HEIGHT), 0)
  assert.equal(revealInventorySelection(10, 9, 0, INVENTORY_ROW_HEIGHT), 0)
  const scrollTop = revealInventorySelection(499, 9, 0, INVENTORY_ROW_HEIGHT)
  const window = inventoryWindow(500, 9, scrollTop, INVENTORY_ROW_HEIGHT)
  assert.ok(window.startRow * 9 <= 499 && window.endRow * 9 > 499)
  assert.equal(scrollTop, (Math.floor(499 / 9) + 1 - 6) * INVENTORY_ROW_HEIGHT)
})
