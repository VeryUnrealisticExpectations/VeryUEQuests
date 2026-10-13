import { useState } from 'react'
import type { Catalog, Position, Quest, Section } from '../types.ts'
import { allQuests, dependencyProblem, newQuest, updateQuest } from '../lib/quests.ts'
import { Dialog } from './Dialog.tsx'
import { ItemIcon } from './ItemIcon.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { TaskListEditor } from './TaskListEditor.tsx'

export function QuestEditorDialog({ quest, section, sections, position, catalog, epoch, onApply, onDelete, onClose }:
  { quest?: Quest; section: Section; sections: Section[]; position: Position; catalog: Catalog; epoch: number;
    onApply: (quest: Quest) => void; onDelete?: () => void; onClose: () => void }) {
  const [draft, setDraft] = useState(() => quest ? structuredClone(quest) : newQuest(section.section_id, position))
  const [error, setError] = useState('')
  const display = (change: Partial<Quest['display']>) => setDraft({ ...draft, display: { ...draft.display, ...change } })
  const candidates = allQuests(sections).filter(candidate => candidate.quest_id !== draft.quest_id)
  return <Dialog title={quest ? 'Edit quest' : 'Add quest'} onClose={onClose}>
    <form onSubmit={event => {
      event.preventDefault()
      const updated = quest ? updateQuest(sections, draft.quest_id, () => draft)
        : sections.map(entry => entry.section_id === section.section_id ? { ...entry, quests: [...entry.quests, draft] } : entry)
      const problem = dependencyProblem(updated)
      if (problem) setError(problem)
      else onApply(draft)
    }}>
      <div className="dialog-body quest-form">
        <div className="form-intro"><ItemIcon id={draft.display.icon} epoch={epoch} /><p>{section.name}</p></div>
        <div className="form-grid">
          <label className="field">Quest title<input required maxLength={160} value={draft.display.title} onChange={event => display({ title: event.target.value })} /></label>
          <label className="field">Subtitle<input value={draft.display.subtitle} onChange={event => display({ subtitle: event.target.value })} /></label>
        </div>
        <label className="field">Description<textarea rows={3} value={draft.display.description} onChange={event => display({ description: event.target.value })} /></label>
        <RegistryPicker label="Quest icon" value={draft.display.icon} onChange={icon => display({ icon })} catalog={catalog} epoch={epoch} allowBlocks />
        <div className="form-grid">
          <label className="field">Tooltip<input value={draft.display.tooltip} onChange={event => display({ tooltip: event.target.value })} /></label>
          <div className="field-row"><label className="field">X<input type="number" required step={1} value={draft.position.x}
            onChange={event => setDraft({ ...draft, position: { ...draft.position, x: Number(event.target.value) } })} /></label>
          <label className="field">Y<input type="number" required step={1} value={draft.position.y}
            onChange={event => setDraft({ ...draft, position: { ...draft.position, y: Number(event.target.value) } })} /></label></div>
        </div>
        <section className="prerequisite-editor"><h3>Complete first</h3><p className="muted">The selected quests must be completed before this quest.</p>
          <div className="prerequisite-list">{candidates.map(candidate => <label key={candidate.quest_id} className="check-field">
            <input type="checkbox" checked={draft.dependencies.includes(candidate.quest_id)} onChange={event => setDraft({ ...draft,
              dependencies: event.target.checked ? [...draft.dependencies, candidate.quest_id] : draft.dependencies.filter(id => id !== candidate.quest_id) })} />
            <span>{candidate.display.title}<small>{sections.find(entry => entry.section_id === candidate.section_id)?.name}</small></span>
          </label>)}{!candidates.length && <p className="muted">Add another quest to create a dependency.</p>}</div>
        </section>
        <TaskListEditor kind="objective" tasks={draft.objectives} onChange={objectives => setDraft({ ...draft, objectives })} catalog={catalog} epoch={epoch} />
        <TaskListEditor kind="reward" tasks={draft.rewards} onChange={rewards => setDraft({ ...draft, rewards })} catalog={catalog} epoch={epoch} />
        {error && <p className="form-error" role="alert">{error}</p>}
      </div>
      <footer className="dialog-footer">{onDelete && <button className="danger" type="button" onClick={onDelete}>Delete quest</button>}
        <span className="spacer" /><button type="button" onClick={onClose}>Cancel</button><button className="primary" type="submit">{quest ? 'Apply changes' : 'Create quest'}</button></footer>
    </form>
  </Dialog>
}
