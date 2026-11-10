export interface Position { x: number; y: number }
export type ChatComponent = string | ComponentObject | ChatComponent[]
export interface ComponentObject {
  text?: string
  translate?: string
  fallback?: string
  with?: unknown[]
  keybind?: string
  extra?: ChatComponent[]
  color?: string
  font?: string
  bold?: boolean
  italic?: boolean
  underlined?: boolean
  strikethrough?: boolean
  obfuscated?: boolean
  [key: string]: unknown
}
export interface ComponentTextContext {
  pending?: boolean
  assetRevision: string
  source: 'local-client' | 'server-assets'
  language: string
  rightToLeft: boolean
  fonts: { id: string; loaded: boolean }[]
  translations: { key: string; value: string }[]
  keybinds: { key: string; label: string; resolved: string }[]
  questBackgrounds?: string[]
}
export interface ComponentPreviewFragment { id: string; component: ChatComponent; literal?: string }
export interface ComponentPreviewRequest {
  assetRevision: string
  previewId: string
  requestId: number
  width: number
  scale: number
  viewportTop: number
  viewportHeight: number
  fragments: ComponentPreviewFragment[]
}
export interface ComponentPreviewRange { start: number; end: number; x: number; y: number; width: number; height: number; line: number }
export interface ComponentPreviewCaret { offset: number; x: number; y: number; line: number; affinity?: string }
export interface ComponentPreviewResponse {
  assetRevision: string
  requestId: number
  width: number
  height: number
  totalHeight: number
  viewportTop: number
  lineHeight: number
  png: string
  fragments: { id: string; plainText: string; ranges: ComponentPreviewRange[]; carets: ComponentPreviewCaret[] }[]
  warnings: string[]
}
export interface ItemStack { id: string; count?: number; components?: Record<string, unknown> }
export interface Task {
  type: string
  objective_id?: string
  reward_id?: string
  icon?: string
  item?: ItemStack
  entity?: string
  advancement?: string
  count?: number
  experience?: number
  levels?: number
  loot_table?: string
  command?: string
  attribute?: string
  amount?: number
  [key: string]: unknown
}
export interface Quest {
  quest_id: string
  section_id: string
  position: Position
  display: { title: ChatComponent; subtitle: ChatComponent; description: ChatComponent; icon: string; tooltip: string; background_sprite?: string }
  objectives: Task[]
  rewards: Task[]
  dependencies: string[]
  [key: string]: unknown
}
export interface Section {
  section_id: string
  name: ChatComponent
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
  advancements: string[]
  lootTables?: string[]
  attributes?: RegistryEntry[]
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
