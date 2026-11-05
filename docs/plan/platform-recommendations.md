# Platform recommendations for a US launch

Written 2026-11-03, alongside release 0.4.0. These cover what to add to the stack as Unicorn grows, the Chrome
extension and cloud sync, and accounts. Nothing here is built yet except where it says "done".

## 1. Technology upgrades

**Short answer: stay on Cloudflare and add its pieces as needed. You don't need Redis or Upstash yet.** The Worker,
D1 and the new KV cache already run next to each other at the edge. Upstash Redis would work (its HTTP client and
`@upstash/ratelimit` run on Workers), but it adds a vendor, a bill and a network hop for things Cloudflare already has.

| Need | Use | When | Why |
| --- | --- | --- | --- |
| Faster AI answers | Stream responses (server-sent events) from `/v1/ai` to the bar | Next release | The first words appear in under a second instead of after the whole answer. The single biggest perceived-speed win. |
| AI cost and reliability | Cloudflare AI Gateway in front of Anthropic, plus Anthropic prompt caching for the long system prompts | Next release | Retries, fallbacks, cost dashboards and cheaper repeated prompts. Turn off request logging so text is never stored. |
| Caching | Workers KV | Done in 0.4.0 | Weather (15–30 min) and town fact sheets (14 days) for hunches. |
| Rate limits and the AI budget | Durable Objects (one per day for the global budget, sharded per install) | Before about 50,000 AI requests a day | Today every request writes two D1 rows. Durable Objects count atomically in memory, with no write contention. |
| Abuse of the free AI | Play Integrity API on `/v1/ai`; Cloudflare Turnstile on website forms | Before public launch | Install IDs are made on the phone, so a script can rotate them. Integrity tokens prove requests come from the real app. |
| Trend aggregation | Cron Triggers to precompute trend tables nightly; Queues for incoming signals | When signals pass about 1M rows | Reading trends then becomes a key lookup instead of a `GROUP BY` over 30 days. |
| Usage metrics | Workers Analytics Engine | Now | Who uses what and how much, counted on our side, with no third-party SDK inside an app that sees typing. |
| The insights product | R2 with Iceberg tables (R2 Data Catalog), or ClickHouse Cloud | When selling insights | D1 caps at 10 GB per database and isn't built for analytics queries. |
| Crashes | Sentry or Firebase Crashlytics, configured to never attach text | Before public launch | Today crashes are only in the on-phone activity log. |
| Product analytics | PostHog (self-hostable) or Analytics Engine events | With accounts | Funnels and retention; avoid ad-tech SDKs entirely. |
| Push | Firebase Cloud Messaging | With weekly recaps | It's how Android delivers notifications. It's a delivery channel, not a data source. |
| Email | Resend or Postmark | With the waitlist invites | Invites and sign-in codes. |

**The biggest gap for the US isn't infrastructure, it's iPhone.** More than half of US smartphones are iPhones. iOS
allows custom keyboards with network access ("Allow Full Access") and an app alongside, so most of Unicorn carries
over: writing tools, Ask Unicorn, saving prompts typed in AI apps, hunches and the Unicorn card. Reading
notifications for `/reply` does not carry over. I'd make an iOS version the next large project after the Android
public launch.

## 2. Chrome extension and cloud sync

### The extension

A Manifest V3 extension can save what people send in ChatGPT, Claude, Perplexity, Gemini, Copilot and Google search
on desktop, which is where a lot of AI use happens.

- One small adapter per site finds the message box and the send action. It saves on send, never on keystrokes, and
  skips password and payment fields, like the phone does.
- Local-first: prompts are kept in the extension's storage until the user signs in and turns on sync.
- Same Near you sharing: the same anonymous daily summaries, the same "see exactly what's shared" preview, the same
  25-person minimum.

**Expect extra scrutiny.** In December 2025, researchers found Urban VPN Proxy and related extensions (over 8 million
users) quietly capturing AI chats and selling them through a data broker. Chrome Web Store reviewers, security
researchers and the press now watch exactly this kind of extension. To pass review and keep trust:

- Make it the extension's single, stated purpose. Show a full-screen disclosure and get an opt-in before saving
  anything.
- Request host access only for the listed AI sites, never "all sites".
- Publish exactly what is stored and shared, and never sell personal data (aggregated, anonymous trends only).

### Cloud sync

**Recommendation: end-to-end encrypted sync.** Prompts are encrypted on the device with a key the server never sees.
The key is derived from a passkey (the WebAuthn PRF extension) or a recovery code shown once. The server stores only
encrypted blobs, in R2 or D1.

- It keeps the promise that what you ask AI is yours, and a breach of Unicorn's servers exposes nothing readable.
- It keeps raw prompts out of scope for most data requests and state privacy laws, because Unicorn can't read them.
- Trends keep working as now, because they are computed on each device before anything is shared.
- The trade-offs: no server-side search, and losing every device and the recovery code means losing the history.
  Most people accept that for private data. It's the same model as password managers.

**Phasing:** extension v1 is local-only with sharing; sync arrives with accounts.

## 3. Accounts

**Yes to accounts, no to a sign-up wall.**

1. **Anonymous account at first launch, automatically.** The app already makes an install ID; the server turns it
   into an anonymous account. That alone tells you how many people use Unicorn and how much (daily and monthly users,
   retention, AI requests and features per user) without asking anyone for anything. An email doesn't add usage data.
   It only adds a way to contact people and to link their devices.
2. **Ask to sign in at moments when it helps the user.** Good moments are turning on sync, installing the extension,
   opening Unicorn on a second phone, and when their Unicorn card reaches level 3 ("don't lose your Unicorn").
3. **Offer passkeys, Sign in with Google and email codes.** On Android, use Credential Manager for Google sign-in and
   passkeys. Add Sign in with Apple for iOS, which Apple requires when other social sign-ins are offered.

Every extra step before the first wow moment loses people, and an app that sees what people type should earn trust
before asking who they are. That's why there's no wall.

### Which auth

| Option | Fits | Watch out for |
| --- | --- | --- |
| **Better Auth on our Worker + D1** (my pick) | Open source, no per-user fees, accounts live in the same database as quotas and trends. It has anonymous-user and passkey plugins. | Needs a query builder for D1 (Drizzle or Kysely) and more setup than a hosted service. We own security updates. |
| Clerk | Fastest to ship, polished UI, a Chrome extension SDK. The free tier was raised to 50,000 monthly retained users in February 2026. | Per-user pricing past the free tier; user data lives with a vendor. |
| Supabase Auth or Firebase Auth | Mature, generous free tiers. | A second backend beside Cloudflare. |

### What accounts bring with them

- Account deletion inside the app and on the web (a Google Play requirement), plus data export.
- Handling access and deletion requests under CCPA and the other state privacy laws.
- An age statement or gate (13+, for COPPA).

## 4. Before a US public launch

These are things to check with a lawyer; I'm not one.

- **Privacy policy and Play data safety form** matching the app: the draft is at `website/privacy/`.
- **CCPA/CPRA and other state laws.** Selling only de-identified, aggregated insights is the safest model. Honor
  Global Privacy Control on the website if it ever adds tracking (it has none today).
- **FTC.** The 2024 rule on reviews and testimonials bans fake ones. The website has none, and hunches are always
  labelled as guesses with their reason shown.
- **Data sources.** Google Trends and Google News are gone as of 0.4.0. Hunches use MET Norway weather (CC BY 4.0,
  attributed in the app) and US National Weather Service alerts (public domain).
- **iPhone and the extension** follow the same privacy design: prompts stay with the user, and only anonymous
  aggregates are shared.

## Sources

- [Clerk: pricing explained (free tier raised to 50,000 MRU)](https://clerk.com/articles/clerk-pricing-explained)
- [Koi Security: Urban VPN extension capturing AI conversations](https://www.koi.ai/blog/urban-vpn-browser-extension-ai-conversations-data-collection)
- [Dark Reading: browser extension harvests 8M users' AI chatbot data](https://www.darkreading.com/endpoint-security/chrome-extension-harvests-ai-chatbot-data)
- [SecurityWeek: Chrome extensions with 900,000 downloads caught stealing AI chats](https://www.securityweek.com/chrome-extensions-with-900000-downloads-caught-stealing-ai-chats/)
