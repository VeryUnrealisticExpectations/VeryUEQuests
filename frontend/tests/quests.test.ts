import test from 'node:test'
import assert from 'node:assert/strict'
import type { Quest, Section } from '../src/types.ts'
import { connectQuests, dependencyProblem, newQuest, newSection, newTask, normalizeSections, removeQuests } from '../src/lib/quests.ts'

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
  assert.equal(collect.item?.count, 1)
  assert.equal(entity.entity, 'minecraft:zombie')
  assert.equal(item.item?.id, 'minecraft:diamond')
  assert.equal(experience.experience, 1)
  assert.ok(collect.objective_id && item.reward_id)
})
