import { useState } from 'react'
import type { RegistryEntry } from '../types.ts'
import { filterRegistry, isRegistryId } from '../lib/registryPicker.ts'

export function ResourceIdPicker({ kind, entries, value, onChoose, validId = isRegistryId }:
  { kind: string; entries: RegistryEntry[]; value: string; onChoose: (id: string) => void; validId?: (id: string) => boolean }) {
  const [query, setQuery] = useState('')
  const raw = query.trim()
  const matches = filterRegistry(entries, raw)
  const search = `Search ${kind} or enter an ID`
  return <>
    <label className="field"><span className="sr-only">{search}</span>
      <input type="search" data-panel-focus placeholder={search} value={query} spellCheck={false}
        onChange={event => setQuery(event.target.value)}
        onKeyDown={event => {
          if (event.key !== 'Enter') return
          event.preventDefault()
          if (matches.length === 1) onChoose(matches[0].id)
          else if (validId(raw)) onChoose(raw)
        }} />
    </label>
    <div className="wizard-search-results">
      {matches.map(({ id, name }) => <button key={id} type="button" className="mc-button wizard-result-button"
        aria-pressed={id === value} onClick={() => onChoose(id)}>
        {name === id ? id : `${name} (${id})`}
      </button>)}
      {!matches.length && <p className="muted">No matching {kind}.</p>}
    </div>
    {validId(raw) && <button type="button" className="mc-button" onClick={() => onChoose(raw)}>Use {raw}</button>}
  </>
}
