import { useState } from 'react'
import type { Catalog, ComponentTextContext, Section } from '../types.ts'
import { newSection } from '../lib/quests.ts'
import { ComponentEditor } from './ComponentEditor.tsx'
import { Dialog } from './Dialog.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { ItemIcon } from './ItemIcon.tsx'

export function SectionEditorDialog({ section, catalog, epoch, textContext, onApply, onDelete, onClose }:
  { section?: Section; catalog: Catalog; epoch: number; textContext: ComponentTextContext; onApply: (section: Section) => void;
    onDelete?: () => void; onClose: () => void }) {
  const [draft, setDraft] = useState(() => section ? structuredClone(section) : newSection())
  const [nameValid, setNameValid] = useState(true)
  return <Dialog title={section ? 'Edit section' : 'Add section'} onClose={onClose}>
    <form onSubmit={event => { event.preventDefault(); if (nameValid) onApply(draft) }}>
      <div className="dialog-body">
        <div className="form-intro"><ItemIcon id={draft.icon} epoch={epoch} /><p>Group quests into a chapter of your quest book.</p></div>
        <ComponentEditor label="Section name" required maxLiteralLength={120} value={draft.name}
          onChange={name => setDraft(current => ({ ...current, name }))} textContext={textContext} onValidityChange={setNameValid} catalog={catalog} epoch={epoch} />
        <RegistryPicker label="Section icon" value={draft.icon} onChange={icon => setDraft(current => ({ ...current, icon }))} catalog={catalog} epoch={epoch} />
        <label className="field">Required mod <span className="muted">optional</span><input value={draft.modLoaded} placeholder="Mod ID, for example create"
          onChange={event => setDraft(current => ({ ...current, modLoaded: event.target.value }))} /></label>
        <label className="check-field"><input type="checkbox" checked={!draft.disabled} onChange={event => setDraft(current => ({ ...current, disabled: !event.target.checked }))} />Enabled in the quest book</label>
      </div>
      <footer className="dialog-footer">{onDelete && <button type="button" className="mc-button mc-button--red" onClick={onDelete}>Delete section</button>}
        <span className="spacer" /><button type="button" className="mc-button" onClick={onClose}>Cancel</button>
        <button className="mc-button mc-button--green" type="submit" disabled={!nameValid}>{section ? 'Apply changes' : 'Create section'}</button></footer>
    </form>
  </Dialog>
}
