import type { ComponentTextContext, Quest } from '../types.ts'
import { hasComponentContent } from '../lib/components.ts'
import { DEFAULT_QUEST_BACKGROUND } from '../lib/quests.ts'
import { integerInput } from '../lib/questWizard.ts'
import type { InputDrafts, QuestTextField } from '../lib/questWizard.ts'
import { ComponentText } from './ComponentText.tsx'
import { ItemIcon } from './ItemIcon.tsx'
import { QuestBackgroundSprite } from './QuestBackgroundSprite.tsx'

export function QuestDetailsStage({ draft, inputs, textContext, epoch, activeTrigger, onOpen, onDisplay, onPosition }:
  { draft: Quest; inputs: InputDrafts; textContext: ComponentTextContext; epoch: number; activeTrigger?: string;
    onOpen: (field: QuestTextField | 'icon' | 'background') => void; onDisplay: (change: Partial<Quest['display']>) => void;
    onPosition: (axis: 'x' | 'y', value: string) => void }) {
  return <>
    {(['title', 'subtitle', 'description'] as const).map(field => {
      const label = field === 'title' ? 'Quest title' : field === 'subtitle' ? 'Subtitle' : 'Description'
      const id = 'quest-' + field
      return <div className="field" key={field}>
        <span id={id + '-label'}>{label}</span>
        <button type="button" id={id} className={'wizard-text-field wizard-text-field--' + field}
          aria-labelledby={id + '-label'} aria-describedby={id + '-value'} aria-expanded={activeTrigger === id}
          aria-controls="quest-wizard-panel" onClick={() => onOpen(field)}>
          <span id={id + '-value'}>{hasComponentContent(draft.display[field])
            ? <ComponentText value={draft.display[field]} textContext={textContext} useInterfaceFont />
            : <span className="wizard-helper">Add {label.toLowerCase()}</span>}</span>
        </button>
      </div>
    })}
    <div className="wizard-appearance">
      <button type="button" id="quest-icon" className="mc-button wizard-appearance-button" aria-controls="quest-wizard-panel"
        aria-expanded={activeTrigger === 'quest-icon'} onClick={() => onOpen('icon')}>
        <ItemIcon id={draft.display.icon} epoch={epoch} /> <span>Quest icon</span> <span aria-hidden="true">›</span>
      </button>
      <button type="button" id="quest-background" className="mc-button wizard-appearance-button" aria-controls="quest-wizard-panel"
        aria-expanded={activeTrigger === 'quest-background'} onClick={() => onOpen('background')}>
        <QuestBackgroundSprite id={draft.display.background_sprite ?? DEFAULT_QUEST_BACKGROUND} revision={textContext.assetRevision} />
        <span>Quest background</span> <span aria-hidden="true">›</span>
      </button>
    </div>
    <label className="field">Tooltip<input value={draft.display.tooltip} onChange={event => onDisplay({ tooltip: event.target.value })} /></label>
    <fieldset className="wizard-position">
      <legend>Position</legend>
      <div className="wizard-appearance">
        {(['x', 'y'] as const).map(axis => {
          const id = 'quest-' + axis
          const value = inputs[id] ?? String(draft.position[axis])
          return <label key={axis} className="field">{axis.toUpperCase()}
            <input id={id} type="text" inputMode="decimal" value={value}
              aria-invalid={integerInput(value, -2_147_483_648, 2_147_483_647) === undefined}
              onChange={event => onPosition(axis, event.target.value)} />
          </label>
        })}
      </div>
    </fieldset>
  </>
}
