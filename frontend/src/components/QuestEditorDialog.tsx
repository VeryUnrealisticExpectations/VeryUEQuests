import { useCallback, useState } from 'react'
import type { Catalog, ComponentTextContext, Position, Quest, Section } from '../types.ts'
import { DEFAULT_QUEST_BACKGROUND, dependencyProblem, newQuest, updateQuest } from '../lib/quests.ts'
import { ComponentEditor } from './ComponentEditor.tsx'
import { ComponentText } from './ComponentText.tsx'
import { Dialog } from './Dialog.tsx'
import { QuestBackgroundPicker } from './QuestBackgroundPicker.tsx'
import { QuestNodeIcon } from './QuestNodeIcon.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { TaskListEditor } from './TaskListEditor.tsx'
import { taskEditorId, taskProblem } from '../lib/taskEditor.ts'

export function QuestEditorDialog({ quest, section, sections, position, catalog, epoch, textContext, onApply, onDelete, onClose }:
  { quest?: Quest; section: Section; sections: Section[]; position: Position; catalog: Catalog; epoch: number; textContext: ComponentTextContext;
    onApply: (quest: Quest) => void; onDelete?: () => void; onClose: () => void }) {
  const [draft, setDraft] = useState(() => quest ? structuredClone(quest) : newQuest(section.section_id, position))
  const [error, setError] = useState('')
  const [activeTask, setActiveTask] = useState<string>()
  const collapseTask = useCallback((id: string) => setActiveTask(current => current === id ? undefined : current), [])
  const [validity, setValidity] = useState({ title: true, subtitle: true, description: true })
  const titleValidity = useCallback((valid: boolean) => setValidity(current => current.title === valid ? current : { ...current, title: valid }), [])
  const subtitleValidity = useCallback((valid: boolean) => setValidity(current => current.subtitle === valid ? current : { ...current, subtitle: valid }), [])
  const descriptionValidity = useCallback((valid: boolean) => setValidity(current => current.description === valid ? current : { ...current, description: valid }), [])
  const display = (change: Partial<Quest['display']>) => setDraft(current => ({ ...current, display: { ...current.display, ...change } }))
  const textValid = Object.values(validity).every(Boolean)
  return <Dialog title={quest ? 'Edit quest' : 'Add quest'} size="wide" onClose={onClose}>
    <form onSubmit={event => {
      event.preventDefault()
      if (!textValid) { setError('Check the quest text fields before applying changes.'); return }
      for (const kind of ['objective', 'reward'] as const) {
        const tasks = kind === 'objective' ? draft.objectives : draft.rewards
        for (let index = 0; index < tasks.length; index++) {
          const problem = taskProblem(tasks[index])
          if (problem) { setError(`${kind === 'objective' ? 'Objective' : 'Reward'} ${index + 1}: ${problem}`); setActiveTask(taskEditorId(kind, tasks[index], index)); return }
        }
      }
      const updated = quest ? updateQuest(sections, draft.quest_id, () => draft)
        : sections.map(entry => entry.section_id === section.section_id ? { ...entry, quests: [...entry.quests, draft] } : entry)
      const problem = dependencyProblem(updated)
      if (problem) setError(problem)
      else onApply(draft)
    }}>
      <div className="dialog-body quest-form">
        <div className="form-intro"><QuestNodeIcon display={draft.display} epoch={epoch} revision={textContext.assetRevision} />
          <p><ComponentText value={section.name} textContext={textContext} /></p></div>
        <div className="form-grid quest-text-fields">
          <ComponentEditor label="Quest title" required maxLiteralLength={160} value={draft.display.title} onChange={title => display({ title })}
            textContext={textContext} onValidityChange={titleValidity} catalog={catalog} epoch={epoch} />
          <ComponentEditor label="Subtitle" value={draft.display.subtitle} onChange={subtitle => display({ subtitle })}
            textContext={textContext} onValidityChange={subtitleValidity} catalog={catalog} epoch={epoch} />
        </div>
        <ComponentEditor label="Description" multiline value={draft.display.description} onChange={description => display({ description })}
          textContext={textContext} onValidityChange={descriptionValidity} catalog={catalog} epoch={epoch} />
        <RegistryPicker label="Quest icon" value={draft.display.icon} onChange={icon => display({ icon })} catalog={catalog} epoch={epoch} />
        <QuestBackgroundPicker value={draft.display.background_sprite ?? DEFAULT_QUEST_BACKGROUND}
          backgrounds={textContext.questBackgrounds ?? []} revision={textContext.assetRevision}
          onChange={id => display({ background_sprite: id === DEFAULT_QUEST_BACKGROUND ? undefined : id })} />
        <div className="form-grid">
          <label className="field">Tooltip<input value={draft.display.tooltip} onChange={event => display({ tooltip: event.target.value })} /></label>
          <div className="field-row"><label className="field">X<input type="number" required step={1} value={draft.position.x}
            onChange={event => setDraft(current => ({ ...current, position: { ...current.position, x: Number(event.target.value) } }))} /></label>
          <label className="field">Y<input type="number" required step={1} value={draft.position.y}
            onChange={event => setDraft(current => ({ ...current, position: { ...current.position, y: Number(event.target.value) } }))} /></label></div>
        </div>
        <div className="task-columns">
          <TaskListEditor kind="objective" tasks={draft.objectives} onChange={objectives => setDraft(current => ({ ...current, objectives }))}
            catalog={catalog} epoch={epoch} textContext={textContext} activeTask={activeTask} onActivate={setActiveTask} onCollapse={collapseTask} />
          <TaskListEditor kind="reward" tasks={draft.rewards} onChange={rewards => setDraft(current => ({ ...current, rewards }))}
            catalog={catalog} epoch={epoch} textContext={textContext} activeTask={activeTask} onActivate={setActiveTask} onCollapse={collapseTask} />
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
      </div>
      <footer className="dialog-footer">{onDelete && <button className="mc-button mc-button--red" type="button" onClick={onDelete}>Delete quest</button>}
        <span className="spacer" /><button type="button" className="mc-button" onClick={onClose}>Cancel</button>
        <button className="mc-button mc-button--green" type="submit" disabled={!textValid}>{quest ? 'Apply changes' : 'Create quest'}</button></footer>
    </form>
  </Dialog>
}
