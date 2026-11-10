import { useLayoutEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { Catalog, ComponentTextContext, Position, Quest, Section, Task } from '../types.ts'
import { DEFAULT_QUEST_BACKGROUND, dependencyProblem, newQuest, newTask } from '../lib/quests.ts'
import { changeTaskType, taskEditorId } from '../lib/taskEditor.ts'
import type { TaskKind } from '../lib/taskEditor.ts'
import { blockedPrerequisites, integerInput, questIssues, questStages, sectionsWithDraft, taskInputId, taskQuantity, taskRowsFor } from '../lib/questWizard.ts'
import type { InputDrafts, QuestIssue, QuestStage, QuestTextField, TaskRows } from '../lib/questWizard.ts'
import { AdvancementPicker } from './AdvancementPicker.tsx'
import { ComponentEditor } from './ComponentEditor.tsx'
import { ComponentText } from './ComponentText.tsx'
import { QuestBackgroundPicker } from './QuestBackgroundPicker.tsx'
import { QuestDetailsStage } from './QuestDetailsStage.tsx'
import { QuestNodeIcon } from './QuestNodeIcon.tsx'
import { PrerequisitePicker, QuestReviewStage } from './QuestReviewStage.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { ResourceIdPicker } from './ResourceIdPicker.tsx'
import { CustomTaskEditor, TaskListEditor, TaskTypePicker } from './TaskListEditor.tsx'
import { WizardDialog } from './WizardDialog.tsx'

type DetailPicker = QuestTextField | 'icon' | 'background'
type Panel = { kind: 'detail'; field: DetailPicker }
  | { kind: 'type'; taskKind: TaskKind; key?: string }
  | { kind: 'task'; taskKind: TaskKind; key: string }
  | { kind: 'prerequisites' }

const textFields = [
  { field: 'title', label: 'Quest title', panelTitle: 'Edit title' },
  { field: 'subtitle', label: 'Subtitle', panelTitle: 'Edit subtitle' },
  { field: 'description', label: 'Description', panelTitle: 'Edit description' },
] as const

const headings: Record<QuestStage, string> = { details: 'Quest details', objectives: 'Objectives', rewards: 'Rewards', review: 'Review quest' }

export function QuestEditorDialog({ quest, section, sections, position, catalog, epoch, textContext, onApply, onDelete, onClose }:
  { quest?: Quest; section: Section; sections: Section[]; position: Position; catalog: Catalog; epoch: number; textContext: ComponentTextContext;
    onApply: (quest: Quest) => void; onDelete?: () => void; onClose: () => void }) {
  const [base, setBase] = useState(() => quest ? structuredClone(quest) : newQuest(section.section_id, position))
  const [rows, setRows] = useState<TaskRows>(() => ({
    objective: taskRowsFor('objective', base.objectives), reward: taskRowsFor('reward', base.rewards),
  }))
  const [inputs, setInputs] = useState<InputDrafts>({})
  const [stage, setStage] = useState<QuestStage>('details')
  const [visited, setVisited] = useState<QuestStage[]>(['details'])
  const [opened, setOpened] = useState<{ panel: Panel; trigger: string }>()
  const [focusRequest, setFocusRequest] = useState<{ id: string }>()
  const [dependencyError, setDependencyError] = useState('')
  const panel = opened?.panel
  const draft = useMemo(() => ({
    ...base, objectives: rows.objective.map(row => row.task), rewards: rows.reward.map(row => row.task),
  }), [base, rows])
  const issues = useMemo(() => questIssues(draft, rows, inputs, sections), [draft, rows, inputs, sections])
  const blocked = useMemo(() => blockedPrerequisites(sections, draft.quest_id), [sections, draft.quest_id])
  const selected = panel?.kind === 'task' || panel?.kind === 'type'
    ? rows[panel.taskKind].find(row => row.key === panel.key) : undefined

  useLayoutEffect(() => {
    if (!focusRequest) return
    const requested = document.getElementById(focusRequest.id)
    const element = focusRequest.id === 'quest-wizard-panel'
      ? requested?.querySelector<HTMLElement>('[data-panel-focus]') ?? document.getElementById('quest-wizard-panel-title')
      : requested
    element?.closest('details')?.setAttribute('open', '')
    element?.focus()
  }, [focusRequest])

  const focus = (id: string) => setFocusRequest({ id })
  const open = (next: Panel, trigger: string, target = 'quest-wizard-panel') => {
    setOpened({ panel: next, trigger })
    focus(target)
  }
  const close = () => {
    setOpened(undefined)
    if (opened) focus(opened.trigger)
  }
  const navigate = (next: QuestStage, target = 'quest-stage-heading') => {
    setStage(next)
    setVisited(current => current.includes(next) ? current : [...current, next])
    setOpened(undefined)
    focus(target)
  }
  const display = (change: Partial<Quest['display']>) =>
    setBase(current => ({ ...current, display: { ...current.display, ...change } }))
  const input = (id: string, text: string) => setInputs(current => ({ ...current, [id]: text }))
  const changePosition = (axis: 'x' | 'y', text: string) => {
    input('quest-' + axis, text)
    const value = integerInput(text, -2_147_483_648, 2_147_483_647)
    if (value !== undefined) setBase(current => ({ ...current, position: { ...current.position, [axis]: value } }))
  }
  const updateTask = (kind: TaskKind, key: string, task: Task) =>
    setRows(current => ({ ...current, [kind]: current[kind].map(row => row.key === key ? { key, task } : row) }))
  const clearTaskInputs = (key: string) => setInputs(current => Object.fromEntries(Object.entries(current)
    .filter(([id]) => !id.startsWith(taskInputId(key, '')))))
  const openTask = (taskKind: TaskKind, key: string, task: Task) => {
    const quantity = taskQuantity(task)
    if (quantity?.field === 'experience' || quantity?.field === 'levels') {
      setOpened(undefined)
      focus(taskInputId(key, quantity.field))
    } else open({ kind: 'task', taskKind, key }, taskInputId(key, 'target'))
  }
  const selectType = (kind: TaskKind, type: string) => {
    if (selected) {
      const { key, task } = selected
      const changed = changeTaskType(kind, task, type)
      if (changed !== task) {
        clearTaskInputs(key)
        updateTask(kind, key, changed)
      }
      openTask(kind, key, changed)
    } else {
      const task = newTask(kind, type)
      const key = taskEditorId(kind, task, rows[kind].length)
      setRows(current => ({ ...current, [kind]: [...current[kind], { key, task }] }))
      openTask(kind, key, task)
    }
  }
  const removeTask = (kind: TaskKind, key: string) => {
    const index = rows[kind].findIndex(row => row.key === key)
    const neighbor = rows[kind][index + 1] ?? rows[kind][index - 1]
    clearTaskInputs(key)
    setRows(current => ({ ...current, [kind]: current[kind].filter(row => row.key !== key) }))
    if ((panel?.kind === 'task' || panel?.kind === 'type') && panel.key === key) setOpened(undefined)
    focus(neighbor ? taskInputId(neighbor.key, 'target') : 'quest-add-' + kind)
  }
  const selectPrerequisite = (id: string, checked: boolean) => {
    setDependencyError('')
    if (checked) {
      const next = { ...draft, dependencies: [...new Set([...draft.dependencies, id])] }
      const problem = dependencyProblem(sectionsWithDraft(sections, next))
      if (problem) { setDependencyError(problem); return }
      setBase(current => ({ ...current, dependencies: next.dependencies }))
    } else setBase(current => ({ ...current, dependencies: current.dependencies.filter(entry => entry !== id) }))
  }
  const showIssue = (issue: QuestIssue) => {
    navigate(issue.stage, issue.target)
    if (issue.target === 'quest-title' || issue.target === 'quest-icon') {
      open({ kind: 'detail', field: issue.target === 'quest-title' ? 'title' : 'icon' }, issue.target)
    } else if (issue.target === 'quest-prerequisites') {
      open({ kind: 'prerequisites' }, issue.target)
    } else if (issue.taskKey && (issue.target.endsWith('-target') || issue.target.endsWith('-fields'))) {
      const kind = issue.stage === 'objectives' ? 'objective' : 'reward'
      const row = rows[kind].find(entry => entry.key === issue.taskKey)
      if (row && issue.target.endsWith('-fields')) open({ kind: 'task', taskKind: kind, key: row.key }, taskInputId(row.key, 'target'), issue.target)
      else if (row) openTask(kind, row.key, row.task)
    }
  }

  const background = draft.display.background_sprite ?? DEFAULT_QUEST_BACKGROUND
  let panelTitle: string | undefined
  let panelContent: ReactNode
  if (panel?.kind === 'detail') {
    if (panel.field === 'icon') {
      panelTitle = 'Choose quest icon'
      panelContent = <RegistryPicker key="quest-icon" label="Search quest icons" value={draft.display.icon} catalog={catalog} epoch={epoch} panel
        onChange={icon => { display({ icon }); close() }} />
    } else if (panel.field === 'background') {
      panelTitle = 'Choose quest background'
      panelContent = <QuestBackgroundPicker value={background} backgrounds={textContext.questBackgrounds ?? []} revision={textContext.assetRevision}
        panel onChange={id => { display({ background_sprite: id === DEFAULT_QUEST_BACKGROUND ? undefined : id }); close() }} />
    } else panelTitle = textFields.find(field => field.field === panel.field)?.panelTitle
  } else if (panel?.kind === 'type') {
    panelTitle = 'Choose ' + panel.taskKind + ' type'
    panelContent = <TaskTypePicker kind={panel.taskKind} catalog={catalog} epoch={epoch} value={selected?.task.type}
      onChoose={type => selectType(panel.taskKind, type)} />
  } else if (panel?.kind === 'task' && selected) {
    const { task, key } = selected
    const commit = (change: Partial<Task>) => { updateTask(panel.taskKind, key, { ...task, ...change }); close() }
    if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:item') {
      panelTitle = 'Choose ' + panel.taskKind + ' item'
      panelContent = <RegistryPicker key={key} label="Search items" value={task.item?.id ?? ''} catalog={catalog} epoch={epoch} panel
        onChange={id => commit({ item: { ...task.item, id } })} />
    } else if (task.type === 'vuequests:entity') {
      panelTitle = 'Choose entity'
      panelContent = <RegistryPicker key={key} label="Search entities" value={task.entity ?? ''} catalog={catalog} epoch={epoch} kind="entities" panel
        onChange={entity => commit({ entity })} />
    } else if (task.type === 'vuequests:advancement') {
      panelTitle = 'Choose advancement'
      panelContent = <AdvancementPicker key={key} entries={catalog.advancements ?? []} value={task.advancement ?? ''} onChoose={advancement => commit({ advancement })} />
    } else if (task.type === 'vuequests:loot_table') {
      panelTitle = 'Choose loot table'
      panelContent = <ResourceIdPicker key={key} kind="loot tables" entries={(catalog.lootTables ?? []).map(id => ({ id, name: id }))}
        value={task.loot_table ?? ''} onChoose={loot_table => commit({ loot_table })} />
    } else if (task.type === 'vuequests:attribute') {
      panelTitle = 'Choose player attribute'
      panelContent = <ResourceIdPicker key={key} kind="player attributes" entries={catalog.attributes ?? []}
        value={task.attribute ?? ''} onChoose={attribute => commit({ attribute })} />
    } else if (task.type === 'vuequests:command') {
      panelTitle = 'Edit reward command'
      panelContent = <>
        <label className="field">Minecraft command
          <input data-panel-focus type="text" value={task.command ?? ''} spellCheck={false} placeholder="give @s minecraft:diamond"
            onChange={event => updateTask(panel.taskKind, key, { ...task, command: event.target.value })} />
        </label>
        <p className="muted">Runs at permission level 2. Use @s for the claimant, or another selector such as @a.</p>
        <div className="wizard-panel-actions"><button type="button" className="mc-button" onClick={close}>Done</button></div>
      </>
    } else {
      panelTitle = 'Edit ' + panel.taskKind + ' fields'
      panelContent = <><CustomTaskEditor key={key} kind={panel.taskKind} row={selected} inputs={inputs} catalog={catalog}
        onInput={input} onChange={(rowKey, changed) => updateTask(panel.taskKind, rowKey, changed)} />
        <div className="wizard-panel-actions"><button type="button" className="mc-button" onClick={close}>Done</button></div></>
    }
  } else if (panel?.kind === 'prerequisites') {
    panelTitle = 'Choose prerequisites'
    panelContent = <PrerequisitePicker draft={draft} sections={sections} textContext={textContext} epoch={epoch} blocked={blocked} onToggle={selectPrerequisite} />
  }
  const textPanel = panel?.kind === 'detail' && textFields.some(({ field }) => field === panel.field)

  return <WizardDialog title={quest ? 'Edit quest' : 'Create quest'}
    titleAside={<span className="wizard-chapter"><QuestNodeIcon display={draft.display} epoch={epoch} revision={textContext.assetRevision} />
      <ComponentText value={section.name} textContext={textContext} useInterfaceFont /></span>}
    stages={questStages.map(entry => ({
      ...entry, complete: visited.includes(entry.id) && !issues.some(issue => entry.id === 'review' || issue.stage === entry.id),
    }))}
    stage={stage} onNavigate={navigate} panelTitle={panelTitle}
    panel={<>
      {textFields.map(({ field, label }) => <ComponentEditor key={field} label={label} value={draft.display[field]}
        visible={panel?.kind === 'detail' && panel.field === field} docked required={field === 'title'}
        maxLiteralLength={field === 'title' ? 160 : undefined} multiline={field === 'description'}
        onChange={value => display({ [field]: value })} textContext={textContext} catalog={catalog} epoch={epoch} />)}
      {textPanel && <div className="wizard-panel-actions"><button type="button" className="mc-button" onClick={close}>Done</button></div>}
      {panelContent}
    </>}
    onClosePanel={close} onClose={onClose} onDelete={onDelete} submitLabel={quest ? 'Apply changes' : 'Create quest'}
    onSubmit={() => { if (issues.length) focus('quest-review-issues'); else onApply(draft) }}>
    <h3 id="quest-stage-heading" tabIndex={-1}>{headings[stage]}</h3>
    {stage === 'details' && <QuestDetailsStage draft={draft} inputs={inputs} textContext={textContext} epoch={epoch}
      activeTrigger={opened?.trigger} onOpen={field => open({ kind: 'detail', field }, 'quest-' + field)}
      onDisplay={display} onPosition={changePosition} />}
    {(['objective', 'reward'] as const).map(kind => stage === (kind === 'objective' ? 'objectives' : 'rewards') &&
      <TaskListEditor key={kind} kind={kind} rows={rows[kind]} inputs={inputs} catalog={catalog} epoch={epoch} activeTrigger={opened?.trigger}
        onInput={input} onChange={(key, task) => updateTask(kind, key, task)} onRemove={key => removeTask(kind, key)}
        onPick={key => { const row = rows[kind].find(entry => entry.key === key); if (row) openTask(kind, key, row.task) }}
        onChooseType={key => open({ kind: 'type', taskKind: kind, key }, key ? taskInputId(key, 'type') : 'quest-add-' + kind)} />)}
    {stage === 'review' && <QuestReviewStage draft={draft} rows={rows} sections={sections} catalog={catalog}
      textContext={textContext} epoch={epoch} issues={issues} active={panel?.kind === 'prerequisites'} dependencyError={dependencyError}
      onNavigate={navigate} onIssue={showIssue} onOpen={() => open({ kind: 'prerequisites' }, 'quest-prerequisites')}
      onRemove={id => { selectPrerequisite(id, false); focus('quest-prerequisites') }} />}
  </WizardDialog>
}
