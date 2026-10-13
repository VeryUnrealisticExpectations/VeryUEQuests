export interface Position { x: number; y: number }
export interface ItemStack { id: string; count: number; components?: Record<string, unknown> }
export interface Task {
  type: string
  objective_id?: string
  reward_id?: string
  icon?: string
  item?: ItemStack
  entity?: string
  experience?: number
  [key: string]: unknown
}
export interface Quest {
  quest_id: string
  section_id: string
  position: Position
  display: { title: string; subtitle: string; description: string; icon: string; tooltip: string }
  objectives: Task[]
  rewards: Task[]
  dependencies: string[]
  [key: string]: unknown
}
export interface Section {
  section_id: string
  name: string
  icon: string
  disabled: boolean
  modLoaded: string
  quests: Quest[]
  [key: string]: unknown
}
export interface RegistryEntry { id: string; name: string; item?: string }
export interface TypeEntry { id: string; fields: string[] }
export type RegistryKind = 'items' | 'blocks' | 'entities'
export interface Catalog {
  items: RegistryEntry[]
  blocks: RegistryEntry[]
  entities: RegistryEntry[]
  objectiveTypes: TypeEntry[]
  rewardTypes: TypeEntry[]
}
export interface EditorSnapshot {
  sections: Section[]
  revision: string
  minecraftVersion: string
  assetRevision: string
  catalog: Catalog
}
