import type { Quest, Section, Task } from '../types.ts'
import { componentLiteralLength, hasComponentContent } from './components.ts'
import { dependencyProblem } from './quests.ts'
import { isAttributeAmount, MAX_OBJECTIVE_COUNT, objectiveCount, taskEditorId, taskProblem } from './taskEditor.ts'
import type { TaskKind } from './taskEditor.ts'
import { isRegistryId } from './registryPicker.ts'

export const questStages = [
  { id: 'details', label: 'Details' },
  { id: 'objectives', label: 'Objectives' },
  { id: 'rewards', label: 'Rewards' },
  { id: 'review', label: 'Review' },
] as const

export type QuestStage = typeof questStages[number]['id']
export type QuestTextField = 'title' | 'subtitle' | 'description'
export interface TaskRow { key: string; task: Task }
export type TaskRows = Record<TaskKind, TaskRow[]>
export type InputDrafts = Record<string, string>
export interface QuestIssue { stage: QuestStage; target: string; message: string; taskKey?: string }

export function taskRowsFor(kind: TaskKind, tasks: Task[]): TaskRow[] {
  return tasks.map((task, index) => ({ key: taskEditorId(kind, task, index), task }))
}

export function integerInput(text: string, min: number, max = Number.MAX_SAFE_INTEGER): number | undefined {
  const value = text.trim()
  if (!/^[+-]?\d+$/.test(value)) return
  const number = Number(value)
  if (Number.isSafeInteger(number) && number >= min && number <= max) return number
}

export function jsonObjectInput(text: string): Record<string, unknown> | undefined {
  try {
    const value: unknown = JSON.parse(text)
    if (value && typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
  } catch {
    return undefined
  }
}

export function attributeAmountInput(text: string): number | undefined {
  const raw = text.trim()
  if (!/^\+?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(raw)) return
  const amount = Number(raw)
  if (isAttributeAmount(amount)) return amount
}

export function taskQuantity(task: Task) {
  const count = objectiveCount(task)
  if (count !== undefined) return { label: 'Quantity', field: 'count' as const, value: count, min: 1, max: MAX_OBJECTIVE_COUNT }
  if (task.type === 'vuequests:item') return { label: 'Quantity', field: 'itemCount' as const, value: task.item?.count ?? 1, min: 1, max: 99 }
  if (task.type === 'vuequests:experience') return { label: 'Experience', field: 'experience' as const, value: task.experience ?? 0, min: 0 }
  if (task.type === 'vuequests:experience_levels') return { label: 'Levels', field: 'levels' as const, value: task.levels ?? 0, min: 1, max: 2_147_483_647 }
}

export const taskInputId = (key: string, field: string) => 'quest-' + key + '-' + field

export function sectionsWithDraft(sections: Section[], draft: Quest): Section[] {
  return sections.map(section => {
    if (section.section_id !== draft.section_id) return section
    const exists = section.quests.some(quest => quest.quest_id === draft.quest_id)
    return { ...section, quests: exists
      ? section.quests.map(quest => quest.quest_id === draft.quest_id ? draft : quest)
      : [...section.quests, draft] }
  })
}

export function blockedPrerequisites(sections: Section[], questId: string): Set<string> {
  const dependents = new Map<string, string[]>()
  for (const section of sections) {
    for (const quest of section.quests) {
      for (const id of quest.dependencies) {
        const entries = dependents.get(id) ?? []
        entries.push(quest.quest_id)
        dependents.set(id, entries)
      }
    }
  }
  const blocked = new Set([questId])
  const pending = [questId]
  while (pending.length) {
    for (const id of dependents.get(pending.pop()!) ?? []) {
      if (blocked.has(id)) continue
      blocked.add(id)
      pending.push(id)
    }
  }
  return blocked
}

export function questIssues(draft: Quest, rows: TaskRows, inputs: InputDrafts, sections: Section[]): QuestIssue[] {
  const issues: QuestIssue[] = []
  if (!hasComponentContent(draft.display.title)) issues.push({ stage: 'details', target: 'quest-title', message: 'Quest title is required.' })
  else if (componentLiteralLength(draft.display.title) > 160) issues.push({ stage: 'details', target: 'quest-title', message: 'Quest title may contain at most 160 literal characters.' })
  if (!isRegistryId(draft.display.icon)) issues.push({ stage: 'details', target: 'quest-icon', message: 'Choose a quest icon or enter a namespaced item ID.' })
  for (const axis of ['x', 'y'] as const) {
    const target = 'quest-' + axis
    if (integerInput(inputs[target] ?? String(draft.position[axis]), -2_147_483_648, 2_147_483_647) === undefined) {
      issues.push({ stage: 'details', target, message: axis.toUpperCase() + ' must be a signed 32-bit integer.' })
    }
  }
  for (const kind of ['objective', 'reward'] as const) {
    const stage = kind === 'objective' ? 'objectives' : 'rewards'
    rows[kind].forEach(({ key, task }, index) => {
      const label = (kind === 'objective' ? 'Objective ' : 'Reward ') + (index + 1) + ' — '
      const quantity = taskQuantity(task)
      let invalidQuantity = false
      if (quantity) {
        const target = taskInputId(key, quantity.field)
        invalidQuantity = integerInput(inputs[target] ?? String(quantity.value), quantity.min, quantity.max) === undefined
        if (invalidQuantity) {
          issues.push({ stage, target, message: label + quantity.label + ' must be a valid whole number.' })
        }
      }
      if (task.type === 'vuequests:attribute') {
        const target = taskInputId(key, 'amount')
        if (attributeAmountInput(inputs[target] ?? String(task.amount ?? '')) === undefined) {
          issues.push({ stage, target, message: label + 'Attribute amount must be a finite positive number.' })
        }
      }
      if (task.type === 'vuequests:travel_coordinates') {
        for (const corner of ['min', 'max'] as const) {
          for (const axis of ['x', 'y', 'z'] as const) {
            const field = `${corner}_${axis}` as const
            const target = taskInputId(key, field)
            if (integerInput(inputs[target] ?? String(task[field] ?? ''), -2_147_483_648, 2_147_483_647) === undefined) {
              issues.push({ stage, target, taskKey: key, message: label + field + ' must be a signed 32-bit integer.' })
            }
          }
        }
      }
      for (const field of ['components', 'fields']) {
        const target = taskInputId(key, field)
        const raw = inputs[target]
        if (raw !== undefined && !jsonObjectInput(raw)) {
          issues.push({ stage, target, taskKey: key, message: label + (field === 'components' ? 'Item components' : 'Custom fields') + ' must be a JSON object.' })
        }
      }
      const problem = taskProblem(task)
      if (problem && !invalidQuantity) issues.push({ stage, target: taskInputId(key, 'target'), taskKey: key, message: label + problem })
    })
  }
  const problem = dependencyProblem(sectionsWithDraft(sections, draft))
  if (problem) issues.push({ stage: 'review', target: 'quest-prerequisites', message: problem })
  return issues
}
