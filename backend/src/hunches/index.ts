// Hunches: playful guesses about what a town is asking today, until enough real people near the user share. They are
// built from real signals (the weather service's alerts, the forecast, the calendar, the time of day and the town's
// fact sheet) and always labelled as guesses in the app, with the reason shown under each one.

import { momentsOn, type Moment } from "./calendar";
import { type Cache } from "./cache";
import { cityPack, type CityPack } from "./cityPack";
import { chainsFor, climateFor, inSeason, isSouthern, metroFor, usesFahrenheit, type Chains, type League, type Metro } from "./places";
import { TEMPLATES, type Ctx, type Kind, type Template } from "./templates";
import { weatherAt, type Weather } from "./weather";

export type Hunch = { emoji: string; text: string; kind: Kind; why: string };

export type HunchPlace = {
  country: string;
  region: string | null;
  regionCode: string | null;
  city: string | null;
  hood: string | null;
  lat: number | null;
  lon: number | null;
  timeZone: string;
};

export type HunchDeps = {
  cache: Cache;
  apiKey: string | undefined;
  model: string;
  /** Whether a missing town fact sheet may be written now (budget check). */
  allowPack: () => Promise<boolean>;
};

const COUNT = 10;
const CAPS: Record<Kind, number> = { weather: 2, moment: 3, season: 2, local: 3, sports: 1, time: 2, everyday: 4 };
const BOOST: Record<Kind, number> = { weather: 6, moment: 4, season: 2, local: 4, sports: 3, time: 3, everyday: 1 };
const LEAGUES: League[] = ["nfl", "college", "nba", "mlb", "nhl", "mls"];
/** Slots that name where the user is, which every hunch may repeat. */
const SHARED_SLOTS = new Set(["city", "place", "state", "weekday", "days", "high", "low", "temp", "alert"]);
/** New places people buy tickets for, as opposed to new shops and restaurants. */
const ATTRACTION = /park|resort|museum|zoo|aquarium|stadium|arena|venue|theater|theatre|attraction|amphitheater|water|ride|cinema/i;
const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export async function hunchesFor(place: HunchPlace, deps: HunchDeps, now = new Date()) {
  const local = localTime(now, place.timeZone);
  const town = place.city ?? place.region;
  const [weather, pack] = await Promise.all([
    place.lat != null && place.lon != null
      ? weatherAt(deps.cache, place.lat, place.lon, place.country, place.timeZone).catch(() => null)
      : Promise.resolve(null),
    place.city
      ? cityPack(deps.cache, deps.apiKey, deps.model, { city: place.city, region: place.region, country: place.country }, deps.allowPack)
      : Promise.resolve(null),
  ]);
  const metro = metroFor(place.lat, place.lon);
  const ctx: Ctx = {
    country: place.country,
    us: place.country === "US",
    month: local.month,
    day: local.day,
    weekday: local.weekday,
    hour: local.hour,
    season: seasonOf(local.month, isSouthern(place.country)),
    climate: new Set(climateFor(place.country, place.regionCode)),
    alert: weather?.alert?.kind ?? null,
    today: weather?.today ?? null,
    tomorrow: weather?.tomorrow ?? null,
    moments: new Map(momentsOn(place.country, local.year, local.month, local.day).reverse().map((mo) => [mo.id, mo])),
    inSeason: new Set(LEAGUES.filter((l) => inSeason(l, local.month, local.day))),
  };

  const hunches: Hunch[] = [];
  // A dangerous-weather alert is shown plainly, first, and no other weather hunch is made around it
  const serious = weather?.alert?.kind === "serious" ? weather.alert : null;
  if (serious) {
    hunches.push({
      emoji: "⚠️",
      text: `There's a ${serious.event} for your area. Follow local officials and stay safe.`,
      kind: "weather",
      why: "From the National Weather Service",
    });
  }

  const slots = new Slots(place, town, ctx, chainsFor(place.country, place.regionCode), metro, pack, weather);
  const seed = hash(`${town}|${local.year}-${local.month}-${local.day}|${Math.floor(local.hour / 6)}`);
  const candidates: { hunch: Hunch; names: string[]; score: number }[] = [];
  for (const template of TEMPLATES) {
    if (serious && template.kind === "weather") continue;
    const moment = template.moment ? ctx.moments.get(template.moment) : undefined;
    if (template.moment && !moment) continue;
    if (template.when && !template.when(ctx, moment)) continue;
    const rng = random(seed ^ hash(template.text));
    const filled = slots.fill(template.text, rng, moment);
    if (!filled) continue;
    const { text, names } = filled;
    const closeness = moment ? Math.max(0, 6 - Math.abs(moment.daysAway) / 3) : 0;
    const score = ((template.weight ?? 1) + BOOST[template.kind] + closeness) * (0.55 + rng());
    candidates.push({ hunch: { emoji: template.emoji, text, kind: template.kind, why: why(template, ctx, moment, weather, place, pack) }, names, score });
  }
  candidates.sort((a, b) => b.score - a.score);
  const taken: Partial<Record<Kind, number>> = {};
  // Each place, store or team appears once, so the list doesn't read like an ad for one of them
  const named = new Set<string>();
  for (const { hunch, names } of candidates) {
    if (hunches.length >= COUNT) break;
    if ((taken[hunch.kind] ?? 0) >= CAPS[hunch.kind]) continue;
    if (names.some((n) => named.has(n))) continue;
    taken[hunch.kind] = (taken[hunch.kind] ?? 0) + 1;
    names.forEach((n) => named.add(n));
    hunches.push(hunch);
  }
  const credits = weather?.today || weather?.now ? ["Weather data from MET Norway (CC BY 4.0)"] : [];
  if (weather?.alert) credits.push("Alerts from the US National Weather Service");
  return {
    place: { city: place.city, hood: place.hood, region: place.region, country: place.country },
    hunches,
    credits,
  };
}

class Slots {
  private readonly fahrenheit: boolean;

  constructor(
    private readonly place: HunchPlace,
    private readonly town: string | null,
    private readonly ctx: Ctx,
    private readonly chains: Chains,
    private readonly metro: Metro | null,
    private readonly pack: CityPack | null,
    private readonly weather: Weather | null,
  ) {
    this.fahrenheit = usesFahrenheit(place.country);
  }

  /** The template with every slot filled and the names it used, or null when a slot can't be filled. */
  fill(text: string, rng: () => number, moment: Moment | undefined): { text: string; names: string[] } | null {
    const pick = <T>(list: T[] | undefined | null): T | null => (list && list.length > 0 ? list[Math.floor(rng() * list.length)] : null);
    const newPlace = pick(this.pack?.newPlaces);
    const attraction = pick(this.pack?.newPlaces.filter((p) => ATTRACTION.test(p.kind)));
    let missing = false;
    const names: string[] = [];
    const out = text.replace(/\{(\w+)\}/g, (_, slot: string) => {
      const value = slot === "attraction" ? attraction?.name ?? null
        : slot === "attraction_town" ? attraction?.town ?? null
        : this.value(slot, pick, newPlace, moment);
      if (value == null || value === "") missing = true;
      else if (!SHARED_SLOTS.has(slot)) names.push(value);
      return value ?? "";
    });
    if (missing) return null;
    return { text: out.charAt(0).toUpperCase() + out.slice(1), names };
  }

  private value(
    slot: string,
    pick: <T>(list: T[] | undefined | null) => T | null,
    newPlace: CityPack["newPlaces"][number] | null,
    moment: Moment | undefined,
  ): string | null {
    const c = this.chains;
    const teams = this.metro?.teams;
    switch (slot) {
      case "city": return this.town;
      case "place": return this.place.hood ?? this.town;
      case "state": return this.place.region;
      case "bigbox": return pick(c.bigbox);
      case "grocery": return pick(c.grocery);
      case "coffee": return pick(c.coffee);
      case "burger": return pick(c.burger);
      case "pharmacy": return pick(c.pharmacy);
      case "hardware": return pick(c.hardware);
      case "stop": return pick(c.stop);
      case "storm": return pick(c.storm);
      case "airport": return this.metro?.airport ?? null;
      case "nfl": case "nba": case "mlb": case "nhl": case "mls": case "college":
        return this.ctx.inSeason.has(slot) ? pick(teams?.[slot as League]) : null;
      case "landmark": return pick(this.pack?.landmarks);
      case "park": return pick(this.pack?.parks);
      case "mall": return pick(this.pack?.shopping);
      case "food": return pick(this.pack?.foods);
      case "road": return pick(this.pack?.roads);
      case "event": return pick(this.pack?.events.filter((e) => e.month === this.ctx.month || e.month === (this.ctx.month % 12) + 1))?.name ?? null;
      case "newplace": return newPlace?.name ?? null;
      case "newplace_kind": return newPlace?.kind ?? null;
      case "newplace_town": return newPlace?.town ?? null;
      case "bigcity": {
        const big = this.pack?.nearestBigCity ?? this.metro?.name ?? null;
        return big && big !== this.town ? big : null;
      }
      case "temp": return this.weather?.now ? this.degrees(this.weather.now.temp) : null;
      case "high": return this.weather?.today ? this.degrees(this.weather.today.high) : null;
      case "low": return this.weather?.today ? this.degrees(this.weather.today.low) : null;
      case "alert": return this.weather?.alert?.event.toLowerCase() ?? null;
      case "days": return moment && moment.daysAway > 0 ? String(moment.daysAway) : null;
      case "weekday": return WEEKDAYS[this.ctx.weekday];
      default: return null;
    }
  }

  private degrees(celsius: number): string {
    return this.fahrenheit ? `${Math.round(celsius * 9 / 5 + 32)}°F` : `${Math.round(celsius)}°C`;
  }
}

/** The short reason shown under a hunch, so it is clear what it is based on. */
function why(template: Template, ctx: Ctx, moment: Moment | undefined, weather: Weather | null, place: HunchPlace, pack: CityPack | null): string {
  switch (template.kind) {
    case "weather":
      if (weather?.alert && ctx.alert) return `${weather.alert.event} from the National Weather Service`;
      return "Based on today's forecast";
    case "moment":
      if (!moment) return "Based on the calendar";
      if (moment.daysAway > 1) return `${cap(moment.name)} is in ${moment.daysAway} days`;
      if (moment.daysAway === 1) return `${cap(moment.name)} is tomorrow`;
      if (moment.daysAway === 0) return `${cap(moment.name)} is today`;
      if (moment.id === "resolutions" || moment.id === "enrollment" || moment.id === "march-madness" || moment.id === "graduation") {
        return `It's ${moment.name}`;
      }
      const ago = -moment.daysAway === 1 ? "yesterday" : `${-moment.daysAway} days ago`;
      return moment.past ? `${moment.past} ${ago}` : `${cap(moment.name)} was ${ago}`;
    case "season":
      return `It's ${ctx.season} in ${place.region ?? place.city ?? "your area"}`;
    case "local":
      return pack?.known ? `Based on what's around ${place.city ?? "you"}` : `Based on where you are`;
    case "sports":
      return "Based on who's playing this season";
    case "time":
      return `It's ${WEEKDAYS[ctx.weekday]} ${partOfDay(ctx.hour)}`;
    default:
      return "Something people ask all the time";
  }
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

function partOfDay(hour: number): string {
  if (hour < 5) return "night";
  if (hour < 12) return "morning";
  if (hour < 17) return "afternoon";
  if (hour < 21) return "evening";
  return "night";
}

function seasonOf(month: number, southern: boolean): Ctx["season"] {
  const m = southern ? ((month + 5) % 12) + 1 : month;
  if (m === 12 || m <= 2) return "winter";
  if (m <= 5) return "spring";
  if (m <= 8) return "summer";
  return "fall";
}

function localTime(now: Date, timeZone: string) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", {
      timeZone, year: "numeric", month: "numeric", day: "numeric", hour: "numeric", hourCycle: "h23", weekday: "short",
    }).formatToParts(now).map((p) => [p.type, p.value]),
  );
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour) % 24,
    weekday: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].indexOf(parts.weekday),
  };
}

/** Valid IANA zone or null. */
export function validTimeZone(zone: string | null | undefined): string | null {
  if (!zone) return null;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
    return zone;
  } catch {
    return null;
  }
}

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

/** Mulberry32: small, fast, deterministic. Everyone in the same town sees the same hunches for a few hours. */
function random(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let x = Math.imul(a ^ (a >>> 15), 1 | a);
    x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x;
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
  };
}
