import { useId, useMemo, useRef, useState } from 'react'
import type { ComponentTextContext } from '../types.ts'

const ROW_HEIGHT = 52
const VISIBLE_ROWS = 5

export function TranslationKeyPicker({ value, onChange, textContext }:
  { value: string; onChange: (key: string) => void; textContext?: ComponentTextContext }) {
  const id = useId()
  const list = useRef<HTMLDivElement>(null)
  const [cursor, setCursor] = useState({ query: value, index: 0 })
  const [scrollTop, setScrollTop] = useState(0)
  const matches = useMemo(() => (textContext?.translations ?? []).filter(entry => entry.key.toLowerCase().includes(value.toLowerCase())),
    [value, textContext?.translations])
  const active = Math.min(matches.length - 1, cursor.query === value ? cursor.index : 0)
  const first = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - 1)
  const selected = textContext?.translations.find(entry => entry.key === value)
  const choose = (index: number) => {
    const entry = matches[index]
    if (!entry) return
    onChange(entry.key); setCursor({ query: entry.key, index: 0 }); setScrollTop(0)
    if (list.current) list.current.scrollTop = 0
  }
  return <div className="translation-key-picker">
    <label className="field">Translation key<input required value={value} autoComplete="off" spellCheck={false}
      placeholder="screen.vuequests.title" role="combobox" aria-autocomplete="list" aria-expanded={matches.length > 0}
      aria-controls={`${id}-keys`} aria-activedescendant={active >= first && active < first + VISIBLE_ROWS + 2 ? `${id}-${active}` : undefined}
      onChange={event => { onChange(event.target.value); setCursor({ query: event.target.value, index: 0 }); setScrollTop(0); if (list.current) list.current.scrollTop = 0 }}
      onKeyDown={event => {
        if (event.key === 'Enter' && matches[active]) { event.preventDefault(); event.stopPropagation(); choose(active); return }
        const offset = { ArrowUp: -1, ArrowDown: 1, PageUp: -VISIBLE_ROWS, PageDown: VISIBLE_ROWS }[event.key]
        if (offset === undefined || !matches.length) return
        event.preventDefault(); event.stopPropagation()
        const next = Math.max(0, Math.min(matches.length - 1, active + offset))
        setCursor({ query: value, index: next })
        if (list.current) {
          if (next * ROW_HEIGHT < list.current.scrollTop) list.current.scrollTop = next * ROW_HEIGHT
          else if ((next + 1) * ROW_HEIGHT > list.current.scrollTop + list.current.clientHeight) list.current.scrollTop = (next + 1) * ROW_HEIGHT - list.current.clientHeight
        }
      }} /></label>
    <div className="translation-pair-heading"><span>Key</span><span>Value · {textContext?.language ?? 'en_us'}</span></div>
    <div ref={list} id={`${id}-keys`} className="translation-key-results" role="listbox" aria-label="Translation keys"
      style={{ height: Math.min(VISIBLE_ROWS, Math.max(1, matches.length)) * ROW_HEIGHT }} onScroll={event => setScrollTop(event.currentTarget.scrollTop)}>
      <div style={{ height: matches.length * ROW_HEIGHT, position: 'relative' }}>
        {matches.slice(first, first + VISIBLE_ROWS + 2).map((entry, offset) => <button key={entry.key} type="button" id={`${id}-${first + offset}`}
          className="mc-button translation-key-option" role="option" aria-selected={first + offset === active}
          style={{ top: (first + offset) * ROW_HEIGHT, height: ROW_HEIGHT }} onMouseDown={event => event.preventDefault()}
          onClick={() => choose(first + offset)} title={`${entry.key}\n${entry.value}`}><code>{entry.key}</code><span>{entry.value}</span></button>)}
      </div>
      {!matches.length && <p className="translation-key-empty">No loaded key matches. You can use this custom key with fallback text.</p>}
    </div>
    <div className="translation-selected-value"><small>{textContext?.language ?? 'en_us'} value</small><span>{selected?.value ?? 'Key is not loaded in this language.'}</span></div>
  </div>
}
