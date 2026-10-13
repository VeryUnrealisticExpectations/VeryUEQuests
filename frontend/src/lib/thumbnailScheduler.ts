interface Subscriber<T> { resolve: (value: T) => void; reject: (error: unknown) => void; detach: () => void }
interface Job<T> { key: string; run: () => Promise<T>; subscribers: Set<Subscriber<T>>; started: boolean }
export const aborted = () => new DOMException('Icon request cancelled.', 'AbortError')
export class ThumbnailQueueFullError extends Error { constructor() { super('The visible icon queue is full.') } }

export class ThumbnailScheduler<T> {
  private jobs = new Map<string, Job<T>>()
  private active = false
  private running: Promise<void> = Promise.resolve()
  idle(): Promise<void> { return this.running }
  get size() { return this.jobs.size }
  request(key: string, run: () => Promise<T>, signal: AbortSignal): Promise<T> {
    if (signal.aborted) return Promise.reject(aborted())
    let job = this.jobs.get(key)
    if (!job) {
      if (this.jobs.size >= 256) return Promise.reject(new ThumbnailQueueFullError())
      job = { key, run, subscribers: new Set(), started: false }; this.jobs.set(key, job)
    }
    const current = job
    return new Promise<T>((resolve, reject) => {
      const cancel = () => {
        subscriber.detach(); current.subscribers.delete(subscriber); reject(aborted())
        if (!current.started && !current.subscribers.size) this.jobs.delete(key)
      }
      const subscriber: Subscriber<T> = { resolve, reject, detach: () => signal.removeEventListener('abort', cancel) }
      current.subscribers.add(subscriber); signal.addEventListener('abort', cancel, { once: true })
      queueMicrotask(() => { void this.pump() })
    })
  }
  clear() {
    for (const job of this.jobs.values()) for (const subscriber of job.subscribers) { subscriber.detach(); subscriber.reject(aborted()) }
    this.jobs.clear()
  }
  private async pump() {
    if (this.active) return
    const job = this.jobs.values().next().value
    if (!job) return
    this.active = true; job.started = true
    let finished!: () => void
    this.running = new Promise<void>(resolve => { finished = resolve })
    try { const result = await job.run(); for (const subscriber of job.subscribers) subscriber.resolve(result) }
    catch (error) { for (const subscriber of job.subscribers) subscriber.reject(error) }
    finally {
      for (const subscriber of job.subscribers) subscriber.detach()
      if (this.jobs.get(job.key) === job) this.jobs.delete(job.key)
      this.active = false; finished(); queueMicrotask(() => { void this.pump() })
    }
  }
}
