export function EditorToolbar({ loading, saving, dirty, connected, onSave, onReload }:
  { loading: boolean; saving: boolean; dirty: boolean; connected: boolean; onSave: () => void; onReload: () => void }) {
  return <header className="editor-toolbar">
    <div className="brand-mark" aria-hidden="true">V</div><div className="editor-brand"><h1>VUE Quests</h1><span>Quest editor</span></div>
    <span className="spacer" />
    <span className={`save-status ${dirty ? 'dirty' : ''}`}>{loading ? 'Connecting…' : saving ? 'Saving…' : dirty ? 'Unsaved changes' : connected ? 'All changes saved' : 'Not connected'}</span>
    <button type="button" disabled={loading || saving} onClick={onReload}>Reload</button>
    <button type="button" className="primary" disabled={!connected || loading || saving || !dirty} onClick={onSave} title="Save changes (Ctrl+S)">Save changes</button>
  </header>
}
