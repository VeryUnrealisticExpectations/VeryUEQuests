import { useState } from 'react'
import { isAdvancementId } from '../lib/taskEditor.ts'

export function AdvancementPicker({ entries, value, onChoose }:
  { entries: string[]; value: string; onChoose: (id: string) => void }) {
  const [query, setQuery] = useState('')
  const raw = query.trim()
  const matches = entries.filter(id => id.toLowerCase().includes(raw.toLowerCase()))
  return <>
    <label className="field"><span className="sr-only">Search advancements or enter an ID</span>
      <input type="search" data-panel-focus placeholder="Search advancements or enter an ID" value={query} spellCheck={false}
        onChange={event => setQuery(event.target.value)}
        onKeyDown={event => {
          if (event.key !== 'Enter') return
          event.preventDefault()
          if (matches.length === 1) onChoose(matches[0])
          else if (isAdvancementId(raw)) onChoose(raw)
        }} />
    </label>
    <div className="wizard-search-results">
      {matches.map(id => <button key={id} type="button" className="mc-button wizard-result-button"
        aria-pressed={id === value} onClick={() => onChoose(id)}>{id}</button>)}
      {!matches.length && <p className="muted">No matching advancements.</p>}
    </div>
    {isAdvancementId(raw) && <button type="button" className="mc-button" onClick={() => onChoose(raw)}>Use {raw}</button>}
  </>
}
