export class BoundedCache<K, V> {
  private entries = new Map<K, { value: V; bytes: number }>()
  private used = 0
  private maximumBytes: number
  private maximumEntries: number
  constructor(maximumBytes: number, maximumEntries: number) {
    this.maximumBytes = maximumBytes
    this.maximumEntries = maximumEntries
  }
  get bytes() { return this.used }
  get size() { return this.entries.size }
  get(key: K): V | undefined {
    const entry = this.entries.get(key)
    if (!entry) return undefined
    this.entries.delete(key); this.entries.set(key, entry)
    return entry.value
  }
  set(key: K, value: V, bytes: number) {
    this.delete(key)
    if (bytes > this.maximumBytes) return
    this.entries.set(key, { value, bytes }); this.used += bytes
    while (this.used > this.maximumBytes || this.entries.size > this.maximumEntries) this.delete(this.entries.keys().next().value!)
  }
  delete(key: K) {
    const entry = this.entries.get(key)
    if (entry) { this.used -= entry.bytes; this.entries.delete(key) }
  }
  clear() { this.entries.clear(); this.used = 0 }
}
