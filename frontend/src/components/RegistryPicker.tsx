import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { Catalog } from '../types.ts'
import { filterRegistry, inventoryColumns, inventoryWindow, INVENTORY_ROW_HEIGHT, INVENTORY_VISIBLE_ROWS,
  isRegistryId, moveInventorySelection, revealInventorySelection } from '../lib/registryPicker.ts'
import { ItemIcon } from './ItemIcon.tsx'

interface RegistryPickerProps {
  label: string
  value: string
  onChange: (id: string) => void
  catalog: Catalog
  epoch: number
  kind?: 'items' | 'entities'
  autoFocus?: boolean
}

export function RegistryPicker({ label, value, onChange, catalog, epoch, kind = 'items', autoFocus = false }: RegistryPickerProps) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const viewport = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const [keyboardBrowsing, setKeyboardBrowsing] = useState(false)
  const [columns, setColumns] = useState(9)
  const [scrollTop, setScrollTop] = useState(0)
  const choices = useMemo(() => filterRegistry(catalog[kind], query), [catalog, kind, query])
  const rowColumns = kind === 'items' ? columns : 1
  const rowHeight = kind === 'items' ? INVENTORY_ROW_HEIGHT : 48
  const highlighted = Math.max(0, Math.min(active, choices.length - 1))
  const window = inventoryWindow(choices.length, rowColumns, scrollTop, rowHeight)
  const activeMounted = highlighted >= window.startRow * rowColumns && highlighted < window.endRow * rowColumns
  const rawId = query.trim()
  const canUseRawId = isRegistryId(rawId)
  const selectedEntry = catalog[kind].find(entry => entry.id === value)
  const selectedIcon = kind === 'entities' ? selectedEntry?.item ?? 'minecraft:egg' : value

  useEffect(() => {
    input.current?.setCustomValidity(isRegistryId(value) ? '' : `Choose ${kind === 'items' ? 'an item' : 'an entity'} or enter a namespaced registry ID.`)
  }, [value, kind])

  useEffect(() => { if (autoFocus) input.current?.focus() }, [autoFocus])

  useEffect(() => {
    const element = viewport.current
    if (!open || !element || kind !== 'items') return
    const observer = new ResizeObserver(entries => {
      const width = entries[0]?.contentRect.width
      if (width !== undefined) setColumns(inventoryColumns(width))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [open, kind])

  const resetScroll = () => {
    setScrollTop(0)
    if (viewport.current) viewport.current.scrollTop = 0
  }
  const show = () => {
    setQuery(''); setActive(0); setKeyboardBrowsing(false); resetScroll(); setOpen(true)
  }
  const close = () => { setOpen(false); setQuery('') }
  const commit = (registryId: string) => {
    onChange(registryId)
    input.current?.focus()
    close()
  }
  const select = (index: number) => { if (choices[index]) commit(choices[index].id) }
  const navigate = (key: string) => {
    if (!open) { show(); return }
    setKeyboardBrowsing(true)
    const next = moveInventorySelection(highlighted, key, choices.length, rowColumns)
    const offset = revealInventorySelection(next, rowColumns, scrollTop, rowHeight)
    setActive(next); setScrollTop(offset)
    if (viewport.current) viewport.current.scrollTop = offset
  }

  return <div className={`registry-field registry-field--${kind}`} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) close()
  }}>
    <label htmlFor={id}>{label}</label>
    <div className="registry-search-row">
      <span className="registry-selected-icon" title={value}><ItemIcon id={selectedIcon || 'minecraft:book'} epoch={epoch} /></span>
      <input ref={input} id={id} role="combobox" aria-autocomplete="list" aria-expanded={open}
        aria-controls={open ? `${id}-options` : undefined} aria-describedby={open ? `${id}-status` : undefined}
        aria-activedescendant={open && choices.length > 0 && activeMounted ? `${id}-option-${highlighted}` : undefined}
        aria-invalid={!isRegistryId(value)} value={open ? query : value} autoComplete="off" spellCheck={false}
        placeholder={`Search ${kind} or enter namespace:${kind === 'items' ? 'item' : 'entity'}`}
        onFocus={show} onClick={() => { if (!open) show() }}
        onChange={event => { setQuery(event.target.value); setOpen(true); setActive(0); setKeyboardBrowsing(false); resetScroll() }}
        onKeyDown={event => {
          const directional = ['ArrowDown', 'ArrowUp', 'PageUp', 'PageDown'].includes(event.key)
          const spatial = ['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) && (keyboardBrowsing || !query || event.altKey)
          if ((directional || spatial) && !event.shiftKey && !event.ctrlKey && !event.metaKey) {
            event.preventDefault(); navigate(event.key)
          } else if (event.key === 'Enter' && open) {
            event.preventDefault()
            if (choices.length) select(highlighted)
            else if (canUseRawId) commit(rawId)
          } else if (event.key === 'Escape' && open) {
            event.preventDefault(); event.stopPropagation(); close()
          }
        }} />
    </div>
    {open && <div className="registry-inventory">
      <div ref={viewport} id={`${id}-options`} className="registry-inventory-scroll" role="listbox"
        aria-label={`${label} ${kind}`} style={{ height: Math.min(INVENTORY_VISIBLE_ROWS, Math.ceil(choices.length / rowColumns)) * rowHeight }}
        onScroll={event => setScrollTop(event.currentTarget.scrollTop)}>
        <div role="presentation" style={{ height: window.before }} />
        {Array.from({ length: window.endRow - window.startRow }, (_, offset) => {
          const row = window.startRow + offset
          return <div key={row} className="registry-inventory-row" role="presentation"
            style={{ height: rowHeight, gridTemplateColumns: kind === 'items' ? `repeat(${rowColumns}, 36px)` : '1fr' }}>
            {choices.slice(row * rowColumns, (row + 1) * rowColumns).map((entry, column) => {
              const index = row * rowColumns + column
              return <button key={entry.id} id={`${id}-option-${index}`} type="button" role="option" tabIndex={-1}
                aria-selected={entry.id === value} aria-setsize={choices.length} aria-posinset={index + 1}
                aria-label={`${entry.name}, ${entry.id}`} title={`${entry.name}\n${entry.id}`}
                className={`mc-button ${kind === 'items' ? 'registry-slot' : 'registry-entity-option'}${highlighted === index ? ' is-active' : ''}${entry.id === value ? ' is-selected' : ''}`}
                onMouseDown={event => event.preventDefault()} onMouseEnter={() => { setActive(index); setKeyboardBrowsing(false) }} onClick={() => select(index)}>
                <ItemIcon id={kind === 'entities' ? entry.item ?? 'minecraft:egg' : entry.id} epoch={epoch} />
                {kind === 'entities' && <span><strong>{entry.name}</strong><small>{entry.id}</small></span>}
              </button>
            })}
          </div>
        })}
        <div role="presentation" style={{ height: window.after }} />
      </div>
      <div id={`${id}-status`} className="registry-status" role="status" aria-live="polite" aria-atomic="true">
        <span>{choices.length ? `${choices.length} matching ${kind}` : `No matching ${kind}.`}</span>
        {choices[highlighted] && <span>{choices[highlighted].name}<small>{choices[highlighted].id}</small></span>}
        <small>Selected: {value || 'none'}</small>
      </div>
      {canUseRawId && <button type="button" className="mc-button mc-button--small registry-raw-id"
        onMouseDown={event => event.preventDefault()} onClick={() => commit(rawId)}>Use {rawId}</button>}
    </div>}
  </div>
}
