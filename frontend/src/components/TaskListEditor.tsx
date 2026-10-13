import type { Catalog, Task } from '../types.ts'
import { labelFor, newTask } from '../lib/quests.ts'
import { RegistryPicker } from './RegistryPicker.tsx'
import { JsonField } from './JsonField.tsx'

export function TaskListEditor({ kind, tasks, onChange, catalog, epoch }:
  { kind: 'objective' | 'reward'; tasks: Task[]; onChange: (tasks: Task[]) => void; catalog: Catalog; epoch: number }) {
  const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
  const update = (index: number, change: Partial<Task>) => onChange(tasks.map((task, i) => i === index ? { ...task, ...change } : task))
  return <section className="task-list">
    <div className="subheading"><h3>{kind === 'objective' ? 'Objectives' : 'Rewards'}</h3>
      <button type="button" disabled={!types.length} onClick={() => onChange([...tasks, newTask(kind, types[0].id)])}>+ Add {kind}</button></div>
    {!tasks.length && <p className="muted">No {kind === 'objective' ? 'objectives' : 'rewards'} yet.</p>}
    {tasks.map((task, index) => <div className="task-card" key={task.objective_id ?? task.reward_id}>
      <div className="task-type"><label className="field">{kind === 'objective' ? 'Objective type' : 'Reward type'}
        <select value={task.type} onChange={event => onChange(tasks.map((entry, i) => i === index
          ? newTask(kind, event.target.value, task.objective_id ?? task.reward_id) : entry))}>
          {!types.some(type => type.id === task.type) && <option value={task.type}>{task.type} (unavailable)</option>}
          {types.map(type => <option key={type.id} value={type.id}>{labelFor(type.id)} · {type.id}</option>)}
        </select></label>
        <button type="button" className="icon-button danger" aria-label={`Remove ${kind} ${index + 1}`} onClick={() => onChange(tasks.filter((_, i) => i !== index))}>×</button>
      </div>
      {(task.type === 'vuequests:collect_item' || task.type === 'vuequests:item') && <div className="field-row">
        <RegistryPicker label="Item" value={task.item?.id ?? ''} onChange={id => update(index, { item: { ...task.item, id, count: task.item?.count ?? 1 } })} catalog={catalog} epoch={epoch} allowBlocks />
        <label className="field small-field">Count<input type="number" min={1} max={99} step={1} required value={task.item?.count ?? 1}
          onChange={event => update(index, { item: { ...task.item, id: task.item?.id ?? 'minecraft:book', count: Number(event.target.value) } })} /></label>
      </div>}
      {task.type === 'vuequests:entity' && <RegistryPicker label="Entity" kind="entities" value={task.entity ?? ''}
        onChange={entity => update(index, { entity })} catalog={catalog} epoch={epoch} />}
      {task.type === 'vuequests:experience' && <label className="field">Experience<input type="number" min={0} step={1} required value={task.experience ?? 0}
        onChange={event => update(index, { experience: Number(event.target.value) })} /></label>}
      {kind === 'objective' && <RegistryPicker label="Objective icon" value={task.icon ?? ''} onChange={icon => update(index, { icon })} catalog={catalog} epoch={epoch} allowBlocks />}
      {(task.type === 'vuequests:collect_item' || task.type === 'vuequests:item') && <details className="advanced-fields"><summary>Item components</summary>
        <JsonField label="Components" value={task.item?.components ?? {}} onChange={components => update(index,
          { item: { ...task.item, id: task.item?.id ?? 'minecraft:book', count: task.item?.count ?? 1, components } })} /></details>}
      {!['vuequests:collect_item', 'vuequests:item', 'vuequests:entity', 'vuequests:experience'].includes(task.type) &&
        <JsonField key={task.type} label={`Fields (${types.find(type => type.id === task.type)?.fields.join(', ') ?? 'custom type'})`}
          value={Object.fromEntries(Object.entries(task).filter(([key]) => !['type', 'objective_id', 'reward_id', 'icon'].includes(key)))}
          onChange={fields => onChange(tasks.map((entry, i) => i === index ? { ...fields, type: task.type,
            ...(kind === 'objective' ? { objective_id: task.objective_id, icon: task.icon } : { reward_id: task.reward_id }) } : entry))} />}
    </div>)}
  </section>
}
