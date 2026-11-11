import type { Catalog, Task } from '../types.ts'
import { integerInput, taskInputId } from '../lib/questWizard.ts'
import type { InputDrafts } from '../lib/questWizard.ts'

export function TravelCoordinatesEditor({ task, rowKey, catalog, inputs, onInput, onChange }:
  { task: Task; rowKey: string; catalog: Catalog; inputs: InputDrafts;
    onInput: (id: string, text: string) => void; onChange: (task: Task) => void }) {
  const dimensionId = taskInputId(rowKey, 'dimensions')
  return <>
    <label className="field">Dimension
      <input data-panel-focus type="text" list={dimensionId} value={task.dimension ?? ''}
        onChange={event => onChange({ ...task, dimension: event.target.value })} />
      <datalist id={dimensionId}>{catalog.dimensions?.map(id => <option key={id} value={id} />)}</datalist>
    </label>
    <p className="muted">Both corners are inclusive block coordinates. Set matching corners to require one block.</p>
    {(['min', 'max'] as const).map(corner => <fieldset key={corner}>
      <legend>{corner === 'min' ? 'Minimum' : 'Maximum'}</legend>
      {(['x', 'y', 'z'] as const).map(axis => {
        const field = `${corner}_${axis}` as const
        const id = taskInputId(rowKey, field)
        const text = inputs[id] ?? String(task[field] ?? '')
        return <label className="field" key={field}>{axis.toUpperCase()}
          <input id={id} type="text" inputMode="numeric" value={text}
            aria-invalid={integerInput(text, -2_147_483_648, 2_147_483_647) === undefined} onChange={event => {
              onInput(id, event.target.value)
              const value = integerInput(event.target.value, -2_147_483_648, 2_147_483_647)
              if (value !== undefined) onChange({ ...task, [field]: value })
            }} />
        </label>
      })}
    </fieldset>)}
  </>
}
