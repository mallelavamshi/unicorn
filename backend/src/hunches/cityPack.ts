// What makes a town itself: its landmarks, parks, new openings, local food and the road everyone complains about.
// Claude Haiku writes it once per town and it is cached for two weeks, so a town costs about one request a fortnight.
// Only names of public places are asked for; templates decide the tone.

import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import * as z from "zod/v4";
import { cached, type Cache } from "./cache";

// Structured outputs don't enforce lengths, so the schema is loose and the lists are trimmed after parsing
const CityPack = z.object({
  /** False when the model doesn't know the town well enough; everything else is then empty. */
  known: z.boolean(),
  landmarks: z.array(z.string()),
  parks: z.array(z.string()),
  shopping: z.array(z.string()),
  newPlaces: z.array(z.object({ name: z.string(), kind: z.string(), town: z.string() })),
  foods: z.array(z.string()),
  roads: z.array(z.string()),
  events: z.array(z.object({ name: z.string(), month: z.number().int() })),
  nearestBigCity: z.string().nullable(),
});
export type CityPack = z.infer<typeof CityPack>;

const EMPTY: CityPack = { known: false, landmarks: [], parks: [], shopping: [], newPlaces: [], foods: [], roads: [], events: [], nearestBigCity: null };

const SYSTEM = `You write a fact sheet about one town for a playful local app. It lists public places and things locals talk about.

Rules:
- Only include real places you are highly confident exist in the town or within about 10 miles of it today. The town's own places come first; a famous place further away is not local. Prefer well-known, long-standing places. If you are unsure, leave it out; fewer is better than wrong.
- If you don't know the town, set known to false and leave every list empty.
- landmarks: famous spots, venues or attractions. parks: parks, lakes, trails. shopping: malls, shopping districts, markets.
- newPlaces: attractions, venues or big stores that opened in the last two years or are about to open, with what kind of place it is and the town it is in.
- foods: dishes or food styles the area is known for (not restaurant names), capitalized as they'd appear mid-sentence ("Texas barbecue", "Tex-Mex", "tacos"). roads: highways or roads locals complain about for traffic.
- events: recurring annual events or festivals with the month they usually happen.
- Never include people, schools, churches or other places of worship, hospitals, police, crimes, disasters, politics, or anything negative about a business.
- Names only, as locals write them, each under 60 characters.`;

export async function cityPack(
  cache: Cache,
  apiKey: string | undefined,
  model: string,
  place: { city: string; region: string | null; country: string },
  allowGenerate: () => Promise<boolean>,
): Promise<CityPack> {
  const key = `pack:v3:${place.country}:${(place.region ?? "").toLowerCase()}:${place.city.toLowerCase()}`;
  if (!apiKey) return EMPTY;
  try {
    return await cached(cache, key, 14 * 86_400, async () => {
      // A missing pack is only worth a request when the budget allows it; otherwise try again another day
      if (!(await allowGenerate())) throw new Error("pack budget");
      return generate(apiKey, model, place);
    });
  } catch (e) {
    if ((e as Error).message !== "pack budget") console.error("city pack failed", (e as Error).name, (e as Error).message.slice(0, 300));
    return EMPTY;
  }
}

async function generate(apiKey: string, model: string, place: { city: string; region: string | null; country: string }): Promise<CityPack> {
  const client = new Anthropic({ apiKey, maxRetries: 1, timeout: 20_000 });
  const where = [place.city, place.region, place.country].filter(Boolean).join(", ");
  const response = await client.messages.parse({
    model,
    max_tokens: 1200,
    system: SYSTEM,
    thinking: { type: "disabled" },
    output_config: { effort: "low", format: zodOutputFormat(CityPack) },
    messages: [{ role: "user", content: `Town: ${where}` }],
  });
  const pack = response.parsed_output;
  if (!pack) throw new Error("unparseable pack");
  const clean = (s: string) => s.replace(/[^\p{L}\p{N} '&.,\-()]/gu, "").trim().slice(0, 60);
  const names = (list: string[], max: number) => list.map(clean).filter((s) => s.length >= 3).slice(0, max);
  if (!pack.known) return EMPTY;
  return {
    known: true,
    landmarks: names(pack.landmarks, 5),
    parks: names(pack.parks, 4),
    shopping: names(pack.shopping, 4),
    newPlaces: pack.newPlaces
      .map((p) => ({ name: clean(p.name), kind: clean(p.kind).toLowerCase(), town: clean(p.town) }))
      .filter((p) => p.name.length >= 3 && p.town.length >= 2).slice(0, 4),
    foods: names(pack.foods, 4),
    roads: names(pack.roads, 3),
    events: pack.events.filter((e) => e.month >= 1 && e.month <= 12)
      .map((e) => ({ name: clean(e.name), month: e.month })).filter((e) => e.name.length >= 3).slice(0, 5),
    nearestBigCity: pack.nearestBigCity ? clean(pack.nearestBigCity) || null : null,
  };
}
