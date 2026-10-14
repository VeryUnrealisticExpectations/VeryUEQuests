import { gameTextureUrl } from '../lib/terrain.ts'

export function EditorToolbar({ loading, saving, dirty, connected, notice, assetRevision, onSave, onReload }:
  { loading: boolean; saving: boolean; dirty: boolean; connected: boolean; notice: string; assetRevision: string; onSave: () => void; onReload: () => void }) {
  return <header className="editor-toolbar">
    <div className="toolbar-terrain" aria-hidden="true">
      <div className="toolbar-grass" style={assetRevision ? { backgroundImage: `url("${gameTextureUrl('grass_block_side', assetRevision)}")` } : undefined} />
      <div className="toolbar-dirt" style={assetRevision ? { backgroundImage: `url("${gameTextureUrl('dirt', assetRevision)}")` } : undefined} />
    </div>
    <div className="toolbar-controls">
      <span className="spacer" />
      <span className={`save-status ${dirty ? 'dirty' : ''}`} role="status">{loading ? 'Connecting…' : saving ? 'Saving…' : dirty ? 'Unsaved changes' : connected ? notice || 'All changes saved' : 'Not connected'}</span>
      <button type="button" className="mc-button mc-button--gray" disabled={loading || saving} onClick={onReload}>Reload</button>
      <button type="button" className="mc-button mc-button--green" disabled={!connected || loading || saving || !dirty} onClick={onSave} title="Save changes (Ctrl+S)">Save changes</button>
    </div>
  </header>
}
