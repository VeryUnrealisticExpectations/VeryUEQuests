import {useEffect, useId, useRef} from 'react'
import type {Catalog, ComponentTextContext, Task} from '../types.ts'
import {labelFor, newTask} from '../lib/quests.ts'
import type {TaskKind} from '../lib/taskEditor.ts'
import {
    changeTaskType,
    customTaskFields,
    defaultTaskType,
    taskDisplayIcon,
    taskEditorId,
    updateCustomTaskFields
} from '../lib/taskEditor.ts'
import {ItemIcon} from './ItemIcon.tsx'
import {RegistryPicker} from './RegistryPicker.tsx'
import {JsonField} from './JsonField.tsx'
import {ButtonSymbol} from './ButtonSymbol.tsx'

interface TaskListEditorProps {
    kind: TaskKind
    tasks: Task[]
    onChange: (tasks: Task[]) => void
    catalog: Catalog
    epoch: number
    textContext: ComponentTextContext
    activeTask?: string
    onActivate: (id?: string) => void
    onCollapse: (id: string) => void
}

export function TaskListEditor({kind, tasks, onChange, catalog, epoch, activeTask, onActivate, onCollapse}: TaskListEditorProps) {
    const id = useId()
    const section = useRef<HTMLElement>(null)
    const slots = useRef<HTMLDivElement>(null)
    const pointerDown = useRef(false)
    const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
    const defaultType = defaultTaskType(kind, types)
    const activeIndex = tasks.findIndex((task, index) => taskEditorId(kind, task, index) === activeTask)
    const active = tasks[activeIndex]
    useEffect(() => {
        const press = () => { pointerDown.current = true }
        const release = () => {
            pointerDown.current = false
            // Finish the click before a shorter modal can move its target.
            requestAnimationFrame(() => {
                if (activeTask && activeIndex >= 0 && !section.current?.contains(document.activeElement)) onCollapse(activeTask)
            })
        }
        document.addEventListener('pointerdown', press, true)
        document.addEventListener('pointerup', release, true)
        document.addEventListener('pointercancel', release, true)
        return () => {
            document.removeEventListener('pointerdown', press, true)
            document.removeEventListener('pointerup', release, true)
            document.removeEventListener('pointercancel', release, true)
        }
    }, [activeTask, activeIndex, onCollapse])
    useEffect(() => {
        if (activeIndex >= 0) slots.current?.querySelectorAll<HTMLButtonElement>('.task-slot')[activeIndex]?.scrollIntoView({block: 'nearest', inline: 'nearest'})
    }, [activeIndex, tasks.length])
    const collapse = (returnFocus = false) => {
        if (returnFocus && activeIndex >= 0) {
            const buttons = slots.current?.querySelectorAll<HTMLButtonElement>('.task-slot')
            buttons?.[activeIndex]?.focus({preventScroll: true})
        }
        if (activeTask) onCollapse(activeTask)
    }
    return <section ref={section} className="task-list" onBlur={event => {
        if (active && !pointerDown.current && !event.currentTarget.contains(event.relatedTarget)) collapse()
    }}>
        <div className="subheading"><h3>{kind === 'objective' ? 'Objectives' : 'Rewards'}</h3>
            <small>{tasks.length}</small>
        </div>
        <div className="task-icon-row">
            <div ref={slots} className="task-icon-slots" role="group"
                 aria-label={`${kind === 'objective' ? 'Objective' : 'Reward'} icons`}>
                {tasks.map((task, index) => {
                    const key = taskEditorId(kind, task, index)
                    const selected = key === activeTask
                    return <button key={key} type="button" className={`mc-button mc-button--icon task-slot${selected ? ' is-active' : ''}`}
                                   aria-label={`Edit ${kind} ${index + 1}: ${labelFor(task.type)}`}
                                   aria-expanded={selected} aria-controls={selected ? `${id}-editor` : undefined}
                                   title={`${labelFor(task.type)}${task.item ? ` · ${task.item.count} ${task.item.id}` : ''}`}
                                   onClick={() => onActivate(selected ? undefined : key)}>
                        <ItemIcon id={taskDisplayIcon(kind, task)} epoch={epoch}/>
                        {task.item && <span className="task-slot-count" aria-hidden="true">{task.item.count}</span>}
                    </button>
                })}
            </div>
            <button type="button" className="mc-button mc-button--icon mc-button--green task-add-button"
                    aria-label={`Add ${kind}`} title={`Add ${kind}`} disabled={!defaultType}
                    onClick={() => {
                        if (!defaultType) return
                        const task = newTask(kind, defaultType)
                        onChange([...tasks, task])
                        onActivate(taskEditorId(kind, task, tasks.length))
                    }}><ButtonSymbol symbol="plus"/>
            </button>
        </div>
        {!tasks.length && <p className="muted">No {kind === 'objective' ? 'objectives' : 'rewards'} yet.</p>}
        {active && <TaskEditor key={activeTask} id={`${id}-editor`} kind={kind} task={active} index={activeIndex}
                              onChange={changed => onChange(tasks.map((entry, i) => i === activeIndex ? changed : entry))}
                              onRemove={() => {
                                  onChange(tasks.filter((_, i) => i !== activeIndex))
                                  collapse()
                              }}
                              onDone={() => collapse(true)} catalog={catalog} epoch={epoch}/>}
    </section>
}

function TaskEditor({id, kind, task, index, onChange, onRemove, onDone, catalog, epoch}:
                    {
                        id: string;
                        kind: TaskKind;
                        task: Task;
                        index: number;
                        onChange: (task: Task) => void;
                        onRemove: () => void;
                        onDone: () => void;
                        catalog: Catalog;
                        epoch: number
                    }) {
    const editor = useRef<HTMLDivElement>(null)
    const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
    const update = (change: Partial<Task>) => onChange({...task, ...change})
    const isItem = task.type === 'vuequests:collect_item' || task.type === 'vuequests:item'
    const finish = () => {
        const fields = editor.current?.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input, select, textarea')
        if (fields && Array.from(fields).some(field => !field.reportValidity())) return
        onDone()
    }
    return <div ref={editor} id={id} className="task-card task-expanded-editor">
        <div className="task-card-header">
            <span className="task-card-icon"><ItemIcon id={taskDisplayIcon(kind, task)} epoch={epoch}/></span>
            <label className="field task-type-field">{kind === 'objective' ? 'Objective type' : 'Reward type'}
                <select autoFocus value={task.type} title={task.type}
                        onChange={event => onChange(changeTaskType(kind, task, event.target.value))}>
                    {!types.some(type => type.id === task.type) &&
                        <option value={task.type}>{labelFor(task.type)} (unavailable)</option>}
                    {types.map(type => <option key={type.id}
                                               value={type.id}>{labelFor(type.id)}{type.id.startsWith('vuequests:') ? '' : ` · ${type.id.split(':')[0]}`}</option>)}
                </select>
            </label>
            <button type="button" className="mc-button mc-button--icon mc-button--red"
                    aria-label={`Remove ${kind} ${index + 1}`} onClick={onRemove}>×
            </button>
        </div>
        {isItem && <div className="field-row task-item-fields">
            <RegistryPicker label="Item" value={task.item?.id ?? ''} onChange={itemId => update({
                item: {
                    ...task.item,
                    id: itemId,
                    count: task.item?.count ?? 1
                }
            })} catalog={catalog} epoch={epoch}/>
            <label className="field small-field">Count<input type="number" min={1} max={99} step={1} required
                                                             value={task.item?.count ?? 1}
                                                             onChange={event => update({
                                                                 item: {
                                                                     ...task.item,
                                                                     id: task.item?.id ?? 'minecraft:book',
                                                                     count: Number(event.target.value)
                                                                 }
                                                             })}/></label>
        </div>}
        {task.type === 'vuequests:entity' && <RegistryPicker label="Entity" kind="entities" value={task.entity ?? ''}
                                                             onChange={entity => update({entity})} catalog={catalog}
                                                             epoch={epoch}/>}
        {task.type === 'vuequests:experience' &&
            <label className="field">Experience<input type="number" min={0} step={1} required
                                                      value={task.experience ?? 0}
                                                      onChange={event => update({experience: Number(event.target.value)})}/></label>}
        {isItem && <details className="advanced-fields">
            <summary>Item components</summary>
            <JsonField label="Components" value={task.item?.components ?? {}} onChange={components => update({
                item: {...task.item, id: task.item?.id ?? 'minecraft:book', count: task.item?.count ?? 1, components},
            })}/></details>}
        {!['vuequests:collect_item', 'vuequests:item', 'vuequests:entity', 'vuequests:experience'].includes(task.type) &&
            <JsonField key={task.type}
                       label={`Fields (${types.find(type => type.id === task.type)?.fields.join(', ') ?? 'custom type'})`}
                       value={customTaskFields(kind, task)}
                       onChange={fields => onChange(updateCustomTaskFields(kind, task, fields))}/>}
        <div className="task-editor-footer">
            <button type="button" className="mc-button mc-button--small mc-button--green" onClick={finish}>Done</button>
        </div>
    </div>
}
