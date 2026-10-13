import { useState } from 'react'
import type { Catalog, Section } from '../types.ts'
import { newSection } from '../lib/quests.ts'
import { Dialog } from './Dialog.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { ItemIcon } from './ItemIcon.tsx'

export function SectionEditorDialog({ section, catalog, epoch, onApply, onDelete, onClose }:
  { section?: Section; catalog: Catalog; epoch: number; onApply: (section: Section) => void; onDelete?: () => void; onClose: () => void }) {
  const [draft, setDraft] = useState(() => section ? structuredClone(section) : newSection())
  return <Dialog title={section ? 'Edit section' : 'Add section'} onClose={onClose}>
    <form onSubmit={event => { event.preventDefault(); onApply(draft) }}>
      <div className="dialog-body">
        <div className="form-intro"><ItemIcon id={draft.icon} epoch={epoch} /><p>Group quests into a chapter of your quest book.</p></div>
        <label className="field">Section name<input required maxLength={120} value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} /></label>
        <RegistryPicker label="Section icon" value={draft.icon} onChange={icon => setDraft({ ...draft, icon })} catalog={catalog} epoch={epoch} allowBlocks />
        <label className="field">Required mod <span className="muted">optional</span><input value={draft.modLoaded} placeholder="Mod ID, for example create"
          onChange={event => setDraft({ ...draft, modLoaded: event.target.value })} /></label>
        <label className="check-field"><input type="checkbox" checked={!draft.disabled} onChange={event => setDraft({ ...draft, disabled: !event.target.checked })} />Enabled in the quest book</label>
      </div>
      <footer className="dialog-footer">{onDelete && <button type="button" className="danger" onClick={onDelete}>Delete section</button>}
        <span className="spacer" /><button type="button" onClick={onClose}>Cancel</button><button className="primary" type="submit">{section ? 'Apply changes' : 'Create section'}</button></footer>
    </form>
  </Dialog>
}
