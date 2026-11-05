// A small read-through cache: Workers KV when it is bound, otherwise the isolate's memory (local dev, tests).

export type Cache = KVNamespace | undefined;

const memory = new Map<string, { until: number; value: unknown }>();

export async function cached<T>(kv: Cache, key: string, ttlSeconds: number, load: () => Promise<T>): Promise<T> {
  if (kv) {
    const hit = await kv.get<{ v: T }>(key, "json").catch(() => null);
    if (hit) return hit.v;
  } else {
    const hit = memory.get(key);
    if (hit && hit.until > Date.now()) return hit.value as T;
  }
  const value = await load();
  if (kv) {
    // KV's minimum TTL is 60 seconds
    await kv.put(key, JSON.stringify({ v: value }), { expirationTtl: Math.max(60, ttlSeconds) }).catch(() => {});
  } else {
    memory.set(key, { until: Date.now() + ttlSeconds * 1000, value });
  }
  return value;
}
