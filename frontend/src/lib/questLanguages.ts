import type { ChatComponent, Quest, QuestLanguageCatalog, QuestLanguageText, QuestLocaleField, Section } from '../types.ts'
import { hasComponentContent } from './components.ts'

type VisibleCatalog = Pick<QuestLanguageCatalog, 'assets' | 'server'> & { edits: QuestLanguageText }

const questFields = ['title', 'subtitle', 'description'] as const
export interface LocalizedField {
  local?: ChatComponent
  inherited: ChatComponent
  english?: ChatComponent
  server?: ChatComponent
}

function localizedField(inline: ChatComponent, local?: ChatComponent, asset?: ChatComponent, server?: ChatComponent,
  englishText?: ChatComponent): LocalizedField {
  const generic = typeof inline === 'object' && !Array.isArray(inline) &&
    (inline.translate === 'screen.vuequests.unnamed_quest' || inline.translate === 'screen.vuequests.unnamed_section')
  const english = englishText ?? (!generic && hasComponentContent(inline) ? inline : undefined)
  return { local, inherited: asset ?? english ?? inline, english, server }
}

export function sectionLanguageField(section: Section | undefined, selected?: VisibleCatalog, english?: VisibleCatalog): LocalizedField {
  if (!section) {
    return { inherited: '' }
  }
  const id = section.section_id
  return localizedField(section.name, selected?.edits.sections[id]?.name, selected?.assets.sections[id]?.name, selected?.server.sections[id]?.name,
    english?.server.sections[id]?.name ?? english?.edits.sections[id]?.name ?? english?.assets.sections[id]?.name)
}

export function questLanguageFields(quest: Quest | undefined, selected?: VisibleCatalog, english?: VisibleCatalog): Record<QuestLocaleField, LocalizedField> {
  const id = quest?.quest_id
  const field = (key: QuestLocaleField) => !id ? { inherited: '' } : localizedField(quest.display[key],
    selected?.edits.quests[id]?.[key], selected?.assets.quests[id]?.[key], selected?.server.quests[id]?.[key],
    english?.server.quests[id]?.[key] ?? english?.edits.quests[id]?.[key] ?? english?.assets.quests[id]?.[key])
  return { title: field('title'), subtitle: field('subtitle'), description: field('description') }
}

export function localizeSections(sections: Section[], selected?: VisibleCatalog, english?: VisibleCatalog): Section[] {
  return sections.map(section => {
    const name = sectionLanguageField(section, selected, english)
    const quests = section.quests.map(quest => {
      const fields = questLanguageFields(quest, selected, english)
      const display = { ...quest.display }
      for (const field of questFields) display[field] = fields[field].server ?? fields[field].local ?? fields[field].inherited
      return { ...quest, display }
    })
    return { ...section, name: name.server ?? name.local ?? name.inherited, quests }
  })
}
