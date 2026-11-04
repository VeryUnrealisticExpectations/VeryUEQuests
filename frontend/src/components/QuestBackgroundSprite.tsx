export function QuestBackgroundSprite({ id, revision }: { id: string; revision: string }) {
  const [namespace, path] = id.includes(':') ? id.split(':', 2) : ['minecraft', id]
  const query = new URLSearchParams({ path: `assets/${namespace}/textures/gui/sprites/${path}.png`, revision })
  return <img className="quest-background-sprite" src={`/api/assets/file?${query}`} alt="" draggable={false} loading="lazy" />
}
