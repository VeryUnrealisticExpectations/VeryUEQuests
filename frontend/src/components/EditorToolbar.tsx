import { useState } from 'react'
import type { QuestEditorLanguage } from '../types.ts'
import { gameTextureUrl } from '../lib/terrain.ts'
import { Dialog } from './Dialog.tsx'

interface LanguageSelector {
  locale: string
  languages: QuestEditorLanguage[]
  onSelectLanguage: (code: string, create: boolean) => void
}

function LanguageDialog({ locale, languages, onSelectLanguage, onClose }: LanguageSelector & { onClose: () => void }) {
  const [query, setQuery] = useState('')
  const search = query.trim().toLowerCase()
  const matches = languages.filter(({ code, name }) => code.includes(search) || name.toLowerCase().includes(search))
  const translations = matches.filter(language => language.hasCatalog || language.code === 'en_us' || language.code === locale)
  const available = matches.filter(language => !language.hasCatalog && language.code !== 'en_us' && language.code !== locale)
  return <Dialog title="Quest language" onClose={onClose}>
    <div className="dialog-body language-picker">
      <label className="field">Search languages<input type="search" autoFocus value={query} placeholder="Language name or code"
        onChange={event => setQuery(event.target.value)} /></label>
      {([[translations, 'Translations', false], [available, 'Start a translation', true]] as const).map(([entries, label, create]) =>
        entries.length > 0 && <section key={label}>
          <h3>{label}</h3>
          <ul className="language-options">{entries.map(({ code, name }) => <li key={code}>
            <button type="button" className="mc-button language-option"
              aria-current={locale === code || undefined} onClick={() => { onSelectLanguage(code, create); onClose() }}>
              <span dir="auto">{name}</span><code>{code}</code>
            </button>
          </li>)}</ul>
        </section>
      )}
      {!matches.length && <p role="status">No matching Minecraft languages.</p>}
    </div>
  </Dialog>
}

export function EditorToolbar({ loading, saving, dirty, connected, notice, assetRevision, onSave, onReload,
  locale, languages, languagesLoading, onSelectLanguage }:
  LanguageSelector & { loading: boolean; saving: boolean; dirty: boolean; connected: boolean; notice: string; assetRevision: string;
    onSave: () => void; onReload: () => void; languagesLoading?: boolean }) {
  const [languageOpen, setLanguageOpen] = useState(false)
  const languageName = languages.find(language => language.code === locale)?.name ?? locale
  const languageIcon = '/api/assets/file?' + new URLSearchParams({
    path: 'assets/minecraft/textures/gui/sprites/icon/language.png', revision: assetRevision,
  })
  return <header className="editor-toolbar">
    <div className="toolbar-terrain" aria-hidden="true">
      <div className="toolbar-grass" style={assetRevision ? { backgroundImage: `url("${gameTextureUrl('grass_block_side', assetRevision)}")` } : undefined} />
      <div className="toolbar-dirt" style={assetRevision ? { backgroundImage: `url("${gameTextureUrl('dirt', assetRevision)}")` } : undefined} />
    </div>
    <div className="toolbar-controls">
      <button type="button" className="mc-button mc-button--gray toolbar-language" aria-haspopup="dialog"
        disabled={!connected || loading || saving || languagesLoading} aria-label={`Change quest language, currently ${languageName}`}
        title="Change quest language" onClick={() => setLanguageOpen(true)}>
        {assetRevision && <img width="20" height="20" alt="" src={languageIcon} />} <code>{locale}</code>
      </button>
      <span className="spacer" />
      <span className={`save-status ${dirty ? 'dirty' : ''}`} role="status">{loading ? 'Connecting…' : saving ? 'Saving…' : dirty ? 'Unsaved changes' : connected ? notice || 'All changes saved' : 'Not connected'}</span>
      <button type="button" className="mc-button mc-button--gray" disabled={loading || saving || languagesLoading} onClick={onReload}>Reload</button>
      <button type="button" className="mc-button mc-button--green" disabled={!connected || loading || saving || languagesLoading || !dirty} onClick={onSave} title="Save changes (Ctrl+S)">Save changes</button>
    </div>
    {languageOpen && <LanguageDialog locale={locale} languages={languages} onSelectLanguage={onSelectLanguage}
      onClose={() => setLanguageOpen(false)} />}
  </header>
}
