import type { Position, Quest, Section, Task } from '../types.ts'

export const DEFAULT_QUEST_BACKGROUND = 'vuequests:quest_backgrounds/background_icon_square'

export const allQuests = (sections: Section[]) => sections.flatMap(section => section.quests)
export const labelFor = (id: string) => id.split(':').at(-1)!.replaceAll('_', ' ').replaceAll('/', ' / ')

export function newSection(): Section {
  return { section_id: crypto.randomUUID(), name: 'New section', icon: 'minecraft:book', disabled: false, modLoaded: '', quests: [] }
}

export function newQuest(sectionId: string, position: Position): Quest {
  return {
    quest_id: crypto.randomUUID(), section_id: sectionId, position,
    display: { title: 'New quest', subtitle: '', description: '', icon: 'minecraft:book', tooltip: '' },
    objectives: [], rewards: [], dependencies: [],
  }
}

export function newTask(kind: 'objective' | 'reward', type: string, id: string = crypto.randomUUID()): Task {
  const identity = kind === 'objective' ? { objective_id: id } : { reward_id: id }
  if (type === 'vuequests:collect_item') return { ...identity, type, item: { id: 'minecraft:cobblestone' }, count: 1 }
  if (type === 'vuequests:entity') return { ...identity, type, entity: 'minecraft:zombie', count: 1 }
  if (type === 'vuequests:advancement') return { ...identity, type, advancement: '' }
  if (type === 'vuequests:item') return { ...identity, type, item: { id: 'minecraft:diamond', count: 1 } }
  if (type === 'vuequests:experience') return { ...identity, type, experience: 1 }
  return { ...identity, type }
}

export function dependencyProblem(sections: Section[]): string | null {
  const quests = allQuests(sections)
  const index = new Map(quests.map(quest => [quest.quest_id, quest]))
  if (index.size !== quests.length) return 'Quest IDs must be unique.'
  const complete = new Set<string>()
  const visiting = new Set<string>()
  function visit(id: string): string | null {
    if (visiting.has(id)) return 'This dependency would create a cycle.'
    if (complete.has(id)) return null
    const quest = index.get(id)
    if (!quest) return 'A prerequisite quest is missing.'
    visiting.add(id)
    for (const dependency of quest.dependencies) {
      const problem = visit(dependency)
      if (problem) return problem
    }
    visiting.delete(id)
    complete.add(id)
    return null
  }
  for (const quest of quests) {
    const problem = visit(quest.quest_id)
    if (problem) return problem
  }
  return null
}

export function updateQuest(sections: Section[], id: string, update: (quest: Quest) => Quest): Section[] {
  return sections.map(section => ({ ...section, quests: section.quests.map(quest => quest.quest_id === id ? update(quest) : quest) }))
}

export function connectQuests(sections: Section[], prerequisite: string, dependent: string): Section[] {
  if (prerequisite === dependent) throw new Error('A quest cannot depend on itself.')
  if (!allQuests(sections).some(quest => quest.quest_id === prerequisite)) throw new Error('The prerequisite quest is missing.')
  if (!allQuests(sections).some(quest => quest.quest_id === dependent)) throw new Error('The dependent quest is missing.')
  const updated = updateQuest(sections, dependent, quest => ({ ...quest, dependencies: [...new Set([...quest.dependencies, prerequisite])] }))
  const problem = dependencyProblem(updated)
  if (problem) throw new Error(problem)
  return updated
}

export function toggleQuestDependency(sections: Section[], prerequisite: string, dependent: string): Section[] {
  if (prerequisite === dependent) throw new Error('A quest cannot depend on itself.')
  const quests = allQuests(sections)
  const from = quests.find(quest => quest.quest_id === prerequisite)
  const to = quests.find(quest => quest.quest_id === dependent)
  if (!from) throw new Error('The prerequisite quest is missing.')
  if (!to) throw new Error('The dependent quest is missing.')
  const remove = to.dependencies.includes(prerequisite)
  const updated = sections.map(section => ({ ...section, quests: section.quests.map(quest => {
    if (quest.quest_id === dependent) return { ...quest, dependencies: remove
      ? quest.dependencies.filter(id => id !== prerequisite)
      : [...new Set([...quest.dependencies, prerequisite])] }
    if (!remove && quest.quest_id === prerequisite) return { ...quest, dependencies: quest.dependencies.filter(id => id !== dependent) }
    return quest
  }) }))
  const problem = dependencyProblem(updated)
  if (problem) throw new Error(problem)
  return updated
}

export function snapQuestPosition(position: Position, modifiers: { ctrlKey?: boolean; shiftKey?: boolean } = {}): Position {
  const step = modifiers.ctrlKey ? 5 : 1
  return { x: Math.round(position.x / step) * step || 0, y: Math.round(position.y / step) * step || 0 }
}

export function removeQuests(sections: Section[], ids: Set<string>): Section[] {
  return sections.map(section => ({ ...section, quests: section.quests.filter(quest => !ids.has(quest.quest_id))
    .map(quest => ({ ...quest, dependencies: quest.dependencies.filter(id => !ids.has(id)) })) }))
}

export function normalizeSections(sections: Section[]): Section[] {
  return sections.map(section => ({ ...section, modLoaded: section.modLoaded ?? '', disabled: section.disabled ?? false,
    quests: (section.quests ?? []).map(quest => ({ ...quest, objectives: quest.objectives ?? [], rewards: quest.rewards ?? [], dependencies: quest.dependencies ?? [] })) }))
}
