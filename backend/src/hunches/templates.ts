// The hunch library: everyday questions with slots that the engine fills from the place, the calendar, the weather
// and the town's fact sheet. A template whose slots can't all be filled is skipped, so a hunch never reads generic
// where it was meant to be local. Tone rules: playful, never mean; businesses only in neutral or happy contexts; no
// people, politics, tragedies or health scares; never jokes about dangerous weather (see "serious" in weather.ts).
//
// Slots: {city} {place} (neighborhood or city) {state} {bigbox} {grocery} {coffee} {burger} {pharmacy} {hardware}
// {stop} {storm} {airport} {nfl} {nba} {mlb} {nhl} {mls} {college} {landmark} {park} {mall} {food} {road} {event}
// {newplace} {newplace_kind} {newplace_town} {attraction} {attraction_town} (a new place you'd buy tickets for) {bigcity} {temp} {high} {low} {alert} {days} {weekday}

import type { Moment } from "./calendar";
import type { Climate } from "./places";
import type { AlertKind, Condition } from "./weather";

export type Kind = "weather" | "moment" | "season" | "local" | "sports" | "time" | "everyday";

export type Ctx = {
  country: string;
  us: boolean;
  month: number;
  day: number;
  /** 0 = Sunday */
  weekday: number;
  hour: number;
  season: "winter" | "spring" | "summer" | "fall";
  climate: Set<Climate>;
  alert: AlertKind | null;
  /** Today's (or the rest of today's) weather; temperatures in °C. */
  today: { high: number; low: number; condition: Condition } | null;
  tomorrow: { high: number; low: number; condition: Condition } | null;
  moments: Map<string, Moment>;
  inSeason: Set<string>;
};

export type Template = {
  kind: Kind;
  emoji: string;
  text: string;
  when?: (c: Ctx, m: Moment | undefined) => boolean;
  /** Needs this calendar moment to be in its window; {days} is how far away it is. */
  moment?: string;
  weight?: number;
};

const t = (kind: Kind, emoji: string, text: string, when?: Template["when"], weight?: number): Template =>
  ({ kind, emoji, text, when, weight });
const m = (moment: string, emoji: string, text: string, when?: Template["when"], weight?: number): Template =>
  ({ kind: "moment", emoji, text, moment, when, weight });

// Conditions
const alert = (...kinds: AlertKind[]) => (c: Ctx) => c.alert != null && kinds.includes(c.alert);
const hours = (from: number, to: number) => (c: Ctx) => (from <= to ? c.hour >= from && c.hour < to : c.hour >= from || c.hour < to);
const days = (...weekdays: number[]) => (c: Ctx) => weekdays.includes(c.weekday);
const months = (...list: number[]) => (c: Ctx) => list.includes(c.month);
const season = (...list: Ctx["season"][]) => (c: Ctx) => list.includes(c.season);
const climate = (k: Climate) => (c: Ctx) => c.climate.has(k);
const today = (...conditions: Condition[]) => (c: Ctx) => c.today != null && conditions.includes(c.today.condition);
const all = (...checks: ((c: Ctx) => boolean)[]) => (c: Ctx) => checks.every((check) => check(c));
const us = (c: Ctx) => c.us;
const weekend = days(0, 6);
const weekdayOnly = days(1, 2, 3, 4, 5);
const noAlert = (c: Ctx) => c.alert == null;
const hot = (c: Ctx) => c.today != null && c.today.high >= 33;
const freezing = (c: Ctx) => c.today != null && c.today.low <= -4;
const chilly = (c: Ctx) => c.today != null && c.today.high <= 12;
const nice = (c: Ctx) => c.today != null && c.today.high >= 18 && c.today.high <= 27 && c.today.condition === "clear";
const playing = (league: string) => (c: Ctx) => c.inSeason.has(league);
const before = (_: Ctx, m: Moment | undefined) => m != null && m.daysAway > 0;
const after = (_: Ctx, m: Moment | undefined) => m != null && m.daysAway < 0;

export const TEMPLATES: Template[] = [
  // Weather alerts -----------------------------------------------------------------------------------------------------
  t("weather", "⛈️", "People are racing to the {city} {bigbox} for batteries and bottled water before the {alert} hits", alert("storm"), 3),
  t("weather", "🚗", "{city} is asking AI where to find covered parking before the hail shows up", all(alert("storm"), climate("tornado")), 3),
  t("weather", "🧇", "{city} is checking whether {storm} is still open. If it is, everyone's fine", alert("storm", "cold"), 3),
  t("weather", "🔦", "Half of {place} is asking where the flashlight is. It's in the junk drawer", alert("storm", "wind")),
  t("weather", "🐕", "People in {place} are asking how to calm a dog that hates thunder", alert("storm")),
  t("weather", "🔋", "{city} is asking how to keep phones charged if the power blinks out tonight", alert("storm", "wind", "cold")),
  t("weather", "🥶", "Everyone in {city} is asking how to wrap outdoor pipes before the {alert}", alert("cold"), 3),
  t("weather", "🍞", "{grocery} in {city} is full of people buying bread and milk for the {alert}", alert("cold"), 3),
  t("weather", "🚰", "People in {place} are asking whether to drip the faucets tonight", alert("cold")),
  t("weather", "🪴", "{place} is asking AI how to cover the plants before the freeze", alert("cold")),
  t("weather", "🥵", "{city} is asking AI if it's too hot to walk the dog after lunch", alert("heat"), 3),
  t("weather", "🧊", "People are asking how to stop the car from turning into an oven during the {alert}", alert("heat")),
  t("weather", "💧", "{place} is asking how much water is enough on a day like today", alert("heat")),
  t("weather", "🌊", "{city} is asking which roads flood first when it really rains", alert("flood"), 2),
  t("weather", "🥾", "People in {place} are asking if their sneakers will survive today's commute", alert("flood")),
  t("weather", "💨", "Someone in {place} is chasing a patio umbrella down the street right now, probably", alert("wind"), 2),
  t("weather", "🗑️", "{city} is asking how to stop the trash cans from blowing away", alert("wind")),
  t("weather", "🌫️", "{city} commuters are asking why everything looks like a horror movie this morning", all(alert("fog"), hours(5, 11)), 2),
  t("weather", "😷", "People in {city} are asking if the air is OK for a run today", alert("air"), 2),
  t("weather", "🌬️", "{city} is asking AI which air purifier is actually worth it", alert("air")),

  // Forecast
  t("weather", "☔", "{city} is asking AI whether to carry an umbrella or just run for it", all(noAlert, today("rain"))),
  t("weather", "🎬", "People in {place} are asking what to do with kids on a rainy {weekday}", all(noAlert, today("rain", "thunder"), weekend)),
  t("weather", "⛈️", "People in {city} are asking if it's safe to shower during a thunderstorm", all(noAlert, today("thunder"))),
  t("weather", "⛄", "{city} is asking if school will be cancelled tomorrow (every kid in town is asking)", (c) => c.tomorrow?.condition === "snow" && weekdayOnly(c), 3),
  t("weather", "🛷", "People are asking where to go sledding near {place}", today("snow")),
  t("weather", "🌡️", "It's {high} in {city} today, and people are asking AI for the coldest drink at {coffee}", all(noAlert, hot), 2),
  t("weather", "🏊", "{place} is asking which pools are still open on a {high} day", all(noAlert, hot)),
  t("weather", "🧤", "It's going down to {low} tonight. {city} is asking how cold is too cold for the dog", all(noAlert, freezing), 2),
  t("weather", "🌤️", "It's {high} and sunny, so {city} is asking for the best patio for brunch", all(nice, weekend), 2),
  t("weather", "🚲", "People in {place} are asking for easy bike trails on a {high} day like this", nice),
  t("weather", "🌳", "Everyone in {city} is asking if {park} is busy today", nice, 2),
  t("weather", "🧥", "{city} is asking AI where they put the winter coats last spring", all(chilly, season("fall")), 2),
  t("weather", "🔥", "People in {place} are asking if it's too early to turn the heat on", all(chilly, season("fall"))),

  // Calendar moments -------------------------------------------------------------------------------------------------
  m("newyear", "🎆", "{city} is asking where to watch fireworks on New Year's Eve"),
  m("newyear", "🥂", "People are asking AI for a New Year's Eve plan that ends before midnight"),
  m("resolutions", "🚭", "People in {city} are asking how to quit smoking for good this year", undefined, 2),
  m("resolutions", "🏋️", "{city} is asking which gym near {place} has the shortest January rush"),
  m("resolutions", "🍸", "People are asking for Dry January mocktails that don't taste like juice"),
  m("resolutions", "💸", "{city} is asking AI to build a budget that survives February"),
  m("resolutions", "📚", "People are asking how to actually read 20 books this year"),
  m("mlk", "🕊️", "People are asking about volunteer events in {city} for MLK Day"),
  m("superbowl", "🏈", "{city} is asking how many wings to order for a Super Bowl party of 12", undefined, 2),
  m("superbowl", "📺", "People are asking AI to explain football to a friend before Sunday"),
  m("superbowl", "🥑", "Every cart at {grocery} in {city} has avocados in it this week, probably"),
  m("valentines", "🌹", "{city} is asking for a dinner reservation that isn't booked out. {days} days to go", before, 2),
  m("valentines", "💝", "People are asking for Valentine's gift ideas that aren't flowers"),
  m("valentines", "🍫", "People are asking AI to write a card that sounds less like a greeting card"),
  m("presidents", "🛋️", "People are asking if the Presidents' Day mattress sales are real"),
  m("dst-start", "⏰", "{city} is asking AI how to survive losing an hour of sleep"),
  m("dst-start-eu", "⏰", "{city} is asking AI how to survive losing an hour of sleep"),
  m("dst-end", "🌚", "{city} is asking why it's dark at 5 pm already", after, 3),
  m("dst-end", "😴", "People are asking how to get the kids to sleep now the clocks have changed", after),
  m("dst-end-eu", "🌚", "{city} is asking why it's dark at 5 pm already", after, 3),
  m("stpatricks", "☘️", "{city} is asking where the St. Patrick's Day parade is this year"),
  m("stpatricks", "🍀", "People are asking what to wear so they don't get pinched"),
  m("march-madness", "🏀", "{city} is asking AI to fill out a bracket. It won't help"),
  m("march-madness", "📊", "People are asking how to win the office bracket without watching basketball"),
  m("easter", "🐣", "{city} is asking where the Easter egg hunts are this year"),
  m("easter", "🥚", "People are asking how long hard-boiled Easter eggs can sit out"),
  m("taxday", "🧾", "{city} is asking AI how to file a tax extension. {days} days left", before, 2),
  m("taxday", "💵", "People are asking where their refund is"),
  m("mothers", "💐", "{city} is asking for a Mother's Day brunch spot with a table left", undefined, 2),
  m("mothers", "🎁", "People are asking for a Mother's Day gift that isn't a candle"),
  m("mothers-uk", "💐", "{city} is asking for a Mother's Day lunch spot with a table left"),
  m("graduation", "🎓", "{city} is asking how much money to put in a graduation card"),
  m("graduation", "🎉", "People are asking AI to write a grad party toast"),
  m("memorial", "🍔", "{city} is asking how long to grill burgers for a Memorial Day cookout"),
  m("memorial", "🏖️", "People are asking how bad traffic will be leaving {city} on Friday"),
  m("fathers", "👔", "{city} is asking for Father's Day gifts that aren't another tie", undefined, 2),
  m("fathers", "🥩", "People are asking for the best steak to grill for Dad"),
  m("juneteenth", "🎉", "People are asking about Juneteenth events in {city}"),
  m("canada-day", "🇨🇦", "{city} is asking where to watch the Canada Day fireworks"),
  m("july4", "🎆", "{city} is asking where to watch the Fourth of July fireworks", undefined, 2),
  m("july4", "🌭", "People are asking how many hot dogs to buy for 15 people"),
  m("july4", "🐶", "People in {place} are asking how to keep the dog calm during fireworks"),
  m("back-to-school", "✏️", "{city} is at {bigbox} with a school supply list and questions", undefined, 2),
  m("back-to-school", "🎒", "People are asking AI for lunchbox ideas kids will actually eat"),
  m("back-to-school", "⏰", "{place} parents are asking how to fix bedtime before school starts", before),
  m("labor", "🏖️", "{city} is asking for Labor Day weekend plans that aren't a five-hour drive"),
  m("labor", "👗", "People are asking if you can still wear white after Labor Day (you can)"),
  m("thanksgiving-ca", "🦃", "{city} is asking how big a turkey feeds 10 people"),
  m("halloween", "🎃", "{city} is asking for last-minute costume ideas using stuff from {bigbox}", before, 2),
  m("halloween", "👻", "People are asking which streets in {place} give out full-size candy bars", before, 2),
  m("halloween", "🎃", "People are asking where the pumpkin patches are near {city}", (c, mo) => mo != null && mo.daysAway > 7),
  m("halloween", "🍬", "People in {city} are asking if leftover Halloween candy can be frozen (asking for a friend)", after, 2),
  m("election", "🗳️", "People in {city} are asking what time the polls close", (c, mo) => mo != null && mo.daysAway <= 1),
  m("election", "📍", "People are asking where their polling place is", (c, mo) => mo != null && mo.daysAway <= 3),
  m("enrollment", "🩺", "People are asking AI to explain deductibles before open enrollment ends"),
  m("bonfire", "🎆", "{city} is asking where the best Bonfire Night fireworks are"),
  m("veterans", "🎖️", "People are asking about Veterans Day events in {city}"),
  m("thanksgiving", "🦃", "{city} is asking how big a turkey feeds 10 people", undefined, 3),
  m("thanksgiving", "🧊", "People are asking if you can thaw a turkey in one day (you can't, but they're asking)", (c, mo) => mo != null && mo.daysAway <= 6, 2),
  m("thanksgiving", "🥧", "{city} is asking AI for a Friendsgiving dish that travels well", undefined, 2),
  m("thanksgiving", "🛒", "{city} is planning a {grocery} run that avoids the night before Thanksgiving", before),
  m("thanksgiving", "✈️", "People are asking when to leave for {airport} the Wednesday before Thanksgiving", (c, mo) => mo != null && mo.daysAway > 0 && mo.daysAway <= 12, 2),
  m("thanksgiving", "🍂", "People are asking AI for ways to change the subject at Thanksgiving dinner"),
  m("thanksgiving", "🥡", "People are asking how long Thanksgiving leftovers are good for", after, 3),
  m("blackfriday", "🛍️", "{city} is asking if the Black Friday deals at {bigbox} are actually deals", undefined, 2),
  m("blackfriday", "🏬", "People are asking what time {mall} opens on Black Friday"),
  m("christmas", "🎄", "{city} is asking where to see the best Christmas lights near {place}", (c, mo) => mo != null && mo.daysAway <= 24, 3),
  m("christmas", "🎁", "People are asking for white elephant gifts under $25", (c, mo) => c.us && mo != null && mo.daysAway <= 24, 2),
  m("christmas", "🎁", "People are asking for Secret Santa gifts that don't end up in a drawer", (c, mo) => !c.us && mo != null && mo.daysAway <= 24, 2),
  m("christmas", "📦", "People are asking for the last day to ship gifts in time", (c, mo) => mo != null && mo.daysAway > 0 && mo.daysAway <= 14, 2),
  m("christmas", "🍪", "{city} is asking for a cookie recipe that impresses grandma", before),
  m("christmas", "💡", "People in {place} are asking how early is too early to put up Christmas lights", (c, mo) => mo != null && mo.daysAway >= 25, 2),
  m("christmas", "🔁", "People are asking how to return a gift without a receipt", after),
  // Big in India and among Indian communities abroad: shown every day in India, every other day elsewhere
  m("diwali", "🪔", "People in {city} are asking where to buy diyas and sweets for Diwali", (c) => c.country === "IN" || c.day % 2 === 0),

  // Seasons ------------------------------------------------------------------------------------------------------------
  t("season", "🤧", "{city} is asking if it's allergies or a cold. It's allergies", season("spring")),
  t("season", "🌸", "People are asking what to plant this spring in {state}", season("spring")),
  t("season", "🧹", "People are asking for a spring cleaning checklist that fits in one Saturday", season("spring")),
  t("season", "🏊", "{city} is asking when the public pools open", (c) => c.season === "summer" || (c.season === "spring" && c.month % 3 === 2)),
  t("season", "🦟", "People in {place} are asking how to keep mosquitoes off the patio", season("summer")),
  t("season", "🍦", "People are asking for the best ice cream near {place}", season("summer")),
  t("season", "🧴", "People are asking how much sunscreen is actually enough", season("summer")),
  t("season", "🍂", "{city} is asking when the leaves will peak", all(season("fall"), (c) => !c.climate.has("desert") && !c.climate.has("humid"))),
  t("season", "🍩", "People are asking where to find the best apple cider donuts near {city}", season("fall")),
  t("season", "🍁", "People in {place} are asking if mulching the leaves counts as raking", season("fall")),
  t("season", "☕", "{city} is asking AI if it's too early for a pumpkin spice latte at {coffee}. It isn't", all(us, months(9, 10, 11))),
  t("season", "❄️", "{city} is asking how to stop getting shocked by every doorknob", season("winter")),
  t("season", "🤒", "People are asking if it's the flu or just a cold", season("winter", "fall")),
  t("season", "🧣", "People are asking for a warm winter coat that doesn't look like a sleeping bag", season("winter", "fall")),
  t("season", "💉", "People are asking if {pharmacy} in {city} does walk-in flu shots", all(us, months(9, 10, 11, 12, 1))),
  t("season", "🌀", "People in {city} are asking what goes in a hurricane kit", all(climate("hurricane"), months(6, 7, 8, 9, 10))),
  t("season", "🌪️", "{city} is asking which room is safest in a tornado warning", all(climate("tornado"), months(3, 4, 5, 6))),
  t("season", "🎒", "{city} is asking AI what goes in a go-bag, just in case", all(climate("wildfire"), months(7, 8, 9, 10))),
  t("season", "🌩️", "{city} is asking when monsoon season actually starts", all(climate("monsoon"), months(6, 7))),
  t("season", "🧂", "People in {place} are asking if it's time for snow tires", all(climate("snow"), months(10, 11))),
  t("season", "🏂", "{city} is asking for the closest ski slopes without a lift line", all(climate("snow"), months(12, 1, 2, 3))),
  t("season", "☔", "People are asking how to keep clothes dry when it rains every day", all(climate("monsoon"), months(6, 7, 8, 9), (c) => c.country === "IN")),
  t("season", "💦", "{city} is asking how to stop frizzy hair in this humidity", all(climate("humid"), season("summer"))),
  t("season", "🌧️", "{city} is asking for something to do that isn't outside, again", all(climate("rain"), season("fall", "winter"))),

  // The town -------------------------------------------------------------------------------------------------------------
  t("local", "🎢", "People are asking about tickets for the new {attraction} in {attraction_town}", undefined, 4),
  t("local", "🆕", "{city} is asking AI how early to get to {attraction} to beat the crowds", undefined, 3),
  t("local", "📸", "People in {place} are asking if the new {newplace_kind} everyone mentions is worth the hype", undefined, 2),
  t("local", "🛍️", "{city} is asking AI what's open yet at {newplace}", undefined, 3),
  t("local", "📍", "{city} is asking for the best time to visit {landmark} without the crowds", undefined, 2),
  t("local", "🧭", "People are asking AI to plan a visitor's day around {landmark}", undefined, 2),
  t("local", "🌳", "People in {place} are asking if {park} is dog-friendly", undefined, 2),
  t("local", "🧺", "{city} is asking for the best picnic spot at {park}", (c) => c.season === "spring" || c.season === "summer" || nice(c), 2),
  t("local", "🛍️", "{city} is asking AI what time {mall} closes tonight", hours(12, 19), 2),
  t("local", "🅿️", "People are asking where to park at {mall} on a weekend", weekend, 2),
  t("local", "🍽️", "{city} is arguing about where to get the best {food}. Again", undefined, 3),
  t("local", "🤤", "People are asking AI for a {food} spot that isn't a tourist trap", undefined, 2),
  t("local", "🚗", "{city} is asking AI if {road} traffic will ever get better", weekdayOnly, 3),
  t("local", "🛣️", "People are asking for a shortcut around {road} this morning", all(weekdayOnly, hours(6, 10)), 3),
  t("local", "🎟️", "{event} is coming up, and {city} is already asking what time to get there", undefined, 3),
  t("local", "🎪", "{event} is coming up. People in {place} are asking where to park", undefined, 2),
  t("local", "🚙", "People in {city} are asking if it's worth driving into {bigcity} this weekend", days(4, 5, 6), 2),
  t("local", "⛽", "{city} is debating whether a road trip counts if you don't stop at {stop}", undefined, 2),
  t("local", "🥪", "People are asking what to grab at {stop} on the way out of town", days(5, 6)),
  t("local", "🍔", "{city} is asking AI if {burger} is open late tonight", hours(20, 1), 2),
  t("local", "🍟", "People are asking for {burger} menu hacks nobody told them about"),
  t("local", "☕", "{place} is asking which {coffee} has the shortest line this morning", hours(6, 10), 2),
  t("local", "🐔", "It's Sunday, Chick-fil-A is closed, and {city} is asking AI for a backup plan", all(us, days(0), hours(10, 21)), 3),
  t("local", "🛒", "People are asking for the best time to shop at {grocery} without the crowds"),
  t("local", "✈️", "{city} is asking how early to get to {airport} for a morning flight", undefined, 2),
  t("local", "🧳", "People are asking AI how to survive a delay at {airport}"),
  t("local", "🔨", "{city} is at {hardware} asking where the thing for the other thing is", all(weekend, hours(8, 14))),
  t("local", "🤝", "People are asking how to make friends after moving to {city}"),
  t("local", "🏖️", "People are asking for a weekend trip that isn't too far from {city}", days(3, 4, 5)),

  // Sports -------------------------------------------------------------------------------------------------------------
  t("sports", "🏈", "{nfl} game day: {city} is asking for the best wings for delivery", all(days(0), hours(9, 20)), 4),
  t("sports", "🏈", "{city} is asking AI to explain one call from yesterday's {nfl} game", all(days(1), hours(6, 14)), 3),
  t("sports", "📺", "People are asking where to watch the {nfl} game near {place}", days(0, 1, 4), 2),
  t("sports", "📋", "{city} is asking AI who to start in fantasy football this week", all(us, playing("nfl"), days(0, 4, 5, 6))),
  t("sports", "🏟️", "{city} is asking for a tailgate menu for the {college} game", days(5, 6), 3),
  t("sports", "🏀", "People are asking how to get cheap {nba} tickets"),
  t("sports", "⚾", "{city} is asking AI how to get to the {mlb} game without paying for parking"),
  t("sports", "🏒", "People are asking if a {nhl} game makes a good first date (it does)"),
  t("sports", "⚽", "{city} is asking where to watch {mls} with other fans"),

  // Time of day and week -------------------------------------------------------------------------------------------------
  t("time", "😮‍💨", "{city} is asking AI how to survive a Monday", all(days(1), hours(5, 12)), 3),
  t("time", "📅", "People are asking how to plan a week they already feel behind on", all(days(0, 1), hours(17, 12))),
  t("time", "🚗", "{place} is asking if leaving ten minutes earlier actually helps", all(weekdayOnly, hours(6, 9))),
  t("time", "🥗", "People near {place} are asking for lunch that isn't {burger} again", all(weekdayOnly, hours(11, 14)), 2),
  t("time", "😴", "{city} is asking AI why 3 pm feels like this", all(weekdayOnly, hours(14, 17)), 2),
  t("time", "🍷", "{city} is asking for date night ideas that aren't dinner and a movie", all(days(5, 6), hours(15, 22)), 3),
  t("time", "🍕", "People are asking AI to pick a pizza place so nobody has to decide", all(days(5), hours(16, 22)), 2),
  t("time", "🥞", "{city} is asking for brunch without a two-hour wait", all(weekend, hours(7, 13)), 3),
  t("time", "🌽", "People are asking if there's a farmers market in {place} today", all(days(6), hours(6, 12), season("spring", "summer", "fall")), 2),
  t("time", "📦", "People are asking for meal prep ideas that last till Thursday", all(days(0), hours(12, 21)), 2),
  t("time", "😬", "{city} has the Sunday scaries and is asking AI how to shake them", all(days(0), hours(16, 24)), 3),
  t("time", "🌙", "{city} is awake and asking AI why it can't sleep", hours(23, 3), 3),
  t("time", "🍜", "People are asking for the best late-night food near {place}", hours(21, 2), 2),
  t("time", "🌅", "{city} early birds are asking AI for a ten-minute workout", hours(5, 7), 2),
  t("time", "🧒", "{city} is asking for weekend plans with kids that don't involve a screen", days(5, 6), 2),
  t("time", "🛋️", "People in {place} are asking for a show to binge tonight", hours(19, 23)),

  // Everyday life ------------------------------------------------------------------------------------------------------
  t("everyday", "🥚", "People are asking how long eggs really last in the fridge"),
  t("everyday", "🛏️", "People are asking how to fold a fitted sheet (still)"),
  t("everyday", "💬", "People are asking AI what to text after a first date"),
  t("everyday", "💼", "People are asking how to ask for a raise without sounding pushy"),
  t("everyday", "🌿", "People are asking whether their plant is overwatered or just dramatic"),
  t("everyday", "🍚", "People are asking how to cook rice without a rice cooker"),
  t("everyday", "🍝", "People are asking if you can freeze cooked pasta"),
  t("everyday", "📧", "People are asking AI to make \"per my last email\" sound nicer"),
  t("everyday", "🥂", "People are asking for help writing a wedding toast for their sister"),
  t("everyday", "🎁", "People are asking for a coworker gift under $20", us),
  t("everyday", "🏃", "People are asking how to start running when they hate running"),
  t("everyday", "🪰", "People are asking how to get rid of fruit flies for good"),
  t("everyday", "🙅", "People are asking how to say no to a coworker politely"),
  t("everyday", "👵", "People are asking AI to explain their job to their grandma"),
  t("everyday", "🎧", "People are asking for the best way to clean earbuds"),
  t("everyday", "🏠", "People are asking how to tell the landlord the AC broke, nicely"),
  t("everyday", "💌", "People are asking how to politely decline a wedding invite"),
  t("everyday", "🧒", "People are asking how to get a toddler to sleep past 6 am"),
  t("everyday", "🐕", "People are asking which human foods are safe for dogs"),
  t("everyday", "📦", "People are asking how much to tip movers", us),
  t("everyday", "🍕", "People are asking AI to settle whether pineapple belongs on pizza"),
  t("everyday", "🧳", "People are asking for a carry-on packing list for a three-day trip"),
  t("everyday", "💤", "People are asking how to fix their sleep schedule in a week"),
  t("everyday", "📱", "People are asking how to free up phone storage without deleting photos"),
  t("everyday", "🧾", "People are asking how to split a dinner bill without it getting weird"),
  t("everyday", "🥑", "People are asking how to tell if an avocado is ripe"),
  t("everyday", "🧠", "People are asking how to stop overthinking a text they already sent"),
  t("everyday", "🎂", "People are asking for a birthday message that isn't just \"HBD\""),
  t("everyday", "💪", "People are asking for a 20-minute workout with no equipment"),
  t("everyday", "🍳", "People are asking what to cook with three eggs, a tomato and half an onion"),
  t("everyday", "📚", "People are asking for a book to get back into reading"),
  t("everyday", "🪴", "People are asking for houseplants that are impossible to kill"),
  t("everyday", "🚘", "People are asking what the orange light on the dashboard means"),
  t("everyday", "🥡", "People are asking AI to turn a fridge full of leftovers into dinner"),
  t("everyday", "🐈", "People are asking why their cat knocks things off the table"),
  t("everyday", "👔", "People are asking what \"business casual\" means these days"),
  t("everyday", "🎤", "People are asking how to calm their nerves before a presentation"),
  t("everyday", "📝", "People are asking AI to write a cover letter that sounds human"),
  t("everyday", "🧹", "People are asking for a 15-minute cleanup before guests arrive"),
  t("everyday", "🎮", "People are asking for games to play with friends online tonight", hours(17, 24)),
  t("everyday", "🎬", "People are asking for a movie the whole family will agree on", weekend),
  t("everyday", "🧘", "People are asking how to start meditating five minutes a day"),
  t("everyday", "💡", "People are asking how to lower the electric bill"),
  t("everyday", "👕", "People are asking how to get a stain out of a white shirt"),
  t("everyday", "🗣️", "People are asking how to be less awkward at networking events"),
  t("everyday", "🥪", "People are asking for a lunch they can make in five minutes", hours(6, 13)),
  t("everyday", "✈️", "People are asking for the cheapest day of the week to book flights"),
  t("everyday", "🌮", "People are asking which wine goes with tacos"),
  t("everyday", "🐶", "People are asking how to stop a puppy from chewing everything"),
  t("everyday", "📸", "People are asking how to take better photos with their phone"),
  t("everyday", "💍", "People are asking for proposal ideas that aren't cheesy"),
  t("everyday", "👩‍💻", "People are asking if it's too late to learn to code"),
  t("everyday", "👶", "People are asking for baby names that aren't in the top 100"),
  t("everyday", "🥘", "People are asking for a slow cooker recipe that tastes like they tried"),
  t("everyday", "🔑", "People are asking how to stop losing their keys"),
  t("everyday", "📨", "People are asking how to write a polite follow-up email", weekdayOnly),
  t("everyday", "🍌", "People are asking what to do with brown bananas besides banana bread"),
  t("everyday", "🫖", "People are asking how to cut back on coffee without the headache"),
  t("everyday", "🛋️", "People are asking how to make a small apartment feel bigger"),
  t("everyday", "🗓️", "People are asking how to stop saying yes to everything"),
  t("everyday", "🚲", "People are asking if an e-bike is worth it for commuting"),
  t("everyday", "🍼", "People are asking how to get a baby to take a bottle"),
  t("everyday", "👴", "People are asking how to set up a new phone for their parents"),
  t("everyday", "🎙️", "People are asking for a podcast for a long drive"),
  t("everyday", "😅", "People are asking how to apologize to a friend without making it weird"),
  t("everyday", "🧊", "People are asking how long leftovers are good for"),
  t("everyday", "🪥", "People are asking if electric toothbrushes are really better"),
  t("everyday", "🧑‍🍳", "People are asking AI to turn a recipe into a grocery list"),
  t("everyday", "🏋️", "People are asking how to get back into the gym after a break"),
  t("everyday", "🎨", "People are asking for a hobby that isn't another screen"),
  t("everyday", "🐾", "People are asking how to introduce a new cat to the old cat"),
  t("everyday", "🛁", "People are asking how to unclog a drain without calling anyone"),
  t("everyday", "📮", "People are asking how to write a thank-you note that doesn't sound copied"),
  t("everyday", "🧾", "People are asking AI for a monthly budget that's actually realistic"),
  t("everyday", "🎲", "People are asking for board games that don't end friendships"),
  t("everyday", "🌶️", "People are asking how to fix a dish that's way too spicy"),
  t("everyday", "🧦", "People are asking where all the missing socks go"),
  t("everyday", "🎉", "People are asking for a surprise party plan that stays a surprise"),
  t("everyday", "🙂", "People are asking how to remember names after one introduction"),
  t("everyday", "🫶", "People are asking how to support a friend who's going through it"),
  t("everyday", "🧑‍🏫", "People are asking AI to explain their kid's math homework"),
  t("everyday", "🍿", "People are asking for a scary movie that isn't too scary", (c) => c.month === 10 || hours(20, 24)(c)),
];
