import { Hono, type Context, type Next } from "hono";
import * as z from "zod/v4";
import { AiRefusal, AiRequest, AiTruncated, mockTask, runTask, runTaskMeta } from "./ai";
import { hunchesFor, validTimeZone } from "./hunches";
import { usStateCode } from "./hunches/places";

type Env = {
  DB: D1Database;
  /** Caches weather and town fact sheets for hunches. Optional: memory is used when it isn't bound. */
  CACHE?: KVNamespace;
  /** New town fact sheets written per day, across everyone. */
  DAILY_PACK_LIMIT: string;
  /** Website origins allowed to post to the waitlist. */
  SITE_ORIGINS: string;
  ANTHROPIC_API_KEY?: string;
  INSTALL_HASH_SALT?: string;
  DAILY_AI_LIMIT: string;
  DAILY_GLOBAL_AI_LIMIT: string;
  FAIR_SHARE_AI_LIMIT: string;
  TRENDS_MIN_PEOPLE: string;
  SIGNAL_RETENTION_DAYS: string;
  AI_MODEL: string;
  /** "meta": the AI tools run on Meta's model first, with Claude as the fallback. Anything else: Claude only. */
  AI_PROVIDER?: string;
  META_API_KEY?: string;
  META_MODEL?: string;
  META_API_BASE?: string;
  AI_MOCK?: string;
  ALLOW_DEBUG_REGION?: string;
};

const app = new Hono<{ Bindings: Env }>();

// The website shows live hunches and takes waitlist sign-ups, so those two routes answer its origins
const siteCors = async (c: Context<{ Bindings: Env }>, next: Next) => {
  const origin = c.req.header("Origin") ?? "";
  const allowed = c.env.SITE_ORIGINS.split(",").map((o) => o.trim()).filter(Boolean);
  if (allowed.includes(origin) || /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)) {
    c.header("Access-Control-Allow-Origin", origin);
    c.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    c.header("Access-Control-Allow-Headers", "Content-Type");
    c.header("Vary", "Origin");
  }
  if (c.req.method === "OPTIONS") return c.body(null, 204);
  await next();
};
app.use("/v1/waitlist", siteCors);
app.use("/v1/hunches", siteCors);

const today = () => new Date().toISOString().slice(0, 10);

/** One-way, per-day salted hash of the random install id: usable for counting, not for tracking across days. */
async function installHash(env: Env, installId: string, day: string): Promise<string> {
  const data = new TextEncoder().encode(`${env.INSTALL_HASH_SALT ?? "dev-salt"}:${day}:${installId}`);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].slice(0, 16).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function installId(header: string | undefined): string | null {
  return header && /^[0-9a-f-]{16,64}$/i.test(header) ? header.toLowerCase() : null;
}

/** Approximate area from Cloudflare's IP geolocation. Never stored per person, only as part of aggregates. */
function area(c: { req: { raw: Request; header: (n: string) => string | undefined }; env: Env }) {
  if (c.env.ALLOW_DEBUG_REGION === "1") {
    const debug = c.req.header("X-Unicorn-Debug-Region");
    if (debug) {
      const [country, region, regionCode, city] = debug.split("/");
      return { country: country ?? "XX", region: region ?? "Unknown", regionCode: regionCode ?? null, city: city ?? null };
    }
  }
  const cf = (c.req.raw as Request & { cf?: IncomingRequestCfProperties }).cf;
  return {
    country: (cf?.country as string | undefined) ?? "XX",
    region: (cf?.region as string | undefined) ?? "Unknown",
    regionCode: (cf?.regionCode as string | undefined) ?? null,
    city: (cf?.city as string | undefined) ?? null,
  };
}

/** The IP's approximate coordinates and time zone, for weather and local time. Never stored. */
function ipLocation(c: { req: { raw: Request } }) {
  const cf = (c.req.raw as Request & { cf?: IncomingRequestCfProperties }).cf;
  const lat = Number(cf?.latitude);
  const lon = Number(cf?.longitude);
  return {
    lat: Number.isFinite(lat) && cf?.latitude != null ? lat : null,
    lon: Number.isFinite(lon) && cf?.longitude != null ? lon : null,
    timeZone: (cf?.timezone as string | undefined) ?? null,
  };
}

/** Counts one use of a daily budget row and says whether it is still within `limit`. */
async function withinBudget(env: Env, key: string, day: string, limit: number): Promise<boolean> {
  const row = await env.DB.prepare(
    "INSERT INTO ai_usage (install_hash, day, count) VALUES (?1, ?2, 1) " +
      "ON CONFLICT (install_hash, day) DO UPDATE SET count = count + 1 RETURNING count",
  ).bind(key, day).first<{ count: number }>();
  return (row?.count ?? Infinity) <= limit;
}

app.get("/v1/health", (c) =>
  c.json({ ok: true, ai: Boolean(c.env.ANTHROPIC_API_KEY || c.env.META_API_KEY) || c.env.AI_MOCK === "1", provider: c.env.AI_PROVIDER === "meta" && c.env.META_API_KEY ? "meta" : "anthropic" }),
);

// AI writing tools ---------------------------------------------------------------------------------------------------

app.post("/v1/ai", async (c) => {
  const id = installId(c.req.header("X-Unicorn-Install"));
  if (!id) return c.json({ error: "Missing install id." }, 400);
  const parsed = AiRequest.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "Invalid request." }, 400);

  const day = today();
  const resetsAt = new Date(Date.parse(day) + 86_400_000).toISOString();
  const hash = await installHash(c.env, id, day);
  // Install ids are generated on the phone, so a global cap bounds the total spend even if they are rotated
  const total = await c.env.DB.prepare(
    "INSERT INTO ai_usage (install_hash, day, count) VALUES ('*', ?1, 1) " +
      "ON CONFLICT (install_hash, day) DO UPDATE SET count = count + 1 RETURNING count",
  ).bind(day).first<{ count: number }>();
  const globalUsed = total?.count ?? 0;
  const globalCap = Number(c.env.DAILY_GLOBAL_AI_LIMIT);
  if (globalUsed === Math.floor(globalCap * 0.8) || globalUsed === globalCap) {
    // An alert rule on this line in Workers Logs warns before everyone is affected
    console.error("ai global usage", { used: globalUsed, cap: globalCap });
  }
  if (globalUsed > globalCap) {
    c.header("Retry-After", "1800");
    return c.json({ error: "Unicorn AI is very busy right now. Try again in a little while." }, 503);
  }
  // Past 80% of the day's budget, heavy users get a fair share so everyone else keeps working
  const fairShare = globalUsed > globalCap * 0.8;
  const limit = fairShare ? Math.min(Number(c.env.DAILY_AI_LIMIT), Number(c.env.FAIR_SHARE_AI_LIMIT)) : Number(c.env.DAILY_AI_LIMIT);
  const row = await c.env.DB.prepare(
    "INSERT INTO ai_usage (install_hash, day, count) VALUES (?1, ?2, 1) " +
      "ON CONFLICT (install_hash, day) DO UPDATE SET count = count + 1 RETURNING count",
  ).bind(hash, day).first<{ count: number }>();
  const used = row?.count ?? 0;
  const quota = { used: Math.min(used, limit), limit, resetsAt };
  if (used > limit) {
    return c.json({ error: "You've used today's free AI requests.", quota }, 429);
  }

  if (c.env.AI_MOCK === "1") return c.json({ results: mockTask(parsed.data), quota });
  if (!c.env.ANTHROPIC_API_KEY && !(c.env.AI_PROVIDER === "meta" && c.env.META_API_KEY)) {
    return c.json({ error: "Unicorn AI isn't set up yet." }, 503);
  }
  try {
    // The text is processed and returned; it is never stored or logged.
    const results = await runAi(c.env, parsed.data);
    return c.json({ results, quota });
  } catch (e) {
    if (e instanceof AiRefusal) return c.json({ error: "Unicorn can't help with that one." }, 422);
    if (e instanceof AiTruncated) {
      return c.json({ error: "Unicorn's answer left part of your text out, so it wasn't used. Try again.", quota }, 502);
    }
    console.error("ai task failed", (e as Error).name);
    return c.json({ error: "Unicorn AI is busy right now. Try again in a moment." }, 502);
  }
});

/**
 * Meta's model answers first when AI_PROVIDER is "meta"; if it fails, times out or drops text, Claude answers
 * instead, so the tools keep working. A refusal is final. Only which model answered is logged, never the text.
 */
async function runAi(env: Env, req: AiRequest): Promise<string[]> {
  if (env.AI_PROVIDER === "meta" && env.META_API_KEY) {
    try {
      const results = await runTaskMeta(env.META_API_KEY, env.META_MODEL ?? "muse-wonder-1.0", env.META_API_BASE ?? "https://api.meta.ai", req);
      console.log(JSON.stringify({ ai: "meta", task: req.task }));
      return results;
    } catch (e) {
      if (e instanceof AiRefusal || !env.ANTHROPIC_API_KEY) throw e;
      console.log(JSON.stringify({ ai: "meta-fallback", task: req.task, reason: (e as Error).message }));
    }
  }
  return runTask(env.ANTHROPIC_API_KEY!, env.AI_MODEL, req);
}

// Consented, aggregated trends -----------------------------------------------------------------------------------------

const KINDS = ["topic", "brand", "intent", "question"] as const;
const SignalsRequest = z.object({
  items: z.array(z.object({ kind: z.enum(KINDS), key: z.string().min(2).max(80) })).max(60),
});

app.post("/v1/signals", async (c) => {
  const id = installId(c.req.header("X-Unicorn-Install"));
  if (!id) return c.json({ error: "Missing install id." }, 400);
  const parsed = SignalsRequest.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "Invalid request." }, 400);
  const day = today();
  const hash = await installHash(c.env, id, day);
  const { country, region } = area(c);
  const items = [...new Map(parsed.data.items.map((i) => [`${i.kind}:${i.key.toLowerCase()}`, i])).values()];
  if (items.length > 0) {
    await c.env.DB.batch(items.map((i) =>
      c.env.DB.prepare(
        "INSERT OR IGNORE INTO signals (day, country, region, kind, key, install_hash) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
      ).bind(day, country, region, i.kind, i.key.toLowerCase().trim(), hash)
    ));
  }
  return c.json({ accepted: items.length });
});

app.get("/v1/trends", async (c) => {
  const scope = c.req.query("scope") === "country" ? "country" : "region";
  const days = Math.min(Math.max(Number(c.req.query("days") ?? 7), 1), 30);
  const since = new Date(Date.now() - (days - 1) * 86_400_000).toISOString().slice(0, 10);
  const { country, region, city } = area(c);
  const minPeople = Number(c.env.TRENDS_MIN_PEOPLE);
  const where = scope === "country" ? "country = ?1 AND day >= ?2" : "country = ?1 AND day >= ?2 AND region = ?4";
  const params: (string | number)[] = scope === "country" ? [country, since, minPeople] : [country, since, minPeople, region];
  // People are counted per day, so one person active on several days counts on each of them
  const { results } = await c.env.DB.prepare(
    `SELECT kind, key, COUNT(*) AS people FROM signals WHERE ${where} GROUP BY kind, key HAVING people >= ?3 ` +
      "ORDER BY people DESC LIMIT 200",
  ).bind(...params).all<{ kind: string; key: string; people: number }>();
  const pick = (kind: string) => results.filter((r) => r.kind === kind).slice(0, 15).map(({ key, people }) => ({ key, people }));
  // How many different people shared today, so the app can show how close the area is to its first trends
  const sharers = await c.env.DB.prepare(
    `SELECT COUNT(DISTINCT install_hash) AS n FROM signals WHERE country = ?1 AND day = ?2${scope === "country" ? "" : " AND region = ?3"}`,
  ).bind(...(scope === "country" ? [country, today()] : [country, today(), region])).first<{ n: number }>();
  return c.json({
    area: { country, region: scope === "region" ? region : null, city, scope, days },
    minPeople,
    sharersToday: sharers?.n ?? 0,
    topics: pick("topic"),
    brands: pick("brand"),
    intents: pick("intent"),
    questions: pick("question"),
  });
});

// Hunches: playful, clearly-labelled guesses about what the area is asking, from real signals --------------------------

const Name = z.string().trim().min(2).max(60).regex(/^[\p{L}\p{N} '.,\-()]+$/u);

app.get("/v1/hunches", async (c) => {
  const id = installId(c.req.header("X-Unicorn-Install"));
  const ip = area(c);
  const loc = ipLocation(c);
  const q = (key: string) => {
    const parsed = Name.safeParse(c.req.query(key));
    return parsed.success ? parsed.data : null;
  };
  const coordinate = (key: string, max: number) => {
    const n = Number(c.req.query(key));
    return c.req.query(key) != null && Number.isFinite(n) && Math.abs(n) <= max ? Math.round(n * 100) / 100 : null;
  };
  // The phone may send its town and rounded coordinates when the user turned on their neighborhood; otherwise the
  // IP's approximate city is used. Neither is stored.
  const country = /^[A-Z]{2}$/.test(c.req.query("country") ?? "") ? c.req.query("country")! : ip.country;
  const sameCountry = country === ip.country;
  const region = q("region") ?? (sameCountry && ip.region !== "Unknown" ? ip.region : null);
  const regionCode = country === "US" ? usStateCode(region) ?? (sameCountry ? ip.regionCode : null) : (sameCountry ? ip.regionCode : null);
  const lat = coordinate("lat", 90);
  const lon = coordinate("lon", 180);
  const day = today();
  const result = await hunchesFor({
    country,
    region,
    regionCode,
    city: q("city") ?? (sameCountry ? ip.city : null),
    hood: q("hood"),
    lat: lat != null && lon != null ? lat : sameCountry ? loc.lat : null,
    lon: lat != null && lon != null ? lon : sameCountry ? loc.lon : null,
    timeZone: validTimeZone(c.req.query("tz")) ?? validTimeZone(loc.timeZone) ?? "UTC",
  }, {
    cache: c.env.CACHE,
    apiKey: c.env.AI_MOCK === "1" ? undefined : c.env.ANTHROPIC_API_KEY,
    model: c.env.AI_MODEL,
    // A new town's fact sheet costs one AI request: capped per install and across everyone each day
    allowPack: async () => {
      if (!id) return false;
      const hash = await installHash(c.env, id, day);
      return await withinBudget(c.env, "pack", day, Number(c.env.DAILY_PACK_LIMIT)) &&
        await withinBudget(c.env, `pack:${hash}`, day, 3);
    },
  });
  c.header("Cache-Control", "private, max-age=900");
  return c.json(result);
});

// Website waitlist -----------------------------------------------------------------------------------------------------

const WaitlistRequest = z.object({
  email: z.string().trim().toLowerCase().email().max(254),
  platform: z.enum(["android", "iphone", "chrome"]),
});

app.post("/v1/waitlist", async (c) => {
  const parsed = WaitlistRequest.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "Please enter a valid email." }, 400);
  const day = today();
  const ipHash = await installHash(c.env, `ip:${c.req.header("CF-Connecting-IP") ?? "unknown"}`, day);
  if (!(await withinBudget(c.env, `wait:${ipHash}`, day, 5))) return c.json({ error: "Too many sign-ups from here today." }, 429);
  await c.env.DB.prepare(
    "INSERT INTO waitlist (email, platform, country, created_at) VALUES (?1, ?2, ?3, ?4) " +
      "ON CONFLICT (email) DO UPDATE SET platform = excluded.platform",
  ).bind(parsed.data.email, parsed.data.platform, area(c).country, new Date().toISOString()).run();
  return c.json({ ok: true });
});

export default {
  fetch: app.fetch,
  async scheduled(_event: ScheduledController, env: Env) {
    const cutoff = new Date(Date.now() - Number(env.SIGNAL_RETENTION_DAYS) * 86_400_000).toISOString().slice(0, 10);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM signals WHERE day < ?1").bind(cutoff),
      env.DB.prepare("DELETE FROM ai_usage WHERE day < ?1").bind(cutoff),
    ]);
  },
} satisfies ExportedHandler<Env>;
