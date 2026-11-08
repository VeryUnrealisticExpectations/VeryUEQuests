import {useEffect, useId, useRef} from 'react'
import type {Catalog, ComponentTextContext, Task} from '../types.ts'
import {labelFor, newTask} from '../lib/quests.ts'
import type {TaskKind} from '../lib/taskEditor.ts'
import {
    changeTaskType,
    customTaskFields,
    defaultTaskType,
    MAX_OBJECTIVE_COUNT,
    objectiveCount,
    taskDisplayIcon,
    taskEditorId,
    taskProblem,
    updateCustomTaskFields
} from '../lib/taskEditor.ts'
import {ItemIcon} from './ItemIcon.tsx'
import {RegistryPicker} from './RegistryPicker.tsx'
import {JsonField} from './JsonField.tsx'
import {ButtonSymbol} from './ButtonSymbol.tsx'

function taskTypeLabel(type: string) {
    return type === 'vuequests:advancement' ? 'Get advancement' : labelFor(type)
}

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
                    const count = objectiveCount(task) ?? (task.item ? task.item.count ?? 1 : undefined)
                    const target = task.item?.id ?? task.entity
                    const advancement = task.type === 'vuequests:advancement' ? task.advancement : undefined
                    const detail = count !== undefined ? `${count} ${target ?? ''}` : advancement
                    return <button key={key} type="button" className={`mc-button mc-button--icon task-slot${selected ? ' is-active' : ''}`}
                                   aria-label={`Edit ${kind} ${index + 1}: ${taskTypeLabel(task.type)}`}
                                   aria-expanded={selected} aria-controls={selected ? `${id}-editor` : undefined}
                                   title={`${taskTypeLabel(task.type)}${detail ? ` · ${detail}` : ''}`}
                                   onClick={() => onActivate(selected ? undefined : key)}>
                        <ItemIcon id={taskDisplayIcon(kind, task)} epoch={epoch}/>
                        {count !== undefined && <span className="task-slot-count" aria-hidden="true">{count}</span>}
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
    const advancementInput = useRef<HTMLInputElement>(null)
    const types = kind === 'objective' ? catalog.objectiveTypes : catalog.rewardTypes
    const update = (change: Partial<Task>) => onChange({...task, ...change})
    const isItem = task.type === 'vuequests:collect_item' || task.type === 'vuequests:item'
    const isEntity = task.type === 'vuequests:entity'
    const isAdvancement = task.type === 'vuequests:advancement'
    const targetCount = objectiveCount(task)
    useEffect(() => {
        if (isAdvancement) advancementInput.current?.setCustomValidity(taskProblem(task) ?? '')
    }, [isAdvancement, task])
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
                        <option value={task.type}>{taskTypeLabel(task.type)} (unavailable)</option>}
                    {types.map(type => <option key={type.id}
                                               value={type.id}>{taskTypeLabel(type.id)}{type.id.startsWith('vuequests:') ? '' : ` · ${type.id.split(':')[0]}`}</option>)}
                </select>
            </label>
            <button type="button" className="mc-button mc-button--icon mc-button--red"
                    aria-label={`Remove ${kind} ${index + 1}`} onClick={onRemove}>×
            </button>
        </div>
        {(isItem || isEntity) && <div className="field-row task-item-fields">
            {isItem
                ? <RegistryPicker label="Item" value={task.item?.id ?? ''} onChange={id => update({
                    item: {...task.item, id}
                })} catalog={catalog} epoch={epoch}/>
                : <RegistryPicker label="Entity" kind="entities" value={task.entity ?? ''}
                                  onChange={entity => update({entity})} catalog={catalog} epoch={epoch}/>}
            {targetCount !== undefined
                ? <label className="field small-field objective-count-field">Count
                    <input type="number" min={1} max={MAX_OBJECTIVE_COUNT} step={1} required value={targetCount}
                           onChange={event => update({count: Number(event.target.value)})}/>
                </label>
                : <label className="field small-field">Count
                    <input type="number" min={1} max={99} step={1} required value={task.item?.count ?? 1}
                           onChange={event => update({
                               item: {...task.item, id: task.item?.id ?? 'minecraft:book', count: Number(event.target.value)}
                           })}/>
                </label>}
        </div>}
        {isAdvancement && <label className="field">Advancement ID
            <input ref={advancementInput} required list={`${id}-advancements`} value={task.advancement ?? ''}
                   placeholder="minecraft:story/mine_stone" autoComplete="off" spellCheck={false}
                   onChange={event => update({advancement: event.target.value})}/>
            <datalist id={`${id}-advancements`}>
                {catalog.advancements?.map(id => <option key={id} value={id}/>)}
            </datalist>
        </label>}
        {task.type === 'vuequests:experience' &&
            <label className="field">Experience<input type="number" min={0} step={1} required
                                                      value={task.experience ?? 0}
                                                      onChange={event => update({experience: Number(event.target.value)})}/></label>}
        {isItem && <details className="advanced-fields">
            <summary>Item components</summary>
            <JsonField label="Components" value={task.item?.components ?? {}} onChange={components => update({
                item: {...task.item, id: task.item?.id ?? 'minecraft:book', components},
            })}/></details>}
        {!isItem && !isEntity && !isAdvancement && task.type !== 'vuequests:experience' &&
            <JsonField key={task.type}
                       label={`Fields (${types.find(type => type.id === task.type)?.fields.join(', ') ?? 'custom type'})`}
                       value={customTaskFields(kind, task)}
                       onChange={fields => onChange(updateCustomTaskFields(kind, task, fields))}/>}
        <div className="task-editor-footer">
            <button type="button" className="mc-button mc-button--small mc-button--green" onClick={finish}>Done</button>
        </div>
    </div>
}
