export const STONE_TILE_SIZE = 32
export const STONE_SHADE_SIZE = 128

export function gameTextureUrl(texture: 'grass_block_side' | 'dirt' | 'stone', revision: string): string {
  return `/api/assets/file?${new URLSearchParams({ path: `assets/minecraft/textures/block/${texture}.png`, revision })}`
}

export function stoneTileDarkness(x: number, y: number): number {
  let hash = Math.imul(x, 374761393) ^ Math.imul(y, 668265263)
  hash = Math.imul(hash ^ (hash >>> 13), 1274126177)
  return 0.56 + ((hash ^ (hash >>> 16)) >>> 0) / 0xffffffff * 0.08
}
