// The calendar moments people plan around: holidays, tax day, the clocks changing, the big game. All computed, no
// lookups. A moment is "active" from `before` days ahead until `after` days past.

export type Moment = { id: string; name: string; daysAway: number; past?: string };

type Def = {
  id: string;
  name: string;
  date: (year: number) => Date | null;
  before: number;
  after: number;
  /** Countries that mark it; everywhere when missing. */
  countries?: string[];
  /** How to say it happened, when "X was 2 days ago" reads oddly. */
  past?: string;
};

const utc = (y: number, m: number, d: number) => new Date(Date.UTC(y, m - 1, d));

/** The nth (1-based) weekday (0 = Sunday) of a month. */
function nth(y: number, m: number, weekday: number, n: number): Date {
  const first = utc(y, m, 1).getUTCDay();
  return utc(y, m, 1 + ((weekday - first + 7) % 7) + (n - 1) * 7);
}

function last(y: number, m: number, weekday: number): Date {
  const end = new Date(Date.UTC(y, m, 0));
  return utc(y, m, end.getUTCDate() - ((end.getUTCDay() - weekday + 7) % 7));
}

const plus = (d: Date, days: number) => new Date(d.getTime() + days * 86_400_000);

/** Western Easter (anonymous Gregorian algorithm). */
function easter(y: number): Date {
  const a = y % 19, b = Math.floor(y / 100), c = y % 100, d = Math.floor(b / 4), e = b % 4;
  const f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  return utc(y, month, ((h + l - 7 * m + 114) % 31) + 1);
}

const thanksgiving = (y: number) => nth(y, 11, 4, 4);
const US = ["US"];
const NA = ["US", "CA"];
const WEST = ["US", "CA", "GB", "IE", "AU", "NZ"];
const UK = ["GB", "IE"];

const DEFS: Def[] = [
  { id: "newyear", name: "New Year's Eve", date: (y) => utc(y, 12, 31), before: 6, after: 1 },
  { id: "resolutions", name: "New Year's resolutions season", date: (y) => utc(y, 1, 1), before: 0, after: 24 },
  { id: "mlk", name: "MLK Day", date: (y) => nth(y, 1, 1, 3), before: 3, after: 0, countries: US },
  { id: "superbowl", name: "Super Bowl Sunday", date: (y) => nth(y, 2, 0, 2), before: 12, after: 1, countries: NA },
  { id: "valentines", name: "Valentine's Day", date: (y) => utc(y, 2, 14), before: 12, after: 0 },
  { id: "presidents", name: "Presidents' Day weekend", date: (y) => nth(y, 2, 1, 3), before: 4, after: 0, countries: US },
  { id: "dst-start", name: "the clocks going forward", past: "The clocks went forward", date: (y) => nth(y, 3, 0, 2), before: 3, after: 4, countries: NA },
  { id: "dst-start-eu", name: "the clocks going forward", past: "The clocks went forward", date: (y) => last(y, 3, 0), before: 3, after: 3, countries: UK },
  { id: "stpatricks", name: "St. Patrick's Day", date: (y) => utc(y, 3, 17), before: 7, after: 0, countries: WEST },
  { id: "march-madness", name: "March Madness", date: (y) => utc(y, 3, 17), before: 4, after: 20, countries: US },
  { id: "easter", name: "Easter", date: easter, before: 10, after: 0 },
  { id: "taxday", name: "Tax Day", date: (y) => utc(y, 4, 15), before: 21, after: 0, countries: US },
  { id: "mothers-uk", name: "Mother's Day", date: (y) => plus(easter(y), -21), before: 10, after: 0, countries: UK },
  { id: "mothers", name: "Mother's Day", date: (y) => nth(y, 5, 0, 2), before: 12, after: 0, countries: ["US", "CA", "AU", "NZ", "IN"] },
  { id: "graduation", name: "graduation season", date: (y) => utc(y, 5, 20), before: 14, after: 20, countries: US },
  { id: "memorial", name: "Memorial Day weekend", date: (y) => last(y, 5, 1), before: 7, after: 0, countries: US },
  { id: "fathers", name: "Father's Day", date: (y) => nth(y, 6, 0, 3), before: 12, after: 0, countries: ["US", "CA", "GB", "IE", "IN"] },
  { id: "juneteenth", name: "Juneteenth", date: (y) => utc(y, 6, 19), before: 3, after: 0, countries: US },
  { id: "canada-day", name: "Canada Day", date: (y) => utc(y, 7, 1), before: 5, after: 0, countries: ["CA"] },
  { id: "july4", name: "the Fourth of July", date: (y) => utc(y, 7, 4), before: 10, after: 1, countries: US },
  { id: "back-to-school", name: "back-to-school season", date: (y) => utc(y, 8, 12), before: 18, after: 18, countries: NA },
  { id: "labor", name: "Labor Day weekend", date: (y) => nth(y, 9, 1, 1), before: 6, after: 0, countries: NA },
  { id: "thanksgiving-ca", name: "Thanksgiving", date: (y) => nth(y, 10, 1, 2), before: 10, after: 0, countries: ["CA"] },
  { id: "halloween", name: "Halloween", date: (y) => utc(y, 10, 31), before: 24, after: 3, countries: WEST },
  { id: "dst-end", name: "the clocks going back", past: "The clocks went back", date: (y) => nth(y, 11, 0, 1), before: 3, after: 9, countries: NA },
  { id: "dst-end-eu", name: "the clocks going back", past: "The clocks went back", date: (y) => last(y, 10, 0), before: 3, after: 9, countries: UK },
  { id: "election", name: "Election Day", date: (y) => (y % 2 === 0 ? plus(nth(y, 11, 1, 1), 1) : null), before: 6, after: 0, countries: US },
  { id: "enrollment", name: "open enrollment", date: (y) => utc(y, 11, 1), before: 0, after: 44, countries: US },
  { id: "bonfire", name: "Bonfire Night", date: (y) => utc(y, 11, 5), before: 4, after: 0, countries: ["GB"] },
  { id: "veterans", name: "Veterans Day", date: (y) => utc(y, 11, 11), before: 2, after: 0, countries: US },
  { id: "thanksgiving", name: "Thanksgiving", date: thanksgiving, before: 25, after: 1, countries: US },
  { id: "blackfriday", name: "Black Friday", date: (y) => plus(thanksgiving(y), 1), before: 10, after: 3, countries: WEST },
  { id: "christmas", name: "Christmas", date: (y) => utc(y, 12, 25), before: 32, after: 1 },
  { id: "diwali", name: "Diwali", date: (y) => ({ 2026: utc(2026, 11, 8), 2027: utc(2027, 10, 29), 2028: utc(2028, 10, 17) } as Record<number, Date>)[y] ?? null, before: 14, after: 1, countries: ["IN", "US", "GB", "CA"] },
];

/** Moments active on a local date (year, month 1-12, day), nearest first. */
export function momentsOn(country: string, year: number, month: number, day: number): Moment[] {
  const today = utc(year, month, day).getTime();
  const out: Moment[] = [];
  for (const def of DEFS) {
    if (def.countries && !def.countries.includes(country)) continue;
    // A moment early next year (or late last year) can already be in its window
    for (const y of [year - 1, year, year + 1]) {
      const date = def.date(y);
      if (!date) continue;
      const daysAway = Math.round((date.getTime() - today) / 86_400_000);
      if (daysAway <= def.before && -daysAway <= def.after) out.push({ id: def.id, name: def.name, daysAway, past: def.past });
    }
  }
  return out.sort((a, b) => Math.abs(a.daysAway) - Math.abs(b.daysAway));
}
