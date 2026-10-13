import { useState } from 'react'

export function JsonField({ label, value, onChange }: { label: string; value: Record<string, unknown>; onChange: (value: Record<string, unknown>) => void }) {
  const [text, setText] = useState(() => JSON.stringify(value, null, 2))
  const [error, setError] = useState('')
  return <label className="field json-field">{label}<textarea spellCheck={false} value={text} aria-invalid={!!error} rows={5}
    onChange={event => {
      const input = event.currentTarget
      setText(input.value)
      try {
        const json: unknown = JSON.parse(input.value)
        if (json === null || typeof json !== 'object' || Array.isArray(json)) throw new Error('Enter a JSON object.')
        input.setCustomValidity(''); setError(''); onChange(json as Record<string, unknown>)
      } catch (failure) {
        const message = failure instanceof Error ? failure.message : 'Enter valid JSON.'
        input.setCustomValidity(message); setError(message)
      }
    }} />{error && <small className="field-error">{error}</small>}</label>
}
