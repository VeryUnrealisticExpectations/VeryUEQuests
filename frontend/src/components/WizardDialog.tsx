import type { ReactNode } from 'react'
import { Dialog } from './Dialog.tsx'

export interface WizardStage<Id extends string> { id: Id; label: string; complete?: boolean }

interface WizardDialogProps<Id extends string> {
  title: string
  titleAside?: ReactNode
  stages: readonly WizardStage<Id>[]
  stage: Id
  onNavigate: (stage: Id) => void
  children: ReactNode
  panelTitle?: string
  panel: ReactNode
  onClosePanel: () => void
  onClose: () => void
  onDelete?: () => void
  submitLabel: string
  onSubmit: () => void
}

export function WizardDialog<Id extends string>({ title, titleAside, stages, stage, onNavigate, children, panelTitle, panel,
  onClosePanel, onClose, onDelete, submitLabel, onSubmit }: WizardDialogProps<Id>) {
  const index = stages.findIndex(entry => entry.id === stage)
  const previous = stages[index - 1]
  const next = stages[index + 1]
  return <Dialog title={title} titleAside={titleAside} size="wizard" onClose={onClose} onEscape={panelTitle ? onClosePanel : onClose}>
    <form className="quest-wizard" noValidate onSubmit={event => {
      event.preventDefault()
      if (!next && (event.nativeEvent as SubmitEvent).submitter?.id === 'quest-wizard-submit') onSubmit()
    }} onKeyDown={event => {
      if (event.defaultPrevented) return
      if (event.key === 'Escape' && panelTitle) {
        event.preventDefault()
        event.stopPropagation()
        onClosePanel()
      } else if (event.key === 'Enter' && event.target instanceof HTMLInputElement) event.preventDefault()
    }}>
      <nav className="wizard-stepper" aria-label="Quest stages">
        <ol>{stages.map((entry, number) => <li key={entry.id} data-complete={entry.id !== stage && entry.complete || undefined}>
          <button type="button" className="wizard-step" aria-current={entry.id === stage ? 'step' : undefined}
            aria-label={entry.label + (entry.complete && entry.id !== stage ? ', complete' : '')} onClick={() => onNavigate(entry.id)}>
            <span className="wizard-step-number" aria-hidden="true">{number + 1}</span><span>{entry.label}</span>
          </button>
        </li>)}</ol>
      </nav>
      <div className="dialog-body wizard-body">
        <div className="wizard-main">{children}</div>
        <div className="wizard-side" data-open={panelTitle ? '' : undefined}>
          <aside id="quest-wizard-panel" aria-labelledby="quest-wizard-panel-title" hidden={!panelTitle}>
            <div className="wizard-panel-header">
              <h3 id="quest-wizard-panel-title" tabIndex={-1}>{panelTitle}</h3>
              <button type="button" className="mc-button mc-button--icon mc-button--small" aria-label="Close panel" onClick={onClosePanel}>×</button>
            </div>
            {panel}
          </aside>
        </div>
      </div>
      <footer className="dialog-footer">
        {onDelete && <button className="mc-button mc-button--red" type="button" onClick={onDelete}>Delete quest</button>}
        <button type="button" className="mc-button" onClick={onClose}>Cancel</button>
        <span className="spacer" />
        {previous && <button type="button" className="mc-button" onClick={() => onNavigate(previous.id)}>Back</button>}
        {/* Keep the clicked navigation button separate from the new submit button through default activation. */}
        {next
          ? <button key="next" type="button" className="mc-button mc-button--green" onClick={() => onNavigate(next.id)}>Next: {next.label}</button>
          : <button key="submit" id="quest-wizard-submit" type="submit" className="mc-button mc-button--green">{submitLabel}</button>}
      </footer>
    </form>
  </Dialog>
}
