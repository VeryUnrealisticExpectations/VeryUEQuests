import { useState } from 'react'
import type { Catalog, ComponentTextContext, Section } from '../types.ts'
import { newSection } from '../lib/quests.ts'
import { Dialog } from './Dialog.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { ItemIcon } from './ItemIcon.tsx'
import { LanguageEditingNotice, LocalizedTextField } from './LocalizedTextField.tsx'
import type { LocalizedTextFields, LocalizedTextUpdates } from './LocalizedTextField.tsx'

export function SectionEditorDialog({ section, catalog, epoch, textContext, localization, onApply, onDelete, onClose }:
  { section?: Section; catalog: Catalog; epoch: number; textContext: ComponentTextContext;
    localization: LocalizedTextFields<'name'>; onApply: (section: Section, updates: LocalizedTextUpdates<'name'>) => void;
    onDelete?: () => void; onClose: () => void }) {
  const [draft, setDraft] = useState(() => section ? structuredClone(section) : {
    ...newSection(), name: { translate: 'screen.vuequests.unnamed_section', fallback: 'Unnamed section' },
  })
  const [updates, setUpdates] = useState<LocalizedTextUpdates<'name'>>(() => section ? {} : { name: 'New section' })
  const [nameValid, setNameValid] = useState(true)
  const field = localization.fields.name
  const localName = updates.name === null ? undefined : updates.name ?? field.local
  const sharedSettings = localization.locale !== 'en_us'
  return <Dialog title={section ? 'Edit section' : 'Add section'} onClose={onClose}>
    <form onSubmit={event => { event.preventDefault(); if (nameValid) { onApply(draft, updates) } }}>
      <div className="dialog-body">
        <div className="form-intro"><ItemIcon id={draft.icon} epoch={epoch} /><p>Group quests into a chapter of your quest book.</p></div>
        <LanguageEditingNotice locale={localization.locale} languageName={localization.languageName} />
        <LocalizedTextField label="Section name" required maxLiteralLength={120} value={localName ?? field.inherited}
          locale={localization.locale} english={field.english} englishContext={localization.englishContext} serverText={field.server}
          canReset={!!section && localName !== undefined} onReset={() => setUpdates({ name: null })}
          onChange={name => setUpdates({ name })} textContext={textContext} onValidityChange={setNameValid} catalog={catalog} epoch={epoch} />
        <fieldset className="locale-shared-fields">
          {sharedSettings && <legend>Shared settings</legend>}
          <RegistryPicker label="Section icon" value={draft.icon} onChange={icon => setDraft(current => ({ ...current, icon }))} catalog={catalog} epoch={epoch} />
          <label className="field">Required mod <span className="muted">optional</span><input value={draft.modLoaded} placeholder="Mod ID, for example create"
            onChange={event => setDraft(current => ({ ...current, modLoaded: event.target.value }))} /></label>
          <label className="check-field"><input type="checkbox" checked={!draft.disabled} onChange={event => setDraft(current => ({ ...current, disabled: !event.target.checked }))} />Enabled in the quest book</label>
        </fieldset>
      </div>
      <footer className="dialog-footer">{onDelete && <button type="button" className="mc-button mc-button--red" onClick={onDelete}>Delete section</button>}
        <span className="spacer" /><button type="button" className="mc-button" onClick={onClose}>Cancel</button>
        <button className="mc-button mc-button--green" type="submit" disabled={!nameValid}>{section ? 'Apply changes' : 'Create section'}</button></footer>
    </form>
  </Dialog>
}
