import type { Catalog, Task } from '../types.ts'
import { labelFor } from '../lib/quests.ts'
import { customTaskFields, taskDisplayIcon, updateCustomTaskFields } from '../lib/taskEditor.ts'
import type { TaskKind } from '../lib/taskEditor.ts'
import { attributeAmountInput, integerInput, jsonObjectInput, taskInputId, taskQuantity } from '../lib/questWizard.ts'
import type { InputDrafts, TaskRow } from '../lib/questWizard.ts'
import { ButtonSymbol } from './ButtonSymbol.tsx'
import { ItemIcon } from './ItemIcon.tsx'
import { JsonField } from './JsonField.tsx'

interface TaskListEditorProps {
  kind: TaskKind
  rows: TaskRow[]
  inputs: InputDrafts
  catalog: Catalog
  epoch: number
  activeTrigger?: string
  onInput: (id: string, text: string) => void
  onChange: (key: string, task: Task) => void
  onRemove: (key: string) => void
  onPick: (key: string) => void
  onChooseType: (key?: string) => void
}

export function TaskListEditor({ kind, rows, inputs, catalog, epoch, activeTrigger, onInput, onChange, onRemove,
  onPick, onChooseType }: TaskListEditorProps) {
  const addId = 'quest-add-' + kind
  const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
  return <>
    <p className="wizard-helper">{kind === 'objective' ? 'What must the player do?' : 'What does the player earn?'}</p>
    <div className="wizard-task-list">
      {rows.map(({ key, task }, index) => {
        const targetId = taskInputId(key, 'target')
        const typeId = taskInputId(key, 'type')
        const componentsId = taskInputId(key, 'components')
        const quantity = taskQuantity(task)
        const quantityId = quantity && taskInputId(key, quantity.field)
        const amountId = taskInputId(key, 'amount')
        const amountText = inputs[amountId] ?? String(task.amount ?? '')
        const isItem = task.type === 'vuequests:collect_item' || task.type === 'vuequests:item'
        const inlineOnly = task.type === 'vuequests:experience' || task.type === 'vuequests:experience_levels'
        const target = taskTarget(task, catalog)
        const label = taskTypeLabel(kind, task.type)
        const changeQuantity = (text: string) => {
          if (!quantity || !quantityId) return
          onInput(quantityId, text)
          const value = integerInput(text, quantity.min, quantity.max)
          if (value === undefined) return
          if (quantity.field === 'itemCount') onChange(key, { ...task, item: { ...task.item, id: task.item?.id ?? '', count: value } })
          else onChange(key, { ...task, [quantity.field]: value })
        }
        return <div key={key} className="wizard-task" data-active={activeTrigger === targetId || activeTrigger === typeId || undefined}>
          <div className="wizard-task-row">
            <button type="button" id={targetId} className="wizard-task-target" aria-label={'Edit ' + kind + ' ' + (index + 1) + ': ' + label}
              aria-expanded={inlineOnly ? undefined : activeTrigger === targetId}
              aria-controls={inlineOnly ? undefined : 'quest-wizard-panel'} onClick={() => onPick(key)}>
              <ItemIcon id={taskDisplayIcon(kind, task)} epoch={epoch} />
              <span><strong>{target || label}</strong>{target && <small>{label}</small>}</span>
            </button>
            {quantity && quantityId && <label className="field wizard-task-quantity">{quantity.label}
              <input id={quantityId} type="text" inputMode="numeric" value={inputs[quantityId] ?? String(quantity.value)}
                aria-invalid={integerInput(inputs[quantityId] ?? String(quantity.value), quantity.min, quantity.max) === undefined}
                onChange={event => changeQuantity(event.target.value)} />
            </label>}
            {task.type === 'vuequests:attribute' && <label className="field wizard-task-quantity">Amount
              <input id={amountId} type="text" inputMode="decimal" value={amountText}
                aria-invalid={attributeAmountInput(amountText) === undefined} onChange={event => {
                  const text = event.target.value
                  onInput(amountId, text)
                  const amount = attributeAmountInput(text)
                  if (amount !== undefined) onChange(key, { ...task, amount })
                }} />
            </label>}
            <button type="button" className="mc-button mc-button--icon mc-button--small mc-button--red"
              aria-label={'Remove ' + kind + ' ' + (index + 1)} onClick={() => onRemove(key)}>×</button>
          </div>
          <div className="wizard-task-actions">
            <button type="button" id={typeId} className="mc-button mc-button--small" aria-expanded={activeTrigger === typeId}
              aria-controls="quest-wizard-panel" onClick={() => onChooseType(key)}>Change type</button>
            {isItem && <details className="advanced-fields">
              <summary>Item components</summary>
              <JsonField id={componentsId} label="Components" text={inputs[componentsId] ?? JSON.stringify(task.item?.components ?? {}, null, 2)}
                onChange={text => {
                  onInput(componentsId, text)
                  const components = jsonObjectInput(text)
                  if (components) onChange(key, { ...task, item: { ...task.item, id: task.item?.id ?? '', components } })
                }} />
            </details>}
          </div>
        </div>
      })}
    </div>
    {!rows.length && <p className="muted">No {kind === 'objective' ? 'objectives' : 'rewards'} yet.</p>}
    <button type="button" id={addId} className={'mc-button wizard-add' + (activeTrigger === addId ? ' mc-button--green' : '')}
      disabled={!types.length} aria-expanded={activeTrigger === addId} aria-controls="quest-wizard-panel" onClick={() => onChooseType()}>
      <ButtonSymbol symbol="plus" /> Add {kind}
    </button>
  </>
}

export function TaskSummary({ kind, rows, catalog, epoch }: Pick<TaskListEditorProps, 'kind' | 'rows' | 'catalog' | 'epoch'>) {
  if (!rows.length) return <p className="muted">None</p>
  return <ul className="wizard-summary-tasks">
    {rows.map(({ key, task }) => {
      const quantity = taskQuantity(task)
      const target = taskTarget(task, catalog)
      const components = task.item?.components
      const fields = !target && !quantity ? customTaskFields(kind, task) : components
      return <li key={key}>
        <div className="wizard-summary-task-row">
          <ItemIcon id={taskDisplayIcon(kind, task)} epoch={epoch} />
          <span><strong>{taskTypeLabel(kind, task.type)}</strong>
            {target && <small>{target}</small>}</span>
          {quantity && <span>{quantity.value}</span>}
          {task.type === 'vuequests:attribute' && task.amount !== undefined && <span>+{task.amount}</span>}
        </div>
        {fields && Object.keys(fields).length > 0 && <details className="advanced-fields">
          <summary>{components ? 'Item components' : 'Custom fields'}</summary>
          <pre className="wizard-summary-payload">{JSON.stringify(fields, null, 2)}</pre>
        </details>}
      </li>
    })}
  </ul>
}

export function CustomTaskEditor({ kind, row, catalog, inputs, onInput, onChange }:
  Pick<TaskListEditorProps, 'kind' | 'catalog' | 'inputs' | 'onInput' | 'onChange'> & { row: TaskRow }) {
  const { key, task } = row
  const id = taskInputId(key, 'fields')
  const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
  const fields = types.find(type => type.id === task.type)?.fields
  return <JsonField id={id} label={fields?.length ? 'Fields (' + fields.join(', ') + ')' : 'Custom fields'}
    text={inputs[id] ?? JSON.stringify(customTaskFields(kind, task), null, 2)} onChange={text => {
      onInput(id, text)
      const value = jsonObjectInput(text)
      if (value) onChange(key, updateCustomTaskFields(kind, task, value))
    }} />
}

function taskTarget(task: Task, catalog: Catalog): string | undefined {
  if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:item') {
    return catalog.items.find(entry => entry.id === task.item?.id)?.name ?? task.item?.id
  }
  if (task.type === 'vuequests:entity') return catalog.entities.find(entry => entry.id === task.entity)?.name ?? task.entity
  if (task.type === 'vuequests:advancement') return task.advancement || 'Choose advancement'
  if (task.type === 'vuequests:loot_table') return task.loot_table || 'Choose loot table'
  if (task.type === 'vuequests:command') return task.command || 'Enter command'
  if (task.type === 'vuequests:attribute') {
    return catalog.attributes?.find(entry => entry.id === task.attribute)?.name ?? (task.attribute || 'Choose attribute')
  }
}

function taskTypeLabel(kind: TaskKind, type: string): string {
  if (kind === 'reward' && type === 'vuequests:advancement') return 'Advancement reward'
  return taskTypeNames[type] ?? labelFor(type)
}

const taskTypeNames: Record<string, string> = {
  'vuequests:collect_item': 'Collect item',
  'vuequests:entity': 'Slay entity',
  'vuequests:advancement': 'Get advancement',
  'vuequests:item': 'Item reward',
  'vuequests:experience': 'Experience',
  'vuequests:experience_levels': 'Experience levels',
  'vuequests:loot_table': 'Loot table',
  'vuequests:command': 'Command',
  'vuequests:attribute': 'Attribute boost',
}

const typeHints: Record<string, { icon: string; description: string }> = {
  'vuequests:collect_item': { icon: 'minecraft:cobblestone', description: 'Require a quantity of an item.' },
  'vuequests:entity': { icon: 'minecraft:iron_sword', description: 'Require kills of an entity type.' },
  'vuequests:advancement': { icon: 'minecraft:enchanted_book', description: 'Require a Minecraft advancement.' },
  'vuequests:item': { icon: 'minecraft:chest', description: 'Give the player an item stack.' },
  'vuequests:experience': { icon: 'minecraft:experience_bottle', description: 'Give the player experience.' },
  'vuequests:experience_levels': { icon: 'minecraft:experience_bottle', description: 'Give the player whole experience levels.' },
  'vuequests:loot_table': { icon: 'minecraft:chest', description: 'Roll a loaded loot table for the player.' },
  'vuequests:command': { icon: 'minecraft:command_block', description: 'Run a command as the claiming player.' },
  'vuequests:attribute': { icon: 'minecraft:golden_apple', description: 'Apply an additive player attribute modifier.' },
}

const advancementRewardHint = { icon: 'minecraft:enchanted_book', description: 'Grant a Minecraft advancement.' }

export function TaskTypePicker({ kind, catalog, epoch, value, onChoose }:
  { kind: TaskKind; catalog: Catalog; epoch: number; value?: string; onChoose: (type: string) => void }) {
  const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
  const order = Object.keys(typeHints)
  const rank = (id: string) => id in typeHints ? order.indexOf(id) : order.length
  const choices = [...types].sort((a, b) => rank(a.id) - rank(b.id))
  return <div className="wizard-type-choices">
    {value && !choices.some(type => type.id === value) && <p className="muted">{value} is currently unavailable.</p>}
    {choices.map(({ id }) => {
      const hint = kind === 'reward' && id === 'vuequests:advancement' ? advancementRewardHint : typeHints[id]
      return <button key={id} type="button" className="mc-button wizard-type-choice"
        aria-pressed={value ? value === id : undefined} onClick={() => onChoose(id)}>
        <ItemIcon id={hint?.icon ?? (kind === 'objective' ? 'minecraft:book' : 'minecraft:chest')} epoch={epoch} />
        <span><strong>{taskTypeLabel(kind, id)}</strong><small>{hint?.description ?? id}</small></span>
        <span aria-hidden="true">›</span>
      </button>
    })}
  </div>
}
