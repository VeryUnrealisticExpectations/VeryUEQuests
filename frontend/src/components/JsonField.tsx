import { jsonObjectInput } from '../lib/questWizard.ts'

export function JsonField({ id, label, text, onChange }:
  { id: string; label: string; text: string; onChange: (text: string) => void }) {
  const invalid = !jsonObjectInput(text)
  return <label className="field json-field">{label}<textarea id={id} spellCheck={false} value={text} aria-invalid={invalid}
    aria-describedby={invalid ? id + '-error' : undefined} rows={5} onChange={event => onChange(event.target.value)} />
    {invalid && <small id={id + '-error'} className="field-error">Enter a valid JSON object.</small>}
  </label>
}
