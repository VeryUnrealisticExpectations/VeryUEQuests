import { useId, useMemo, useState } from 'react'
import type { Catalog, RegistryKind } from '../types.ts'
import { ItemIcon } from './ItemIcon.tsx'

export function RegistryPicker({ label, value, onChange, catalog, epoch, kind = 'items', allowBlocks = false }:
  { label: string; value: string; onChange: (id: string) => void; catalog: Catalog; epoch: number; kind?: RegistryKind; allowBlocks?: boolean }) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [source, setSource] = useState<RegistryKind>(kind)
  const choices = useMemo(() => {
    const query = value.toLowerCase()
    return catalog[source].filter(entry => entry.id.toLowerCase().includes(query) || entry.name.toLowerCase().includes(query)).slice(0, 30)
  }, [catalog, source, value])
  const select = (index: number) => {
    const entry = choices[index]
    if (!entry || source === 'blocks' && !entry.item) return
    onChange(source === 'blocks' ? entry.item! : entry.id)
    setOpen(false)
  }
  return <div className="registry-field">
    <label htmlFor={id}>{label}</label>
    {allowBlocks && <div className="picker-tabs" aria-label={`${label} registry`}>
      <button type="button" className={source === 'items' ? 'active' : ''} onClick={() => { setSource('items'); setOpen(true) }}>Items</button>
      <button type="button" className={source === 'blocks' ? 'active' : ''} onClick={() => { setSource('blocks'); setOpen(true) }}>Blocks</button>
    </div>}
    <input id={id} role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={`${id}-options`}
      aria-activedescendant={open && choices[active] ? `${id}-${active}` : undefined} value={value} required autoComplete="off"
      placeholder={source === 'entities' ? 'minecraft:zombie' : 'minecraft:cobblestone'}
      onFocus={() => setOpen(true)} onBlur={() => setOpen(false)}
      onChange={event => { onChange(event.target.value); setOpen(true); setActive(0) }}
      onKeyDown={event => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault(); setOpen(true); setActive(index => Math.max(0, Math.min(choices.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1))))
        } else if (event.key === 'Enter' && open) { event.preventDefault(); select(active) }
        else if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); setOpen(false) }
      }} />
    {open && <div id={`${id}-options`} className="registry-options" role="listbox" aria-label={`${label} suggestions`}>
      {choices.map((entry, index) => <button key={entry.id} id={`${id}-${index}`} type="button" role="option"
        aria-selected={active === index} aria-disabled={source === 'blocks' && !entry.item}
        className={active === index ? 'active' : ''} onMouseDown={event => event.preventDefault()} onClick={() => select(index)}>
        <ItemIcon id={source === 'entities' ? entry.item ?? 'minecraft:egg' : entry.id} kind={source === 'blocks' ? 'block' : 'item'} epoch={epoch} />
        <span><strong>{entry.name}</strong><small>{entry.id}{source === 'blocks' && !entry.item ? ' · No item form' : ''}</small></span>
      </button>)}
      {!choices.length && <p className="empty-option">No matching {source}. You can enter a registry ID.</p>}
    </div>}
  </div>
}
