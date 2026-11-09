import { useState } from 'react'
import type { Catalog, ComponentTextContext, Quest, Section } from '../types.ts'
import { plainComponentText } from '../lib/components.ts'
import { DEFAULT_QUEST_BACKGROUND } from '../lib/quests.ts'
import type { QuestIssue, QuestStage, TaskRows } from '../lib/questWizard.ts'
import { ComponentText } from './ComponentText.tsx'
import { ItemIcon } from './ItemIcon.tsx'
import { QuestNodeIcon } from './QuestNodeIcon.tsx'
import { TaskSummary } from './TaskListEditor.tsx'

interface PrerequisiteProps {
  draft: Quest
  sections: Section[]
  textContext: ComponentTextContext
  epoch: number
}

export function QuestReviewStage({ draft, sections, textContext, epoch, rows, catalog, issues, active, dependencyError, onNavigate,
  onIssue, onOpen, onRemove }: PrerequisiteProps & {
  rows: TaskRows; catalog: Catalog; issues: QuestIssue[]; active: boolean; dependencyError: string;
  onNavigate: (stage: QuestStage) => void; onIssue: (issue: QuestIssue) => void;
  onOpen: () => void; onRemove: (id: string) => void;
}) {
  return <>
    {!!issues.length && <section id="quest-review-issues" className="wizard-review-issues" tabIndex={-1} aria-labelledby="quest-review-issues-heading">
      <h4 id="quest-review-issues-heading">Needs attention</h4>
      <ul>{issues.map(issue => <li key={issue.target + issue.message}>
        <button type="button" className="wizard-issue-link" onClick={() => onIssue(issue)}>{issue.message}</button>
      </li>)}</ul>
    </section>}
    <section className="wizard-review-section">
      <div className="wizard-review-heading"><h4>Details</h4>
        <button type="button" className="mc-button mc-button--small" onClick={() => onNavigate('details')}>Edit details</button>
      </div>
      <div className="wizard-review-quest">
        <QuestNodeIcon display={draft.display} epoch={epoch} revision={textContext.assetRevision} />
        <div><strong><ComponentText value={draft.display.title} textContext={textContext} useInterfaceFont /></strong>
          <p><ComponentText value={draft.display.subtitle} textContext={textContext} useInterfaceFont /></p></div>
      </div>
      <dl className="wizard-review-details">
        <dt>Description</dt><dd><ComponentText value={draft.display.description || '—'} textContext={textContext} useInterfaceFont /></dd>
        <dt>Tooltip</dt><dd>{draft.display.tooltip || '—'}</dd>
        <dt>Icon</dt><dd>{draft.display.icon}</dd>
        <dt>Background</dt><dd>{draft.display.background_sprite ?? DEFAULT_QUEST_BACKGROUND}</dd>
        <dt>Position</dt><dd>X {draft.position.x}, Y {draft.position.y}</dd>
      </dl>
    </section>
    {(['objective', 'reward'] as const).map(kind => {
      const stage = kind === 'objective' ? 'objectives' : 'rewards'
      return <section className="wizard-review-section" key={kind}>
        <div className="wizard-review-heading"><h4>{kind === 'objective' ? 'Objectives' : 'Rewards'}</h4>
          <button type="button" className="mc-button mc-button--small" onClick={() => onNavigate(stage)}>Edit {stage}</button>
        </div>
        <TaskSummary kind={kind} rows={rows[kind]} catalog={catalog} epoch={epoch} />
      </section>
    })}
    <section className="wizard-review-section">
      <h4>Prerequisites</h4>
      {!draft.dependencies.length && <p className="wizard-helper">No prerequisites selected.</p>}
      {!!draft.dependencies.length && <ul className="wizard-prerequisite-chips">
        {draft.dependencies.map((id, index) => {
          const section = sections.find(entry => entry.quests.some(quest => quest.quest_id === id))
          const quest = section?.quests.find(quest => quest.quest_id === id)
          const name = quest ? plainComponentText(quest.display.title, textContext) || id : 'Missing quest (' + id + ')'
          return <li key={id + ':' + index}>
            {quest && <ItemIcon id={quest.display.icon} epoch={epoch} />}
            <span>{name}{section && <small>{plainComponentText(section.name, textContext)}</small>}</span>
            <button type="button" className="mc-button mc-button--icon mc-button--small" aria-label={'Remove prerequisite ' + name} onClick={() => onRemove(id)}>×</button>
          </li>
        })}
      </ul>}
      {dependencyError && <p className="form-error" role="alert">{dependencyError}</p>}
      <button type="button" id="quest-prerequisites" className="mc-button wizard-add" aria-expanded={active}
        aria-controls="quest-wizard-panel" onClick={onOpen}>+ Add prerequisite</button>
    </section>
  </>
}

export function PrerequisitePicker({ draft, sections, textContext, epoch, blocked, onToggle }:
  PrerequisiteProps & { blocked: Set<string>; onToggle: (id: string, checked: boolean) => void }) {
  const [query, setQuery] = useState('')
  const needle = query.trim().toLowerCase()
  const entries = sections.flatMap(section => {
    const chapter = plainComponentText(section.name, textContext)
    return section.quests.filter(quest => quest.quest_id !== draft.quest_id).map(quest => ({
      quest, chapter, name: plainComponentText(quest.display.title, textContext) || quest.quest_id,
    }))
  }).filter(({ quest, chapter, name }) => (name + ' ' + chapter + ' ' + quest.quest_id).toLowerCase().includes(needle))
  return <>
    <p className="wizard-helper">Players must complete all selected quests first.</p>
    <label className="field"><span className="sr-only">Search existing quests</span>
      <input type="search" data-panel-focus placeholder="Search existing quests" value={query} onChange={event => setQuery(event.target.value)} />
    </label>
    <div className="wizard-search-results">
      {entries.map(({ quest, chapter, name }) => {
        const id = quest.quest_id
        const checked = draft.dependencies.includes(id)
        const cyclic = blocked.has(id) && !checked
        return <label key={id} className="wizard-prerequisite-option" data-disabled={cyclic || undefined}>
          <input type="checkbox" checked={checked} disabled={cyclic} onChange={event => onToggle(id, event.target.checked)} />
          <ItemIcon id={quest.display.icon} epoch={epoch} />
          <span><strong>{name}</strong><small>{chapter}</small>{cyclic && <small>This would create a cycle.</small>}</span>
        </label>
      })}
      {!entries.length && <p className="wizard-helper">No matching quests.</p>}
    </div>
    <small className="wizard-helper" role="status">{draft.dependencies.length} selected</small>
  </>
}
