import type { Task, TypeEntry } from '../types.ts'
import { newTask } from './quests.ts'
import { isRegistryId } from './registryPicker.ts'

export type TaskKind = 'objective' | 'reward'
export const MAX_OBJECTIVE_COUNT = 2_147_483_647

export function defaultTaskType(kind: TaskKind, types: TypeEntry[]): string | undefined {
  const preferred = kind === 'objective' ? 'vuequests:collect_item' : 'vuequests:item'
  return types.find(type => type.id === preferred)?.id ?? types[0]?.id
}

export function taskDisplayIcon(kind: TaskKind, task: Task): string {
  if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:submit_item') return task.item?.id || 'minecraft:cobblestone'
  if (task.type === 'vuequests:entity') return 'minecraft:iron_sword'
  if (task.type === 'vuequests:item') return task.item?.id || 'minecraft:chest'
  if (task.type === 'vuequests:experience' || task.type === 'vuequests:experience_levels') return 'minecraft:experience_bottle'
  if (task.type === 'vuequests:advancement' && kind === 'reward') return 'minecraft:enchanted_book'
  if (task.type === 'vuequests:command') return 'minecraft:command_block'
  if (task.type === 'vuequests:attribute') return 'minecraft:golden_apple'
  if (task.type === 'vuequests:travel_dimension') return 'minecraft:obsidian'
  if (task.type === 'vuequests:travel_biome') return 'minecraft:grass_block'
  if (task.type === 'vuequests:travel_coordinates') return 'minecraft:compass'
  if (task.type === 'vuequests:travel_structure') return 'minecraft:stone_bricks'
  if (task.type === 'vuequests:statistic') return 'minecraft:writable_book'
  return kind === 'objective' ? 'minecraft:book' : 'minecraft:chest'
}

export function taskEditorId(kind: TaskKind, task: Task, index: number): string {
  return `${kind}:${(kind === 'objective' ? task.objective_id : task.reward_id) ?? index}`
}

export function objectiveCount(task: Task): number | undefined {
  if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:submit_item') return task.count ?? task.item?.count ?? 1
  if (task.type === 'vuequests:entity' || task.type === 'vuequests:statistic') return task.count ?? 1
}

export function isAdvancementId(id: string): boolean {
  return isRegistryId(id) || isRegistryId(`minecraft:${id.replace(/^:/, '')}`)
}

export function isAttributeAmount(amount: unknown): amount is number {
  return typeof amount === 'number' && Number.isFinite(amount) && amount > 0
}

export function taskProblem(task: Task): string | undefined {
  if (task.type === 'vuequests:collect_item' || task.type === 'vuequests:submit_item' || task.type === 'vuequests:item') {
    if (!task.item || !isRegistryId(task.item.id)) return 'Choose an item.'
    const count = task.item.count ?? 1
    if (!Number.isInteger(count) || count < 1 || count > 99) return 'Item stack count must be between 1 and 99.'
  } else if (task.type === 'vuequests:experience' && (!Number.isInteger(task.experience) || task.experience! < 0)) {
    return 'Experience must be a nonnegative integer.'
  } else if (task.type === 'vuequests:entity' && (!task.entity || !isRegistryId(task.entity))) {
    return 'Choose an entity.'
  } else if (task.type === 'vuequests:advancement') {
    const id = task.advancement
    if (typeof id !== 'string' || !isAdvancementId(id)) {
      return 'Enter a valid advancement ID, such as minecraft:story/mine_stone.'
    }
  } else if (task.type === 'vuequests:experience_levels') {
    if (!Number.isInteger(task.levels) || task.levels! < 1 || task.levels! > 2_147_483_647) {
      return 'Experience levels must be a positive 32-bit integer.'
    }
  } else if (task.type === 'vuequests:loot_table') {
    if (typeof task.loot_table !== 'string' || !isRegistryId(task.loot_table)) return 'Choose a loot table.'
  } else if (task.type === 'vuequests:command') {
    if (typeof task.command !== 'string' || !task.command.trim() || task.command.trim() === '/' || /[\r\n]/.test(task.command)) {
      return 'Enter a single Minecraft command.'
    }
  } else if (task.type === 'vuequests:attribute') {
    if (typeof task.attribute !== 'string' || !isRegistryId(task.attribute)) return 'Choose a player attribute.'
    if (!isAttributeAmount(task.amount)) return 'Attribute amount must be a finite positive number.'
  }
  if (task.type === 'vuequests:travel_dimension' || task.type === 'vuequests:travel_coordinates') {
    if (!task.dimension || !isRegistryId(task.dimension)) return 'Choose a dimension.'
  }
  if (task.type === 'vuequests:travel_biome' && (!task.biome || !isRegistryId(task.biome))) return 'Choose a biome.'
  if (task.type === 'vuequests:travel_structure' && (!task.structure || !isRegistryId(task.structure))) return 'Choose a structure.'
  if (task.type === 'vuequests:statistic') {
    if (!task.stat_type || !isRegistryId(task.stat_type)) return 'Choose a statistic type.'
    if (!task.statistic || !isRegistryId(task.statistic)) return 'Choose a statistic.'
  }
  if (task.type === 'vuequests:travel_coordinates') {
    for (const axis of ['x', 'y', 'z'] as const) {
      const min = task[`min_${axis}`]
      const max = task[`max_${axis}`]
      if (min === undefined || max === undefined || !Number.isInteger(min) || !Number.isInteger(max)
        || min < -2_147_483_648 || max > 2_147_483_647 || min > max) return 'Enter valid integer bounds with each minimum at most its maximum.'
    }
  }
  const target = objectiveCount(task)
  if (target !== undefined && (!Number.isInteger(target) || target < 1 || target > MAX_OBJECTIVE_COUNT)) {
    return `Objective count must be an integer between 1 and ${MAX_OBJECTIVE_COUNT}.`
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
