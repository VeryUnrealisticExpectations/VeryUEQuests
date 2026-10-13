import { useId } from 'react'
import type { Quest } from '../types.ts'

export function DependencyLines({ quests }: { quests: Quest[] }) {
  const marker = useId().replaceAll(':', '')
  const index = new Map(quests.map(quest => [quest.quest_id, quest]))
  return <svg className="dependency-lines" width="1" height="1" aria-hidden="true">
    <defs><marker id={marker} markerWidth="12" markerHeight="12" refX="6" refY="6" orient="auto" markerUnits="userSpaceOnUse">
      <path d="M 2 2 L 7 6 L 2 10" fill="none" stroke="#f2d080" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
    </marker></defs>
    {quests.flatMap(quest => quest.dependencies.map(dependency => {
      const from = index.get(dependency)
      if (!from || from.quest_id === quest.quest_id) return null
      const a = from.position, b = quest.position
      const path = `M ${a.x} ${a.y} L ${(a.x + b.x) / 2} ${(a.y + b.y) / 2} L ${b.x} ${b.y}`
      return <g key={`${dependency}:${quest.quest_id}`}><path d={path} className="dependency-border" /><path d={path} className="dependency-line" markerMid={`url(#${marker})`} /></g>
    }))}
  </svg>
}
