import { useEffect, useState, useSyncExternalStore } from 'react'
import type { Position } from './types.ts'
import { useQuestEditor } from './hooks/useQuestEditor.ts'
import { allQuests, connectQuests, removeQuests, updateQuest } from './lib/quests.ts'
import { rendererEpoch, setRendererAssets, subscribeRenderer } from './lib/itemRenderer.ts'
import { EditorToolbar } from './components/EditorToolbar.tsx'
import { SectionSidebar } from './components/SectionSidebar.tsx'
import { QuestCanvas } from './components/QuestCanvas.tsx'
import { SectionEditorDialog } from './components/SectionEditorDialog.tsx'
import { QuestEditorDialog } from './components/QuestEditorDialog.tsx'

type Editing = { kind: 'section'; id?: string } | { kind: 'quest'; id?: string; position: Position }

export default function App() {
  const editor = useQuestEditor()
  const [selectedSection, setSelectedSection] = useState<string>()
  const [selectedQuest, setSelectedQuest] = useState<string>()
  const [editing, setEditing] = useState<Editing>()
  const assetEpoch = useSyncExternalStore(subscribeRenderer, rendererEpoch, rendererEpoch)
  const snapshot = editor.snapshot
  const sections = snapshot?.sections ?? []
  const section = sections.find(entry => entry.section_id === selectedSection) ?? sections[0]
  const busy = editor.loading || editor.saving
  useEffect(() => {
    const revision = snapshot?.assetRevision
    if (!revision) return
    setRendererAssets(revision, snapshot.minecraftVersion)
  }, [snapshot?.assetRevision, snapshot?.minecraftVersion])
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
        event.preventDefault()
        if (!document.querySelector('dialog[open]')) void editor.save()
      }
    }
    window.addEventListener('keydown', shortcut)
    return () => window.removeEventListener('keydown', shortcut)
  }, [editor])
  return <div className="editor-app">
    <EditorToolbar loading={editor.loading} saving={editor.saving} dirty={editor.dirty} connected={!!snapshot}
      onSave={() => void editor.save()} onReload={() => { if (!editor.dirty || window.confirm('Discard unsaved changes and reload quests?')) void editor.reload() }} />
    {editor.error && <div className="status-banner error" role="alert"><span>{editor.error}{!snapshot ? ' Start a Minecraft world to connect to the editor.' : ''}</span>
      <button type="button" onClick={() => editor.setError('')} aria-label="Dismiss error">×</button></div>}
    {editor.notice && <div className="status-banner success" role="status">{editor.notice}</div>}
    <div className="editor-workspace">
      <SectionSidebar sections={sections} selected={section?.section_id} epoch={assetEpoch} disabled={busy || !snapshot}
        onSelect={id => { setSelectedSection(id); setSelectedQuest(undefined) }} onAdd={() => setEditing({ kind: 'section' })}
        onEdit={id => setEditing({ kind: 'section', id })} />
      <QuestCanvas key={section?.section_id ?? 'empty'} section={section} selected={selectedQuest} epoch={assetEpoch} disabled={busy || !snapshot}
        onSelect={setSelectedQuest} onEdit={id => setEditing({ kind: 'quest', id, position: { x: 0, y: 0 } })}
        onAdd={position => setEditing({ kind: 'quest', position })}
        onMove={(id, position) => editor.edit(entries => updateQuest(entries, id, quest => ({ ...quest, position })))}
        onConnect={(from, to) => {
          try { const next = connectQuests(sections, from, to); editor.edit(() => next) }
          catch (failure) { editor.setError(failure instanceof Error ? failure.message : 'Could not create the dependency.') }
        }} />
    </div>
    {snapshot && editing?.kind === 'section' && <SectionEditorDialog catalog={snapshot.catalog} epoch={assetEpoch}
      section={sections.find(entry => entry.section_id === editing.id)} onClose={() => setEditing(undefined)}
      onApply={updated => {
        editor.edit(entries => entries.some(entry => entry.section_id === updated.section_id)
          ? entries.map(entry => entry.section_id === updated.section_id ? updated : entry) : [...entries, updated])
        setSelectedSection(updated.section_id); setSelectedQuest(undefined); setEditing(undefined)
      }} onDelete={editing.id ? () => {
        if (!window.confirm('Delete this section and all of its quests?')) return
        const id = editing.id!
        const ids = new Set(sections.find(entry => entry.section_id === id)!.quests.map(quest => quest.quest_id))
        editor.edit(entries => removeQuests(entries.filter(entry => entry.section_id !== id), ids))
        setSelectedSection(undefined); setSelectedQuest(undefined); setEditing(undefined)
      } : undefined} />}
    {snapshot && section && editing?.kind === 'quest' && <QuestEditorDialog section={section} sections={sections} position={editing.position}
      quest={allQuests(sections).find(quest => quest.quest_id === editing.id)} catalog={snapshot.catalog} epoch={assetEpoch} onClose={() => setEditing(undefined)}
      onApply={quest => {
        editor.edit(entries => allQuests(entries).some(entry => entry.quest_id === quest.quest_id)
          ? updateQuest(entries, quest.quest_id, () => quest) : entries.map(entry => entry.section_id === quest.section_id ? { ...entry, quests: [...entry.quests, quest] } : entry))
        setSelectedQuest(quest.quest_id); setEditing(undefined)
      }} onDelete={editing.id ? () => {
        if (!window.confirm('Delete this quest and remove its dependency connections?')) return
        editor.edit(entries => removeQuests(entries, new Set([editing.id!])))
        setSelectedQuest(undefined); setEditing(undefined)
      } : undefined} />}
  </div>
}
