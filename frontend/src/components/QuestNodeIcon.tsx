import type { Quest } from '../types.ts'
import { DEFAULT_QUEST_BACKGROUND } from '../lib/quests.ts'
import { ItemIcon } from './ItemIcon.tsx'
import { QuestBackgroundSprite } from './QuestBackgroundSprite.tsx'

export function QuestNodeIcon({ display, epoch, revision }:
  { display: Quest['display']; epoch: number; revision: string }) {
  return <span className="quest-node-icon" aria-hidden="true">
    <QuestBackgroundSprite id={display.background_sprite ?? DEFAULT_QUEST_BACKGROUND} revision={revision} />
    <ItemIcon id={display.icon} epoch={epoch} />
  </span>
}
