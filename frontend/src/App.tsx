import type {CSSProperties} from 'react'
import {useEffect, useMemo, useState, useSyncExternalStore} from 'react'
import type {Position} from './types.ts'
import {useQuestEditor} from './hooks/useQuestEditor.ts'
import {useEditorResources} from './hooks/useEditorResources.ts'
import {allQuests, moveSection, removeQuests, sortSections, toggleQuestDependency, updateQuest} from './lib/quests.ts'
import {localizeSections, questLanguageFields, sectionLanguageField} from './lib/questLanguages.ts'
import {gameTextureUrl} from './lib/terrain.ts'
import {rendererEpoch, setRendererAssets, subscribeRenderer} from './lib/itemRenderer.ts'
import {EditorToolbar} from './components/EditorToolbar.tsx'
import {SectionSidebar} from './components/SectionSidebar.tsx'
import {QuestCanvas, type QuestReveal} from './components/QuestCanvas.tsx'
import {SectionEditorDialog} from './components/SectionEditorDialog.tsx'
import {QuestEditorDialog} from './components/QuestEditorDialog.tsx'

type Editing = { kind: 'section'; id?: string } | { kind: 'quest'; id?: string; position: Position }

export default function App() {
    const editor = useQuestEditor()
    const [selectedSection, setSelectedSection] = useState<string>()
    const [selectedQuest, setSelectedQuest] = useState<string>()
    const [revealQuest, setRevealQuest] = useState<QuestReveal>()
    const [editing, setEditing] = useState<Editing>()
    const assetEpoch = useSyncExternalStore(subscribeRenderer, rendererEpoch, rendererEpoch)
    const snapshot = editor.snapshot
    const textContext = useEditorResources(snapshot?.assetRevision ?? '', !!snapshot, editor.locale)
    const englishContext = useEditorResources(snapshot?.assetRevision ?? '', !!snapshot && editor.locale !== 'en_us', 'en_us')
    const sections = useMemo(() => sortSections(localizeSections(snapshot?.sections ?? [], editor.activeLanguage, editor.englishLanguage), textContext),
        [snapshot, editor.activeLanguage, editor.englishLanguage, textContext])
    const section = sections.find(entry => entry.section_id === selectedSection) ?? sections[0]
    const editingSection = editing?.kind === 'section' ? snapshot?.sections.find(entry => entry.section_id === editing.id) : undefined
    const editingQuest = editing?.kind === 'quest' && snapshot ? allQuests(snapshot.sections).find(entry => entry.quest_id === editing.id) : undefined
    const localization = {
        locale: editor.locale,
        languageName: editor.languages.find(language => language.code === editor.locale)?.name ?? editor.locale,
        englishContext
    }
    const busy = editor.loading || editor.saving || editor.languagesLoading
    const goToQuest = (id: string) => {
        for (const targetSection of sections) {
            const target = targetSection.quests.find(quest => quest.quest_id === id)
            if (target) {
                setSelectedSection(targetSection.section_id)
                setSelectedQuest(id)
                setRevealQuest({id, position: target.position})
                setEditing(undefined)
                return
            }
        }
    }
    useEffect(() => {
        const revision = textContext.assetRevision
        if (!revision) {
            return
        }
        setRendererAssets(revision, snapshot?.minecraftVersion ?? '26.3')
    }, [textContext.assetRevision, snapshot?.minecraftVersion])
    useEffect(() => {
        const shortcut = (event: KeyboardEvent) => {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
                event.preventDefault()
                if (!document.querySelector('dialog[open]')) {
                    void editor.save()
                }
            }
        }
        window.addEventListener('keydown', shortcut)
        return () => window.removeEventListener('keydown', shortcut)
    }, [editor])
    return <div className="editor-app" style={{
        '--grass-texture': `url("${gameTextureUrl('grass_block_side', textContext.assetRevision)}")`,
        '--dirt-texture': `url("${gameTextureUrl('dirt', textContext.assetRevision)}")`,
        '--stone-texture': `url("${gameTextureUrl('stone', textContext.assetRevision)}")`,
    } as CSSProperties}>
        <EditorToolbar loading={editor.loading} saving={editor.saving} dirty={editor.dirty} connected={!!snapshot}
                       notice={editor.notice} assetRevision={textContext.assetRevision}
                       locale={editor.locale} languages={editor.languages} languagesLoading={editor.languagesLoading}
                       onSelectLanguage={(code, create) => void editor.selectLanguage(code, create)}
                       onSave={() => void editor.save()} onReload={() => {
            if (!editor.dirty || window.confirm('Discard unsaved changes and reload quests and translations?')) {
                void editor.reload()
            }
        }}/>
        {editor.error && <div className="status-banner error" role="alert">
            <span>{editor.error}{!snapshot ? ' Start a Minecraft world to connect to the editor.' : ''}</span>
            <button className="mc-button mc-button--small mc-button--icon" type="button"
                    onClick={() => editor.setError('')} aria-label="Dismiss error">×
            </button>
        </div>}
        <div className="editor-workspace">
            <SectionSidebar sections={sections} selected={section?.section_id} epoch={assetEpoch}
                            textContext={textContext} disabled={busy || !snapshot}
                            onSelect={id => {
                                setSelectedSection(id);
                                setSelectedQuest(undefined)
                            }} onAdd={() => setEditing({kind: 'section'})}
                            onEdit={id => setEditing({kind: 'section', id})}
                            onMove={(id, targetId, position) => {
                                setSelectedSection(section?.section_id)
                                const ordered = moveSection(sections, id, targetId, position, textContext)
                                const priorities = new Map(ordered.map(entry => [entry.section_id, entry.priority]))
                                editor.edit(entries => entries.map(entry => ({
                                    ...entry,
                                    priority: priorities.get(entry.section_id) ?? entry.priority
                                })))
                            }}/>
            <QuestCanvas key={section?.section_id ?? 'empty'} section={section} sections={sections}
                         selected={selectedQuest} reveal={revealQuest} onReveal={() => setRevealQuest(undefined)}
                         epoch={assetEpoch} assetRevision={textContext.assetRevision} textContext={textContext}
                         disabled={busy || !snapshot}
                         onSelect={setSelectedQuest}
                         onEdit={id => setEditing({kind: 'quest', id, position: {x: 0, y: 0}})}
                         onAdd={position => setEditing({kind: 'quest', position})}
                         onMove={(id, position) => editor.edit(entries => updateQuest(entries, id, quest => ({
                             ...quest,
                             position
                         })))}
                         onConnect={(from, to) => {
                             try {
                                 const next = toggleQuestDependency(snapshot?.sections ?? [], from, to);
                                 editor.edit(() => next)
                             } catch (failure) {
                                 editor.setError(failure instanceof Error ? failure.message : 'Could not create the dependency.')
                             }
                         }}/>
        </div>
        {snapshot && editing?.kind === 'section' &&
            <SectionEditorDialog catalog={snapshot.catalog} epoch={assetEpoch} textContext={textContext}
                                 section={editingSection} localization={{
                ...localization, fields: {
                    name: sectionLanguageField(editingSection, editor.activeLanguage,
                        editor.locale === 'en_us' ? undefined : editor.englishLanguage)
                }
            }} onClose={() => setEditing(undefined)}
                                 onApply={(updated, text) => {
                                     editor.edit(entries => entries.some(entry => entry.section_id === updated.section_id)
                                         ? entries.map(entry => entry.section_id === updated.section_id ? updated : entry)
                                         : [...sections.map(({section_id}, priority) => ({
                                             ...entries.find(entry => entry.section_id === section_id)!,
                                             priority
                                         })),
                                             {...updated, priority: sections.length}])
                                     editor.editSectionTranslation(updated.section_id, text)
                                     setSelectedSection(updated.section_id);
                                     setSelectedQuest(undefined);
                                     setEditing(undefined)
                                 }} onDelete={editing.id ? () => {
                if (!window.confirm('Delete this section and all of its quests?')) {
                    return
                }
                const id = editing.id!
                const ids = new Set(sections.find(entry => entry.section_id === id)!.quests.map(quest => quest.quest_id))
                editor.edit(entries => removeQuests(entries.filter(entry => entry.section_id !== id), ids))
                setSelectedSection(undefined);
                setSelectedQuest(undefined);
                setEditing(undefined)
            } : undefined}/>}
        {snapshot && section && editing?.kind === 'quest' &&
            <QuestEditorDialog section={section} sections={sections} position={editing.position}
                               quest={editingQuest} localization={{
                ...localization, fields: questLanguageFields(editingQuest, editor.activeLanguage,
                    editor.locale === 'en_us' ? undefined : editor.englishLanguage)
            }} catalog={snapshot.catalog} epoch={assetEpoch} textContext={textContext}
                               onClose={() => setEditing(undefined)}
                               onGoToQuest={goToQuest}
                               onApply={(quest, text) => {
                                   editor.edit(entries => allQuests(entries).some(entry => entry.quest_id === quest.quest_id)
                                       ? updateQuest(entries, quest.quest_id, () => quest) : entries.map(entry => entry.section_id === quest.section_id ? {
                                           ...entry,
                                           quests: [...entry.quests, quest]
                                       } : entry))
                                   editor.editQuestTranslation(quest.quest_id, text)
                                   setSelectedQuest(quest.quest_id);
                                   setEditing(undefined)
                               }} onDelete={editing.id ? () => {
                if (!window.confirm('Delete this quest and remove its dependency connections?')) {
                    return
                }
                editor.edit(entries => removeQuests(entries, new Set([editing.id!])))
                setSelectedQuest(undefined);
                setEditing(undefined)
            } : undefined}/>}
    </div>
}
