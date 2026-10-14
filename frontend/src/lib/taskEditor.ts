import type { Task, TypeEntry } from '../types.ts'
import { newTask } from './quests.ts'
import { isRegistryId } from './registryPicker.ts'

export type TaskKind = 'objective' | 'reward'

export function defaultTaskType(kind: TaskKind, types: TypeEntry[]): string | undefined {
  const preferred = kind === 'objective' ? 'vuequests:collect_item' : 'vuequests:item'
  return types.find(type => type.id === preferred)?.id ?? types[0]?.id
}

export function taskDisplayIcon(kind: TaskKind, task: Task): string {
  if (task.type === 'vuequests:collect_item') return task.item?.id || 'minecraft:cobblestone'
  if (task.type === 'vuequests:entity') return 'minecraft:iron_sword'
  if (task.type === 'vuequests:item') return task.item?.id || 'minecraft:chest'
  if (task.type === 'vuequests:experience') return 'minecraft:experience_bottle'
  return kind === 'objective' ? 'minecraft:book' : 'minecraft:chest'
}

export function taskEditorId(kind: TaskKind, task: Task, index: number): string {
  return `${kind}:${(kind === 'objective' ? task.objective_id : task.reward_id) ?? index}`
}

export function taskProblem(task: Task): string | undefined {
  if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:item') {
    if (!task.item || !isRegistryId(task.item.id)) return 'Choose an item.'
    if (!Number.isInteger(task.item.count) || task.item.count < 1 || task.item.count > 99) return 'Item count must be between 1 and 99.'
  } else if (task.type === 'vuequests:experience' && (!Number.isInteger(task.experience) || task.experience! < 0)) {
    return 'Experience must be a nonnegative integer.'
  } else if (task.type === 'vuequests:entity' && (!task.entity || !isRegistryId(task.entity))) {
    return 'Choose an entity.'
  }
}

export function changeTaskType(kind: TaskKind, task: Task, type: string): Task {
  return task.type === type ? task : newTask(kind, type, kind === 'objective' ? task.objective_id : task.reward_id)
}

export function customTaskFields(kind: TaskKind, task: Task): Record<string, unknown> {
  const reserved = kind === 'objective' ? ['type', 'objective_id'] : ['type', 'reward_id']
  return Object.fromEntries(Object.entries(task).filter(([key]) => !reserved.includes(key)))
}

export function updateCustomTaskFields(kind: TaskKind, task: Task, fields: Record<string, unknown>): Task {
  return { ...fields, type: task.type, ...(kind === 'objective'
    ? { objective_id: task.objective_id } : { reward_id: task.reward_id }) }
}
