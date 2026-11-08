import test from 'node:test'
import assert from 'node:assert/strict'
import type { Quest, Section } from '../src/types.ts'
import { connectQuests, dependencyProblem, newQuest, newSection, newTask, normalizeSections, removeQuests, snapQuestPosition, toggleQuestDependency } from '../src/lib/quests.ts'
import { gameTextureUrl, stoneTileDarkness } from '../src/lib/terrain.ts'

const fixture = () => {
  const section = newSection()
  section.quests = [newQuest(section.section_id, { x: 0, y: 0 }), newQuest(section.section_id, { x: 80, y: 25 }), newQuest(section.section_id, { x: 160, y: 0 })]
  return section
}
test('connections preserve prerequisite direction and reject cycles across sections', () => {
  const first = fixture(), second = fixture()
  let sections = connectQuests([first, second], first.quests[0].quest_id, first.quests[1].quest_id)
  sections = connectQuests(sections, first.quests[1].quest_id, second.quests[0].quest_id)
  assert.deepEqual(sections[0].quests[1].dependencies, [first.quests[0].quest_id])
  assert.equal(dependencyProblem(sections), null)
  assert.throws(() => connectQuests(sections, second.quests[0].quest_id, first.quests[0].quest_id), /cycle/)
  assert.throws(() => connectQuests(sections, first.quests[0].quest_id, first.quests[0].quest_id), /itself/)
  assert.deepEqual(first.quests[1].dependencies, [])
})
test('deleting a quest removes incoming references and keeps unrelated edges', () => {
  const section = fixture()
  const [a, b, c] = section.quests
  const sections = connectQuests(connectQuests([section], a.quest_id, b.quest_id), b.quest_id, c.quest_id)
  const remaining = removeQuests(sections, new Set([b.quest_id]))
  assert.equal(remaining[0].quests.length, 2)
  assert.deepEqual(remaining[0].quests[1].dependencies, [])
  assert.equal(dependencyProblem(remaining), null)
})
test('duplicate connections are idempotent and missing IDs are rejected', () => {
  const section = fixture()
  const [a, b] = section.quests
  const once = connectQuests([section], a.quest_id, b.quest_id)
  const twice = connectQuests(once, a.quest_id, b.quest_id)
  assert.equal(twice[0].quests[1].dependencies.length, 1)
  assert.throws(() => connectQuests([section], 'missing', b.quest_id), /missing/)
})

test('dependency gestures create, remove, and reverse prerequisite direction without changing other edges', () => {
  const section = fixture()
  const [a, b, c] = section.quests
  const original = connectQuests([section], a.quest_id, c.quest_id)
  const added = toggleQuestDependency(original, a.quest_id, b.quest_id)
  assert.deepEqual(added[0].quests[1].dependencies, [a.quest_id])
  assert.deepEqual(original[0].quests[1].dependencies, [])
  const removed = toggleQuestDependency(added, a.quest_id, b.quest_id)
  assert.deepEqual(removed[0].quests[1].dependencies, [])
  assert.deepEqual(removed[0].quests[2].dependencies, [a.quest_id])
  const reversed = toggleQuestDependency(added, b.quest_id, a.quest_id)
  assert.deepEqual(reversed[0].quests[0].dependencies, [b.quest_id])
  assert.deepEqual(reversed[0].quests[1].dependencies, [])
  assert.deepEqual(reversed[0].quests[2].dependencies, [a.quest_id])
  assert.equal(dependencyProblem(reversed), null)
})

test('a dependency reversal that closes an alternate path is rejected atomically', () => {
  const section = fixture()
  const [a, b, c] = section.quests
  let sections = connectQuests([section], a.quest_id, b.quest_id)
  sections = connectQuests(sections, a.quest_id, c.quest_id)
  sections = connectQuests(sections, c.quest_id, b.quest_id)
  const before = JSON.stringify(sections)
  assert.throws(() => toggleQuestDependency(sections, b.quest_id, a.quest_id), /cycle/)
  assert.equal(JSON.stringify(sections), before)
  assert.deepEqual(sections[0].quests[1].dependencies, [a.quest_id, c.quest_id])
})

test('dependency gestures validate both endpoints and reject self-links and cycles across sections', () => {
  const first = fixture(), second = fixture()
  const a = first.quests[0], b = second.quests[0], c = second.quests[1]
  let sections = toggleQuestDependency([first, second], a.quest_id, b.quest_id)
  sections = toggleQuestDependency(sections, b.quest_id, c.quest_id)
  assert.deepEqual(sections[1].quests[0].dependencies, [a.quest_id])
  assert.throws(() => toggleQuestDependency(sections, c.quest_id, a.quest_id), /cycle/)
  assert.throws(() => toggleQuestDependency(sections, a.quest_id, a.quest_id), /itself/)
  assert.throws(() => toggleQuestDependency(sections, 'missing', b.quest_id), /prerequisite.*missing/)
  assert.throws(() => toggleQuestDependency(sections, a.quest_id, 'missing'), /dependent.*missing/)
})

test('quest drag snapping uses world coordinates with Ctrl precedence and integer storage', () => {
  const world = { x: 13.6, y: -8.6 }
  assert.deepEqual(snapQuestPosition(world), { x: 14, y: -9 })
  assert.deepEqual(snapQuestPosition(world, { shiftKey: true }), { x: 14, y: -9 })
  assert.deepEqual(snapQuestPosition(world, { ctrlKey: true }), { x: 15, y: -10 })
  assert.deepEqual(snapQuestPosition(world, { shiftKey: true, ctrlKey: true }), { x: 15, y: -10 })
  assert.deepEqual(snapQuestPosition({ x: -0.3, y: -1 }, { ctrlKey: true }), { x: 0, y: 0 })
  assert.deepEqual(snapQuestPosition(world, { ctrlKey: false, shiftKey: true }), { x: 14, y: -9 })
  const screenDelta = 17, zoom = 0.5
  assert.deepEqual(snapQuestPosition({ x: screenDelta / zoom, y: -screenDelta / zoom }, { ctrlKey: true }), { x: 35, y: -35 })
})

test('terrain URLs follow asset revisions and world shading stays stable and subtle', () => {
  const url = new URL(gameTextureUrl('stone', 'pack revision+1'), 'http://localhost')
  assert.equal(url.pathname, '/api/assets/file')
  assert.equal(url.searchParams.get('path'), 'assets/minecraft/textures/block/stone.png')
  assert.equal(url.searchParams.get('revision'), 'pack revision+1')
  assert.notEqual(gameTextureUrl('dirt', 'one'), gameTextureUrl('dirt', 'two'))
  const values = new Set<number>()
  for (let x = -10; x <= 10; x++) for (let y = -10; y <= 10; y++) {
    const darkness = stoneTileDarkness(x, y)
    assert.equal(stoneTileDarkness(x, y), darkness)
    assert.ok(darkness >= 0.56 && darkness <= 0.64)
    values.add(darkness)
  }
  assert.ok(values.size > 100)
})
test('normalizing optional lists preserves custom quest fields', () => {
  const section = fixture()
  const raw = { ...section, quests: [{ ...section.quests[0], objectives: undefined, rewards: undefined, dependencies: undefined, custom: { flag: true } }] } as unknown as Section
  const normalized = normalizeSections([raw])
  assert.deepEqual(normalized[0].quests[0].objectives, [])
  assert.deepEqual(normalized[0].quests[0].custom, { flag: true })
  assert.equal((raw.quests[0] as Quest).objectives, undefined)
})
test('built-in task templates match quest serializer field names', () => {
  const collect = newTask('objective', 'vuequests:collect_item')
  const entity = newTask('objective', 'vuequests:entity')
  const item = newTask('reward', 'vuequests:item')
  const experience = newTask('reward', 'vuequests:experience')
  assert.equal(collect.count, 1)
  assert.equal(collect.item?.count, undefined)
  assert.equal(entity.entity, 'minecraft:zombie')
  assert.equal(entity.count, 1)
  assert.equal(item.item?.id, 'minecraft:diamond')
  assert.equal(experience.experience, 1)
  assert.ok(collect.objective_id && item.reward_id)
})
