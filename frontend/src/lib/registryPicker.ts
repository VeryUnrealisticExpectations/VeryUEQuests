import type { RegistryEntry } from '../types.ts'

export const INVENTORY_SLOT_SIZE = 36
export const INVENTORY_SLOT_GAP = 2
export const INVENTORY_ROW_HEIGHT = INVENTORY_SLOT_SIZE + INVENTORY_SLOT_GAP
export const INVENTORY_VISIBLE_ROWS = 6

export function filterRegistry(entries: RegistryEntry[], search: string): RegistryEntry[] {
  const query = search.trim().toLowerCase()
  return query ? entries.filter(entry => entry.id.toLowerCase().includes(query) || entry.name.toLowerCase().includes(query)) : entries
}

export function isRegistryId(value: string): boolean {
  return /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value)
}

export function inventoryColumns(width: number): number {
  return Math.max(1, Math.min(9, Math.floor((width + INVENTORY_SLOT_GAP) / INVENTORY_ROW_HEIGHT)))
}

export function inventoryWindow(count: number, columns: number, scrollTop: number, rowHeight: number,
  visibleRows = INVENTORY_VISIBLE_ROWS, overscan = 1) {
  const rows = Math.ceil(count / columns)
  const first = Math.max(0, Math.min(rows - 1, Math.floor(scrollTop / rowHeight)))
  const startRow = Math.max(0, first - overscan)
  const last = Math.ceil((Math.max(0, scrollTop) + visibleRows * rowHeight) / rowHeight)
  const endRow = Math.min(rows, last + overscan)
  return { startRow, endRow, before: startRow * rowHeight, after: Math.max(0, rows - endRow) * rowHeight }
}

export function moveInventorySelection(index: number, key: string, count: number, columns: number,
  visibleRows = INVENTORY_VISIBLE_ROWS): number {
  const offsets: Record<string, number> = {
    ArrowLeft: -1, ArrowRight: 1, ArrowUp: -columns, ArrowDown: columns,
    PageUp: -columns * visibleRows, PageDown: columns * visibleRows,
  }
  const next = key === 'Home' ? 0 : key === 'End' ? count - 1 : index + (offsets[key] ?? 0)
  return Math.max(0, Math.min(count - 1, next))
}

export function revealInventorySelection(index: number, columns: number, scrollTop: number, rowHeight: number,
  visibleRows = INVENTORY_VISIBLE_ROWS): number {
  const top = Math.floor(index / columns) * rowHeight
  const bottom = top + rowHeight
  return top < scrollTop ? top : bottom > scrollTop + visibleRows * rowHeight ? bottom - visibleRows * rowHeight : scrollTop
}
