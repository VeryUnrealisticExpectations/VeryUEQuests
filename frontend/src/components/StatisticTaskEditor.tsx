import type { Catalog, Task } from '../types.ts'
import { ResourceIdPicker } from './ResourceIdPicker.tsx'

export function StatisticTaskEditor({ task, catalog, onChange }:
  { task: Task; catalog: Catalog; onChange: (task: Task) => void }) {
  const types = catalog.statisticTypes ?? []
  const selected = types.find(type => type.id === task.stat_type)
  return <>
    <label className="field">Statistic type
      <select data-panel-focus value={task.stat_type ?? ''} onChange={event => onChange({ ...task, stat_type: event.target.value, statistic: '' })}>
        {!selected && <option value={task.stat_type ?? ''}>{task.stat_type || 'Choose statistic type'}</option>}
        {types.map(type => <option key={type.id} value={type.id}>{type.name} ({type.id})</option>)}
      </select>
    </label>
    <ResourceIdPicker key={task.stat_type} kind="statistics" value={task.statistic ?? ''}
      entries={(selected?.values ?? []).map(id => ({ id, name: id }))}
      onChoose={statistic => onChange({ ...task, statistic })} />
  </>
}
