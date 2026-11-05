// Regional context for hunches: the chains people actually shop at, the teams they follow and the airport they
// complain about, by country, state and metro. Hand-curated, so a hunch never needs a live search to sound local.

export type Chains = {
  bigbox: string[];
  grocery: string[];
  coffee: string[];
  burger: string[];
  pharmacy: string[];
  hardware: string[];
  /** The road-trip stop people have opinions about (Buc-ee's, Wawa, Sheetz). */
  stop: string[];
  /** Chains that are part of local storm lore, like Waffle House in the South. */
  storm: string[];
};

const US_DEFAULT: Chains = {
  bigbox: ["Walmart", "Target", "Costco"],
  grocery: ["Kroger", "Trader Joe's", "Aldi"],
  coffee: ["Starbucks"],
  burger: ["McDonald's", "Chick-fil-A"],
  pharmacy: ["CVS", "Walgreens"],
  hardware: ["Home Depot", "Lowe's"],
  stop: [],
  storm: [],
};

// Only the differences from US_DEFAULT; lists replace the default list for that state
const US_STATES: Record<string, Partial<Chains>> = {
  TX: { grocery: ["H-E-B", "Kroger", "Tom Thumb"], burger: ["Whataburger", "Chick-fil-A", "In-N-Out"], coffee: ["Starbucks", "Dutch Bros"], stop: ["Buc-ee's"], storm: ["Waffle House"] },
  FL: { grocery: ["Publix", "Winn-Dixie"], coffee: ["Starbucks", "Dunkin'"], stop: ["Wawa", "Buc-ee's"], storm: ["Waffle House"] },
  GA: { grocery: ["Publix", "Kroger"], burger: ["Chick-fil-A", "Zaxby's"], stop: ["QuikTrip", "Buc-ee's"], storm: ["Waffle House"] },
  AL: { grocery: ["Publix", "Winn-Dixie"], burger: ["Chick-fil-A", "Milo's"], stop: ["Buc-ee's"], storm: ["Waffle House"] },
  SC: { grocery: ["Publix", "Food Lion"], burger: ["Chick-fil-A", "Bojangles"], stop: ["QuikTrip"], storm: ["Waffle House"] },
  NC: { grocery: ["Harris Teeter", "Food Lion", "Publix"], burger: ["Bojangles", "Cook Out", "Chick-fil-A"], stop: ["Sheetz", "QuikTrip"], storm: ["Waffle House"] },
  TN: { grocery: ["Kroger", "Publix"], burger: ["Chick-fil-A", "Krystal"], stop: ["Buc-ee's"], storm: ["Waffle House"] },
  VA: { grocery: ["Wegmans", "Harris Teeter", "Food Lion"], burger: ["Chick-fil-A", "Five Guys"], stop: ["Wawa", "Sheetz"] },
  MS: { grocery: ["Kroger"], storm: ["Waffle House"] },
  LA: { grocery: ["Rouses", "Winn-Dixie"], burger: ["Raising Cane's", "Popeyes"], storm: ["Waffle House"] },
  AR: { grocery: ["Kroger", "Harps"], storm: ["Waffle House"] },
  KY: { grocery: ["Kroger", "Meijer"], burger: ["Chick-fil-A", "Culver's"], stop: ["Buc-ee's"], storm: ["Waffle House"] },
  OK: { grocery: ["Reasor's", "Crest"], burger: ["Whataburger", "Braum's"], stop: ["QuikTrip", "Love's"] },
  CA: { grocery: ["Trader Joe's", "Ralphs", "Safeway"], burger: ["In-N-Out"], coffee: ["Starbucks", "Philz", "Dutch Bros"] },
  NV: { grocery: ["Smith's", "Albertsons"], burger: ["In-N-Out"], coffee: ["Starbucks", "Dutch Bros"] },
  AZ: { grocery: ["Fry's", "Safeway", "Sprouts"], burger: ["In-N-Out", "Whataburger"], coffee: ["Dutch Bros", "Starbucks"], stop: ["QuikTrip"] },
  UT: { grocery: ["Smith's", "Harmons"], burger: ["In-N-Out", "Crown Burgers"], coffee: ["Swig", "Starbucks"] },
  NM: { grocery: ["Smith's", "Albertsons"], burger: ["Blake's Lotaburger", "Whataburger"] },
  CO: { grocery: ["King Soopers", "Safeway"], burger: ["In-N-Out", "Good Times"], coffee: ["Dutch Bros", "Starbucks"] },
  ID: { grocery: ["WinCo", "Albertsons"], burger: ["In-N-Out"], coffee: ["Dutch Bros"] },
  WA: { grocery: ["Fred Meyer", "QFC", "Safeway"], burger: ["Dick's Drive-In", "Burgerville"], coffee: ["Starbucks", "Dutch Bros"] },
  OR: { grocery: ["Fred Meyer", "New Seasons", "Safeway"], burger: ["Burgerville", "In-N-Out"], coffee: ["Dutch Bros", "Starbucks"] },
  IL: { grocery: ["Jewel-Osco", "Mariano's", "Aldi"], burger: ["Portillo's", "Culver's"], coffee: ["Dunkin'", "Starbucks"] },
  IN: { grocery: ["Kroger", "Meijer"], burger: ["Culver's", "Steak 'n Shake"] },
  MI: { grocery: ["Meijer", "Kroger"], burger: ["Culver's", "Coney Island"], coffee: ["Biggby", "Tim Hortons"] },
  OH: { grocery: ["Kroger", "Giant Eagle", "Meijer"], burger: ["Skyline Chili", "Wendy's"], stop: ["Sheetz"] },
  PA: { grocery: ["Wegmans", "Giant", "Giant Eagle"], coffee: ["Wawa", "Dunkin'"], stop: ["Wawa", "Sheetz"] },
  NY: { grocery: ["Wegmans", "Trader Joe's", "Stop & Shop"], coffee: ["Dunkin'", "Starbucks"], burger: ["Shake Shack", "McDonald's"] },
  NJ: { grocery: ["ShopRite", "Wegmans"], coffee: ["Dunkin'", "Wawa"], stop: ["Wawa", "QuickChek"] },
  MA: { grocery: ["Market Basket", "Stop & Shop", "Trader Joe's"], coffee: ["Dunkin'"], burger: ["Wendy's", "Five Guys"] },
  CT: { grocery: ["Stop & Shop", "Big Y"], coffee: ["Dunkin'"] },
  RI: { grocery: ["Stop & Shop", "Dave's Marketplace"], coffee: ["Dunkin'", "Dave's Coffee"] },
  NH: { grocery: ["Market Basket", "Hannaford"], coffee: ["Dunkin'"] },
  ME: { grocery: ["Hannaford", "Shaw's"], coffee: ["Dunkin'"] },
  VT: { grocery: ["Price Chopper", "Hannaford"], coffee: ["Dunkin'"] },
  MD: { grocery: ["Giant", "Wegmans", "Safeway"], stop: ["Royal Farms", "Wawa"] },
  DE: { grocery: ["Acme", "Giant"], stop: ["Wawa"] },
  DC: { grocery: ["Giant", "Safeway", "Trader Joe's"], burger: ["Shake Shack", "Five Guys"] },
  WV: { grocery: ["Kroger"], stop: ["Sheetz"] },
  MN: { grocery: ["Cub", "Hy-Vee", "Lunds & Byerlys"], burger: ["Culver's"], stop: ["Kwik Trip", "Holiday"] },
  WI: { grocery: ["Pick 'n Save", "Woodman's"], burger: ["Culver's"], stop: ["Kwik Trip"] },
  IA: { grocery: ["Hy-Vee", "Fareway"], burger: ["Culver's"], stop: ["Casey's", "Kwik Star"] },
  MO: { grocery: ["Schnucks", "Hy-Vee", "Dierbergs"], burger: ["Culver's", "Steak 'n Shake"], stop: ["QuikTrip", "Casey's"] },
  KS: { grocery: ["Dillons", "Hy-Vee"], burger: ["Freddy's", "Culver's"], stop: ["QuikTrip", "Casey's"] },
  NE: { grocery: ["Hy-Vee", "Baker's"], burger: ["Runza", "Culver's"], stop: ["Casey's"] },
  SD: { grocery: ["Hy-Vee"], burger: ["Culver's"], stop: ["Casey's"] },
  ND: { grocery: ["Hornbacher's"], burger: ["Culver's"] },
  MT: { grocery: ["Albertsons", "Town & Country"] },
  WY: { grocery: ["Smith's", "Albertsons"] },
  HI: { grocery: ["Foodland", "Safeway"], burger: ["L&L Hawaiian Barbecue", "Zippy's"] },
  AK: { grocery: ["Fred Meyer", "Carrs"] },
};

const COUNTRIES: Record<string, Chains> = {
  CA: {
    bigbox: ["Costco", "Walmart", "Canadian Tire"], grocery: ["Loblaws", "Sobeys", "Metro", "No Frills"],
    coffee: ["Tim Hortons"], burger: ["A&W", "Harvey's"], pharmacy: ["Shoppers Drug Mart"], hardware: ["Canadian Tire", "Home Depot"],
    stop: [], storm: [],
  },
  GB: {
    bigbox: ["Tesco", "Asda"], grocery: ["Tesco", "Sainsbury's", "Aldi", "Lidl"], coffee: ["Costa", "Pret"],
    burger: ["Greggs", "McDonald's"], pharmacy: ["Boots"], hardware: ["B&Q"], stop: [], storm: [],
  },
  IE: {
    bigbox: ["Tesco", "Dunnes"], grocery: ["Tesco", "Dunnes", "Lidl"], coffee: ["Insomnia"], burger: ["Supermac's"],
    pharmacy: ["Boots"], hardware: ["Woodie's"], stop: ["Applegreen"], storm: [],
  },
  AU: {
    bigbox: ["Kmart", "Big W"], grocery: ["Woolworths", "Coles", "Aldi"], coffee: ["the local café"], burger: ["Hungry Jack's"],
    pharmacy: ["Chemist Warehouse"], hardware: ["Bunnings"], stop: [], storm: [],
  },
  NZ: {
    bigbox: ["The Warehouse", "Kmart"], grocery: ["Pak'nSave", "Woolworths", "New World"], coffee: ["the local café"],
    burger: ["BurgerFuel"], pharmacy: ["Chemist Warehouse"], hardware: ["Bunnings", "Mitre 10"], stop: [], storm: [],
  },
  IN: {
    bigbox: ["DMart", "Reliance Smart"], grocery: ["DMart", "Blinkit", "Zepto"], coffee: ["Starbucks", "Third Wave"],
    burger: ["McDonald's", "Burger Singh"], pharmacy: ["Apollo Pharmacy"], hardware: ["the local hardware shop"], stop: [], storm: [],
  },
};

const GENERIC: Chains = {
  bigbox: ["the supermarket"], grocery: ["the supermarket"], coffee: ["the corner café"], burger: ["the burger place"],
  pharmacy: ["the pharmacy"], hardware: ["the hardware store"], stop: [], storm: [],
};

export function chainsFor(country: string, regionCode: string | null): Chains {
  if (country === "US") return { ...US_DEFAULT, ...(regionCode ? US_STATES[regionCode] : undefined) };
  return COUNTRIES[country] ?? GENERIC;
}

// Climate: which weather a place jokes about, and what it worries about by season -------------------------------------

export type Climate = "hurricane" | "tornado" | "wildfire" | "snow" | "monsoon" | "earthquake" | "humid" | "desert" | "rain";

const US_CLIMATE: Record<string, Climate[]> = {
  TX: ["tornado", "hurricane", "humid"], FL: ["hurricane", "humid"], LA: ["hurricane", "humid"], MS: ["hurricane", "tornado", "humid"],
  AL: ["hurricane", "tornado", "humid"], GA: ["humid", "hurricane"], SC: ["hurricane", "humid"], NC: ["hurricane", "humid"],
  OK: ["tornado"], KS: ["tornado"], NE: ["tornado", "snow"], IA: ["tornado", "snow"], MO: ["tornado"], AR: ["tornado", "humid"],
  TN: ["tornado", "humid"], KY: ["tornado"], CA: ["wildfire", "earthquake"], OR: ["wildfire", "rain"], WA: ["rain", "earthquake"],
  AZ: ["desert", "monsoon"], NV: ["desert"], NM: ["desert", "monsoon"], UT: ["snow", "desert"], CO: ["snow"], ID: ["snow"],
  MT: ["snow"], WY: ["snow"], MN: ["snow"], WI: ["snow"], MI: ["snow"], ND: ["snow"], SD: ["snow"], IL: ["snow", "tornado"],
  IN: ["snow", "tornado"], OH: ["snow"], PA: ["snow"], NY: ["snow"], NJ: ["snow", "hurricane"], MA: ["snow"], CT: ["snow"],
  RI: ["snow"], NH: ["snow"], VT: ["snow"], ME: ["snow"], MD: ["snow", "humid"], DE: ["snow"], DC: ["humid"], VA: ["humid"],
  WV: ["snow"], HI: ["humid"], AK: ["snow"],
};

export function climateFor(country: string, regionCode: string | null): Climate[] {
  if (country === "US") return (regionCode && US_CLIMATE[regionCode]) || [];
  if (country === "CA") return ["snow"];
  if (country === "GB" || country === "IE") return ["rain"];
  if (country === "AU") return ["wildfire", "humid"];
  if (country === "IN") return ["monsoon", "humid"];
  return [];
}

/** Southern-hemisphere countries get their seasons the other way round. */
const SOUTH = new Set(["AU", "NZ", "ZA", "AR", "BR", "CL", "UY", "PY", "PE", "BO"]);
export const isSouthern = (country: string) => SOUTH.has(country);

/** Fahrenheit countries. */
export const usesFahrenheit = (country: string) => country === "US" || country === "LR" || country === "PR";

// Metros: teams and airports. Centres and radii in km; a town belongs to the nearest metro whose radius covers it ------

export type League = "nfl" | "nba" | "mlb" | "nhl" | "mls" | "college";
export type Metro = { name: string; lat: number; lon: number; km: number; airport: string; teams: Partial<Record<League, string[]>> };

const METROS: Metro[] = [
  { name: "New York", lat: 40.71, lon: -74.01, km: 60, airport: "JFK", teams: { nfl: ["Giants", "Jets"], nba: ["Knicks", "Nets"], mlb: ["Yankees", "Mets"], nhl: ["Rangers", "Islanders", "Devils"], mls: ["NYCFC", "Red Bulls"] } },
  { name: "Los Angeles", lat: 34.05, lon: -118.24, km: 70, airport: "LAX", teams: { nfl: ["Rams", "Chargers"], nba: ["Lakers", "Clippers"], mlb: ["Dodgers", "Angels"], nhl: ["Kings", "Ducks"], mls: ["LAFC", "Galaxy"] } },
  { name: "Chicago", lat: 41.88, lon: -87.63, km: 60, airport: "O'Hare", teams: { nfl: ["Bears"], nba: ["Bulls"], mlb: ["Cubs", "White Sox"], nhl: ["Blackhawks"], mls: ["Chicago Fire"] } },
  { name: "Dallas", lat: 32.85, lon: -96.95, km: 75, airport: "DFW", teams: { nfl: ["Cowboys"], nba: ["Mavericks"], mlb: ["Rangers"], nhl: ["Stars"], mls: ["FC Dallas"] } },
  { name: "Houston", lat: 29.76, lon: -95.37, km: 60, airport: "IAH", teams: { nfl: ["Texans"], nba: ["Rockets"], mlb: ["Astros"], mls: ["Dynamo"] } },
  { name: "Austin", lat: 30.27, lon: -97.74, km: 40, airport: "AUS", teams: { college: ["Longhorns"], mls: ["Austin FC"] } },
  { name: "San Antonio", lat: 29.42, lon: -98.49, km: 45, airport: "SAT", teams: { nba: ["Spurs"] } },
  { name: "Washington", lat: 38.9, lon: -77.04, km: 50, airport: "Dulles", teams: { nfl: ["Commanders"], nba: ["Wizards"], mlb: ["Nationals"], nhl: ["Capitals"], mls: ["D.C. United"] } },
  { name: "Baltimore", lat: 39.29, lon: -76.61, km: 35, airport: "BWI", teams: { nfl: ["Ravens"], mlb: ["Orioles"] } },
  { name: "Philadelphia", lat: 39.95, lon: -75.17, km: 45, airport: "PHL", teams: { nfl: ["Eagles"], nba: ["76ers"], mlb: ["Phillies"], nhl: ["Flyers"], mls: ["Union"] } },
  { name: "Pittsburgh", lat: 40.44, lon: -79.99, km: 40, airport: "PIT", teams: { nfl: ["Steelers"], mlb: ["Pirates"], nhl: ["Penguins"] } },
  { name: "Boston", lat: 42.36, lon: -71.06, km: 50, airport: "Logan", teams: { nfl: ["Patriots"], nba: ["Celtics"], mlb: ["Red Sox"], nhl: ["Bruins"], mls: ["Revolution"] } },
  { name: "Buffalo", lat: 42.89, lon: -78.88, km: 35, airport: "BUF", teams: { nfl: ["Bills"], nhl: ["Sabres"] } },
  { name: "Miami", lat: 25.76, lon: -80.19, km: 50, airport: "MIA", teams: { nfl: ["Dolphins"], nba: ["Heat"], mlb: ["Marlins"], nhl: ["Panthers"], mls: ["Inter Miami"] } },
  { name: "Tampa", lat: 27.95, lon: -82.46, km: 50, airport: "TPA", teams: { nfl: ["Buccaneers"], mlb: ["Rays"], nhl: ["Lightning"] } },
  { name: "Orlando", lat: 28.54, lon: -81.38, km: 45, airport: "MCO", teams: { nba: ["Magic"], mls: ["Orlando City"] } },
  { name: "Jacksonville", lat: 30.33, lon: -81.66, km: 40, airport: "JAX", teams: { nfl: ["Jaguars"] } },
  { name: "Atlanta", lat: 33.75, lon: -84.39, km: 60, airport: "Hartsfield-Jackson", teams: { nfl: ["Falcons"], nba: ["Hawks"], mlb: ["Braves"], mls: ["Atlanta United"] } },
  { name: "Charlotte", lat: 35.23, lon: -80.84, km: 45, airport: "CLT", teams: { nfl: ["Panthers"], nba: ["Hornets"], mls: ["Charlotte FC"] } },
  { name: "Raleigh", lat: 35.78, lon: -78.64, km: 40, airport: "RDU", teams: { nhl: ["Hurricanes"] } },
  { name: "Nashville", lat: 36.16, lon: -86.78, km: 45, airport: "BNA", teams: { nfl: ["Titans"], nhl: ["Predators"], mls: ["Nashville SC"] } },
  { name: "Memphis", lat: 35.15, lon: -90.05, km: 35, airport: "MEM", teams: { nba: ["Grizzlies"] } },
  { name: "New Orleans", lat: 29.95, lon: -90.07, km: 40, airport: "MSY", teams: { nfl: ["Saints"], nba: ["Pelicans"] } },
  { name: "Detroit", lat: 42.33, lon: -83.05, km: 45, airport: "DTW", teams: { nfl: ["Lions"], nba: ["Pistons"], mlb: ["Tigers"], nhl: ["Red Wings"] } },
  { name: "Cleveland", lat: 41.5, lon: -81.69, km: 40, airport: "CLE", teams: { nfl: ["Browns"], nba: ["Cavaliers"], mlb: ["Guardians"] } },
  { name: "Columbus", lat: 39.96, lon: -83.0, km: 40, airport: "CMH", teams: { college: ["Buckeyes"], nhl: ["Blue Jackets"], mls: ["Crew"] } },
  { name: "Cincinnati", lat: 39.1, lon: -84.51, km: 40, airport: "CVG", teams: { nfl: ["Bengals"], mlb: ["Reds"], mls: ["FC Cincinnati"] } },
  { name: "Indianapolis", lat: 39.77, lon: -86.16, km: 40, airport: "IND", teams: { nfl: ["Colts"], nba: ["Pacers"] } },
  { name: "Milwaukee", lat: 43.04, lon: -87.91, km: 35, airport: "MKE", teams: { nba: ["Bucks"], mlb: ["Brewers"] } },
  { name: "Green Bay", lat: 44.51, lon: -88.01, km: 30, airport: "GRB", teams: { nfl: ["Packers"] } },
  { name: "Minneapolis", lat: 44.98, lon: -93.27, km: 50, airport: "MSP", teams: { nfl: ["Vikings"], nba: ["Timberwolves"], mlb: ["Twins"], nhl: ["Wild"], mls: ["Minnesota United"] } },
  { name: "St. Louis", lat: 38.63, lon: -90.2, km: 45, airport: "STL", teams: { mlb: ["Cardinals"], nhl: ["Blues"], mls: ["St. Louis City"] } },
  { name: "Kansas City", lat: 39.1, lon: -94.58, km: 45, airport: "MCI", teams: { nfl: ["Chiefs"], mlb: ["Royals"], mls: ["Sporting KC"] } },
  { name: "Oklahoma City", lat: 35.47, lon: -97.52, km: 40, airport: "OKC", teams: { nba: ["Thunder"] } },
  { name: "Denver", lat: 39.74, lon: -104.99, km: 50, airport: "DIA", teams: { nfl: ["Broncos"], nba: ["Nuggets"], mlb: ["Rockies"], nhl: ["Avalanche"], mls: ["Rapids"] } },
  { name: "Salt Lake City", lat: 40.76, lon: -111.89, km: 45, airport: "SLC", teams: { nba: ["Jazz"], nhl: ["Mammoth"], mls: ["Real Salt Lake"] } },
  { name: "Phoenix", lat: 33.45, lon: -112.07, km: 60, airport: "Sky Harbor", teams: { nfl: ["Cardinals"], nba: ["Suns"], mlb: ["Diamondbacks"] } },
  { name: "Las Vegas", lat: 36.17, lon: -115.14, km: 40, airport: "Harry Reid", teams: { nfl: ["Raiders"], nhl: ["Golden Knights"] } },
  { name: "San Diego", lat: 32.72, lon: -117.16, km: 40, airport: "San Diego airport", teams: { mlb: ["Padres"], mls: ["San Diego FC"] } },
  { name: "San Francisco", lat: 37.77, lon: -122.42, km: 40, airport: "SFO", teams: { nfl: ["49ers"], nba: ["Warriors"], mlb: ["Giants"] } },
  { name: "San Jose", lat: 37.34, lon: -121.89, km: 30, airport: "SJC", teams: { nfl: ["49ers"], nhl: ["Sharks"], mls: ["Earthquakes"] } },
  { name: "Sacramento", lat: 38.58, lon: -121.49, km: 40, airport: "SMF", teams: { nba: ["Kings"], mlb: ["A's"] } },
  { name: "Portland", lat: 45.52, lon: -122.68, km: 40, airport: "PDX", teams: { nba: ["Trail Blazers"], mls: ["Timbers"] } },
  { name: "Seattle", lat: 47.61, lon: -122.33, km: 50, airport: "Sea-Tac", teams: { nfl: ["Seahawks"], mlb: ["Mariners"], nhl: ["Kraken"], mls: ["Sounders"] } },
  { name: "Toronto", lat: 43.65, lon: -79.38, km: 50, airport: "Pearson", teams: { nhl: ["Maple Leafs"], nba: ["Raptors"], mlb: ["Blue Jays"], mls: ["Toronto FC"] } },
  { name: "Montreal", lat: 45.5, lon: -73.57, km: 40, airport: "YUL", teams: { nhl: ["Canadiens"], mls: ["CF Montréal"] } },
  { name: "Vancouver", lat: 49.28, lon: -123.12, km: 35, airport: "YVR", teams: { nhl: ["Canucks"], mls: ["Whitecaps"] } },
  { name: "Calgary", lat: 51.05, lon: -114.07, km: 35, airport: "YYC", teams: { nhl: ["Flames"] } },
  { name: "Edmonton", lat: 53.55, lon: -113.49, km: 35, airport: "YEG", teams: { nhl: ["Oilers"] } },
  { name: "London", lat: 51.51, lon: -0.13, km: 40, airport: "Heathrow", teams: {} },
  { name: "Manchester", lat: 53.48, lon: -2.24, km: 30, airport: "Manchester Airport", teams: {} },
  { name: "Sydney", lat: -33.87, lon: 151.21, km: 50, airport: "Sydney Airport", teams: {} },
  { name: "Melbourne", lat: -37.81, lon: 144.96, km: 50, airport: "Tullamarine", teams: {} },
  { name: "Dublin", lat: 53.35, lon: -6.26, km: 30, airport: "Dublin Airport", teams: {} },
];

export function metroFor(lat: number | null, lon: number | null): Metro | null {
  if (lat == null || lon == null) return null;
  let best: Metro | null = null;
  let bestKm = Infinity;
  for (const metro of METROS) {
    const km = distanceKm(lat, lon, metro.lat, metro.lon);
    if (km <= metro.km && km < bestKm) {
      best = metro;
      bestKm = km;
    }
  }
  return best;
}

function distanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rad = Math.PI / 180;
  const a = Math.sin(((lat2 - lat1) * rad) / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(((lon2 - lon1) * rad) / 2) ** 2;
  return 12_742 * Math.asin(Math.sqrt(a));
}

/** Rough season windows as [month, day] pairs, so a team is only talked about while it plays. */
const SEASONS: Record<League, [number, number, number, number]> = {
  nfl: [9, 4, 2, 12],
  college: [8, 29, 1, 20],
  nba: [10, 20, 6, 20],
  nhl: [10, 6, 6, 20],
  mlb: [3, 25, 11, 2],
  mls: [2, 20, 12, 8],
};

export function inSeason(league: League, month: number, day: number): boolean {
  const [m1, d1, m2, d2] = SEASONS[league];
  const at = month * 100 + day;
  const start = m1 * 100 + d1;
  const end = m2 * 100 + d2;
  return start <= end ? at >= start && at <= end : at >= start || at <= end;
}

const US_STATE_CODES: Record<string, string> = {
  alabama: "AL", alaska: "AK", arizona: "AZ", arkansas: "AR", california: "CA", colorado: "CO", connecticut: "CT",
  delaware: "DE", "district of columbia": "DC", florida: "FL", georgia: "GA", hawaii: "HI", idaho: "ID", illinois: "IL",
  indiana: "IN", iowa: "IA", kansas: "KS", kentucky: "KY", louisiana: "LA", maine: "ME", maryland: "MD",
  massachusetts: "MA", michigan: "MI", minnesota: "MN", mississippi: "MS", missouri: "MO", montana: "MT", nebraska: "NE",
  nevada: "NV", "new hampshire": "NH", "new jersey": "NJ", "new mexico": "NM", "new york": "NY", "north carolina": "NC",
  "north dakota": "ND", ohio: "OH", oklahoma: "OK", oregon: "OR", pennsylvania: "PA", "rhode island": "RI",
  "south carolina": "SC", "south dakota": "SD", tennessee: "TN", texas: "TX", utah: "UT", vermont: "VT", virginia: "VA",
  washington: "WA", "west virginia": "WV", wisconsin: "WI", wyoming: "WY",
};

/** "Texas" -> "TX" for US states named by the phone; null elsewhere. */
export function usStateCode(name: string | null): string | null {
  return name ? US_STATE_CODES[name.toLowerCase()] ?? null : null;
}
