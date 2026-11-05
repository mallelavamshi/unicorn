// Live weather for hunches. Alerts come from the US National Weather Service (public domain); the forecast comes from
// MET Norway (CC BY 4.0, worldwide). Both are free for commercial use and ask for an identifying User-Agent and
// caching, which `cached` provides. Coordinates are rounded to about 1 km before they leave the Worker.

import { cached, type Cache } from "./cache";

const USER_AGENT = "UnicornApp/0.4 (+https://unicorn-api.m-vamshikrishna22.workers.dev)";

export type Condition = "thunder" | "snow" | "rain" | "fog" | "cloudy" | "clear";

/** Alert families that hunches can play with. "serious" alerts get a plain safety line and no jokes. */
export type AlertKind = "storm" | "cold" | "heat" | "flood" | "wind" | "fog" | "air" | "serious";

export type Alert = { event: string; kind: AlertKind };

export type Day = { high: number; low: number; condition: Condition };

export type Weather = {
  alert: Alert | null;
  /** Temperatures are in °C; the engine converts. */
  now: { temp: number; condition: Condition } | null;
  today: Day | null;
  tomorrow: Day | null;
};

// Life-threatening alerts: never joked about
const SERIOUS = /tornado warning|tornado emergency|hurricane warning|flash flood (warning|emergency)|extreme wind|tsunami|storm surge warning|evacuation|shelter in place|fire warning|civil danger|earthquake|blizzard warning|extreme heat warning|ice storm warning/i;

function alertKind(event: string, severity: string): AlertKind | null {
  if (SERIOUS.test(event) || severity === "Extreme") return "serious";
  if (/thunderstorm|tornado watch|severe weather|hail/i.test(event)) return "storm";
  if (/winter|freeze|frost|wind chill|cold|snow|ice|sleet/i.test(event)) return "cold";
  if (/heat/i.test(event)) return "heat";
  if (/flood/i.test(event)) return "flood";
  if (/wind|gale/i.test(event)) return "wind";
  if (/fog/i.test(event)) return "fog";
  if (/air quality|smoke|dust/i.test(event)) return "air";
  return null;
}

const ALERT_ORDER: AlertKind[] = ["serious", "storm", "cold", "flood", "heat", "wind", "air", "fog"];

export async function weatherAt(cache: Cache, lat: number, lon: number, country: string, timeZone: string): Promise<Weather> {
  const la = lat.toFixed(2);
  const lo = lon.toFixed(2);
  const [alert, forecast] = await Promise.all([
    country === "US" ? cached(cache, `nws:${la},${lo}`, 900, () => nwsAlert(la, lo)).catch(() => null) : Promise.resolve(null),
    cached(cache, `met:${la},${lo}`, 1800, () => metForecast(la, lo)).catch(() => null),
  ]);
  return { alert, ...summarize(forecast ?? [], timeZone) };
}

async function nwsAlert(lat: string, lon: string): Promise<Alert | null> {
  const res = await fetch(`https://api.weather.gov/alerts/active?point=${lat},${lon}`, {
    headers: { "User-Agent": USER_AGENT, Accept: "application/geo+json" },
  });
  if (!res.ok) throw new Error(`nws ${res.status}`);
  const body = await res.json() as { features?: { properties?: { event?: string; severity?: string } }[] };
  const alerts = (body.features ?? []).flatMap((f) => {
    const event = f.properties?.event;
    const kind = event ? alertKind(event, f.properties?.severity ?? "") : null;
    return event && kind ? [{ event, kind }] : [];
  });
  alerts.sort((a, b) => ALERT_ORDER.indexOf(a.kind) - ALERT_ORDER.indexOf(b.kind));
  return alerts[0] ?? null;
}

type Point = { time: string; temp: number; symbol: string | null };

async function metForecast(lat: string, lon: string): Promise<Point[]> {
  const res = await fetch(`https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${lat}&lon=${lon}`, {
    headers: { "User-Agent": USER_AGENT },
  });
  if (!res.ok) throw new Error(`met ${res.status}`);
  const body = await res.json() as {
    properties?: {
      timeseries?: {
        time: string;
        data: {
          instant: { details: { air_temperature?: number } };
          next_1_hours?: { summary?: { symbol_code?: string } };
          next_6_hours?: { summary?: { symbol_code?: string } };
        };
      }[];
    };
  };
  // Only the next two days matter; keep the cached value small
  return (body.properties?.timeseries ?? []).slice(0, 60).flatMap((t) => {
    const temp = t.data.instant.details.air_temperature;
    if (temp == null) return [];
    return [{ time: t.time, temp, symbol: t.data.next_1_hours?.summary?.symbol_code ?? t.data.next_6_hours?.summary?.symbol_code ?? null }];
  });
}

function condition(symbol: string | null): Condition {
  const s = symbol ?? "";
  if (s.includes("thunder")) return "thunder";
  if (s.includes("snow") || s.includes("sleet")) return "snow";
  if (s.includes("rain")) return "rain";
  if (s.includes("fog")) return "fog";
  if (s.includes("cloudy") && !s.includes("partly")) return "cloudy";
  return "clear";
}

const CONDITION_ORDER: Condition[] = ["thunder", "snow", "rain", "fog", "cloudy", "clear"];

function summarize(points: Point[], timeZone: string): Omit<Weather, "alert"> {
  if (points.length === 0) return { now: null, today: null, tomorrow: null };
  const dayOf = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" });
  const days = new Map<string, Point[]>();
  for (const p of points) {
    const key = dayOf.format(new Date(p.time));
    days.set(key, [...(days.get(key) ?? []), p]);
  }
  const day = (list: Point[] | undefined): Day | null => {
    if (!list || list.length < 3) return null;
    const temps = list.map((p) => p.temp);
    // The day's most notable weather wins: one thunderstorm hour makes it a stormy day
    const worst = list.map((p) => condition(p.symbol)).sort((a, b) => CONDITION_ORDER.indexOf(a) - CONDITION_ORDER.indexOf(b))[0];
    return { high: Math.max(...temps), low: Math.min(...temps), condition: worst };
  };
  const [first, second] = [...days.values()];
  return {
    now: { temp: points[0].temp, condition: condition(points[0].symbol) },
    today: day(first),
    tomorrow: day(second),
  };
}
