# What users dislike about CleverType and Grammarly, and what Unicorn should do about it

Research date: 2026-11-02. Scope: the CleverType AI Keyboard and the Grammarly keyboard (including the Android writing assistant that replaced it). This is an internal research note. Public copy still follows the positioning rule: Unicorn is a smart AI app, never "a keyboard".

## Summary

- **CleverType's biggest problem is its paywall, not its AI.** About half of its 1–3★ Play reviews (48%; 55% of 1★) complain about the free AI running out. The free tier is a one-time trial of 2,000 AI words ([FAQ](https://www.clevertype.co/faq)), and users read the "Free AI Keyboard" marketing as bait. Unicorn's free AI is its main advantage, but only if users never hit a surprise wall. Our backend has a 150-request daily cap per install, and one global daily cap of 50,000 requests covers everyone.
- **The second biggest is basic typing quality.** One in five CleverType complaints (20%) is about autocorrect and prediction: apostrophes, contractions, changing correct words, and not learning. People judge an "AI keyboard" on whether `im` becomes `I'm`. Our AOSP LatinIME engine is a strong base, but we need a regression suite to keep it that way.
- **Reliability failures turn into 1★ reviews right away.** These include the keyboard vanishing, blank keys, and "oops, something went wrong" (13% of CleverType; about 40% of Grammarly).
- **Privacy fear is triggered by Android itself.** The system warning that an input method "may collect passwords and credit card numbers" makes users uninstall CleverType (8% overall; 12% of 1★). Unicorn also saves prompts in AI apps, so we carry more of this risk than CleverType, not less.
- **Grammarly shows what happens when you take the keyboard away.** In March 2024 it replaced its Android keyboard with a floating bubble. Nearly half of its 1★ reviews since then are about that change, and the bubble is now its signature complaint. Lessons: don't put overlays over the text, don't nag, and don't remove a feature people rely on.
- **Features we lack that CleverType has:** glide typing, multilingual typing (40+ languages), keyboard resize, clipboard, emoji search, custom tones and assistants, AI voice clean-up, and desktop. CleverType users also ask for things it lacks, which we can win on: GIFs, one-handed mode, a number row, settings backup, and a usable free tier.

## Method and caveats

| Source | What was read | Notes |
|---|---|---|
| Google Play, CleverType ([listing](https://play.google.com/store/apps/details?id=com.clevertype.ai.keyboard&hl=en_US&gl=US)) | 540 reviews filtered by stars (180×1★, 200×2★, 160×3★), sorted "Most relevant", 484 with real text | 4.3★, 43.8K reviews, 1M+ downloads, updated 2026-10-31 |
| Google Play, Grammarly ([listing](https://play.google.com/store/apps/details?id=com.grammarly.android.keyboard&hl=en_US&gl=US)) | 120 reviews (80×1★, 20×2★, 20×3★), "Most relevant"; 79 of them from 2024 or later | 4.3★, 264K reviews, 50M+ downloads. The web dialog stopped loading 2★ and 3★ reviews after 20, so the 2★ and 3★ share is thin |
| App Store, CleverType ([reviews](https://apps.apple.com/us/app/clevertype-ai-keyboard/id6560110828?see-all=reviews&platform=iphone)) | The 10 visible reviews, 5 of them complaints | 4.1★ from 150 ratings |
| App Store, Grammarly ([reviews](https://apps.apple.com/us/app/grammarly-ai-keyboard-notes/id1158877342?see-all=reviews&platform=iphone)) | The 14 visible reviews, 6 of them negative or mixed | 4.7★ from 228K ratings; seller is now Superhuman Platform Inc. |
| Aggregators | [JustUseApp (CleverType)](https://justuseapp.com/en/app/6560110828/clevertype-ai-keyboard/reviews), [AlternativeTo (CleverType)](https://alternativeto.net/software/clevertype/about/), [unitQ (Grammarly)](https://unitq.com/unitq-scorecards/grammarlykeyboard) | JustUseApp rates 43% of its 135 analysed CleverType reviews as negative. unitQ scores Grammarly's iOS app 45%. Both are automated, so treat them as rough |
| Trustpilot | [Grammarly](https://uk.trustpilot.com/review/grammarly.com): about 3.5 from about 10.4K reviews, mostly about billing for web and desktop | CleverType has no Trustpilot page |
| Tech press and forums | [TidBITS: iOS 18 breaks Grammarly Keyboard](https://talk.tidbits.com/t/ios-18-breaks-grammarly-keyboard/28926), [Grammarly iOS 12.2.0 release notes](https://releases.sh/release/rel_M51QgzSLfNjrE-0Q4JLrn-grammarly-ai-keyboard-notes-12-2-0), [Samsung Community on the Grammarly change](https://us.community.samsung.com/t5/Galaxy-S22/How-to-use-Grammarly-on-my-Samsung-S22-Ultra/m-p/3506780), [Fleksy on CleverType](https://fleksy.com/blog/upgrade-your-writing-with-clevertype-android-keyboard) | There are no independent hands-on reviews of CleverType. Most press about it is vendor content |
| Reddit | **Not covered.** reddit.com is blocked for both our search tool and our browser | Worth a manual pass on r/androidapps, r/Android and r/Grammarly before we lock the roadmap |

**How the shares were computed.** Each review was tagged with every issue it mentions, using keyword rules applied to the review text and then spot-checked by reading. One review can carry several tags, so the columns don't add up to 100%. "Most relevant" sorting favours long, helpful-voted reviews, so read the shares as relative weight, not exact frequency. Dates are review dates. Quotes are under 15 words and keep the reviewer's spelling.

## 1. CleverType: issues in 1–3★ reviews

Context: free typing, plus a **one-time** 2,000-word trial of the AI features ([FAQ](https://www.clevertype.co/faq)). After that, Premium costs $5.99/month for mobile and desktop, with 25% off yearly ([pricing](https://www.clevertype.co/pricing)). Store prices are $4.99 to $6.99 a month and $44.99 to $59.99 a year ([App Store](https://apps.apple.com/us/app/clevertype-ai-keyboard/id6560110828)). A sign-in is required. Typing works offline; AI needs the network. A referral programme earns extra free words.

| # | Issue | Share of 1–3★ (n=484) | Share of 1★ (n=177) | What users describe | Representative line |
|---|---|---|---|---|---|
| 1 | **Paywall and free limit** | ~48% | ~55% | The free AI is used up within minutes or days. Users say the "Free" ads are misleading and want a one-time price instead of a subscription. Grammar fix, tone and translation are all behind the wall, and short inputs use up many "words" | "It only gives you 2000 words of AI support for free" (Play 1★, 2026-06-28) |
| 2 | **Autocorrect and prediction** | ~20% | ~18% | Apostrophes and contractions fail (im→I'm, itll→it'll). Correct words get changed (well→we'll, Spotify→sorority). It doesn't learn the user's words. Tapping a suggestion inserts it mid-word. No auto-capitalisation after a full stop, and double-space for a period doesn't work | "It can't really do apostrophes" (Play 1★, 2026-04-02) |
| 3 | **Reliability** | ~13% | ~13% | The keyboard disappears or shows blank keys. The keyboard doesn't open. Repeated "oops, something went wrong" or "error occurred" on AI. Crashes after updates. Typing stops until the user pays | "The suggestion line appears, but no keyboard." (Play 1★, 2025-07-01) |
| 4 | **Layout and customisation** | ~9% | ~6% | Only the height can be resized; users can't move the keyboard up or make it smaller and centred. The keyboard is too big. No number row, no one-handed, split or floating mode. No ₹ symbol. Key borders don't work on image themes. Sound and vibration settings are broken | "…resize my keyboard to be smaller and in the center I cant" (Play 3★, 2024-11-18) |
| 5 | **Privacy fear** | ~8% | ~12% | Android's input-method warning about passwords and cards. Typed text goes to OpenAI with no opt-out. Reading the last message needs accessibility permission. Banking apps (HSBC) refuse to open | "my phone gave me alerts this tool wants to read my password" (Play 1★, 2026-07-04) |
| 6 | **Languages** | ~8% | ~5% | Urdu, Chinese Pinyin, Tagalog, Hindi, Kannada, Sinhala and Bangla are missing or broken. Swipe doesn't work in Polish. The layout is tied to the language (QWERTZ users get QWERTY). Bilingual users can't get suggestions in two languages at once | "Keyboard layout cannot be set independently from keyboard language" (Play 1★, 2026-02-26) |
| 7 | **Support** | ~7% | ~10% | Bot replies, no answer even to paying users, and copy-paste "email us" replies to reviews | "Customer support is responding through bot" (Play 1★, 2025-11-20) |
| 8 | **Billing** | ~6% | ~9% | Users paid but still see "limit exceeded". Failed payments still debited the card. Renewals happened without notice. Refunds are refused. Purchases can't be restored on iOS | "Imagine paying 6 a month to get a "exceeded free word limit"" (Play 1★, 2026-05-18) |
| 9 | **Glide and swipe typing** | ~5% | ~5% | Inaccurate and laggy. The swipe trail disappears. General gestures (swipe left to delete a word) don't work | "It wouldn't even spell "bet" after 15 attempts." (Play 2★, 2025-07-27) |
| 10 | **Voice typing** | ~5% | ~3% | Cloud only (no offline option). It often fails. It looks like a recording UI, not live dictation. Hindi comes out in Urdu script. It doesn't work in Gmail or WhatsApp. It was later put behind the paywall | "constant failures for voice to text, about 70% of my attempts fail" (Play 1★, 2025-11-27) |
| 11 | **Account required** | ~3% | ~6% | Users must sign up to type. Sign-in loops and stuck loading screens. Only one Google account is supported | "A keyboard doesn't need an account" (Play 2★, 2024-05-26) |
| 12 | **Emoji, GIF and clipboard** | ~3% | ~2% | No GIF tab. Emoji search results don't appear in recents. The clipboard doesn't work. All emojis get added at the end | "it doesn't support GIFs and I use a lot of them" (Play 3★, 2026-06-07) |
| 13 | **Lag and battery** | ~3% | ~3% | Slow typing, lag in Telegram, AI taking about 10 seconds, battery drain | "It is always lagging, even though the network is good." (Play 1★, 2026-06-04) |
| 14 | **AI output quality** | ~3% | ~4% | Grammar fix shortens a paragraph and drops content (iOS). Translation skips parts of the text. Rewrites contain misspellings. Wrong answers in the chat | "The app converts the entire paragraph into one or two sentences" (App Store, "Bugs", Apr 11) |
| 15 | **Settings lost** | <1% | <1% | No export or backup of custom prompts and themes. A reinstall to fix crashes wipes all of it. These reviews have many helpful votes | "when I changed my device, all the settings were lost" (Play 1★, 2025-10-16) |
| 16 | **Interference with other apps (iOS)** | rare | rare | Video playback stops when the keyboard opens. The mic stays active in the background | "Still stops video playback when opening the keyboard" (App Store, Apr 9) |

What CleverType users like, and what we should match: the AI features when they work, an AMOLED-black theme, quick support replies in the early days, and a basic keyboard that works offline.

## 2. Grammarly: issues in 1–3★ reviews

Context: on Android, Grammarly **discontinued its keyboard around 6 March 2024**, going by the dates of the first user reviews. It was replaced by "Grammarly for Android", a floating widget that runs on top of any keyboard through accessibility access. The Play listing still links to the shutdown notice, which now returns 404, and the support site points old-keyboard users to switch keyboards ([support](https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now)). On iOS, the keyboard still exists as "Grammarly: AI Keyboard & Voice". The free plan includes 100 AI prompts a month and Pro includes 2,000; Pro is listed at $12 ([plans](https://www.grammarly.com/plans)). iOS in-app prices are $29.99 a month or $139.99 a year, or $17.99 a month for mobile only.

| # | Issue | Share of 1–3★ (n=120) | Share of 1★ (n=80) | What users describe | Representative line |
|---|---|---|---|---|---|
| 1 | **Keyboard removed; intrusive floating widget** | ~43% | ~49% | The bubble covers text and suggestions, and nag banners say "Use Grammarly with your favorite keyboard". Users can't dismiss it. Paying users lost the feature they paid for. Some users with disabilities relied on the keyboard | "Eliminating the grammarly keyboard was a mistake." (Play 1★, 2024-03-30) |
| 2 | **Reliability** | ~40% | ~44% | The assistant turns itself off or disconnects many times a day. It freezes the email app. Users have to reset or reinstall it every few weeks | "App disconnect itself at least 10 times a day." (Play 1★, 2025-01-06) |
| 3 | **Correction quality** | ~40% | ~33% | Wrong fixes, fixes that revert or insert twice, punctuation in the middle of a word, over-correcting slang. Dictionary words stay underlined. It doesn't learn. On the old keyboard, "im" became "in" | "suggests a fix, and either does it and reverts back, inserts it twice" (Play 2★, 2025-11-28) |
| 4 | **Price and paywall** | ~28% | ~30% | Premium is expensive (about $30 a month on mobile). The free version is too limited. AI prompts were cut. iOS shows daily rewrite limits | "5 star performance, 2 star pricing." (Play 2★, 2020-10-29) |
| 5 | **Billing and refunds** | ~8% | ~6% | No renewal reminder and no refunds. Users have to contact support to cancel. This is the most-voted review in the sample (947 helpful votes) | "they do NOT remind you they are about to renew" (Play 1★, 2023-12-09) |
| 6 | **Privacy and permissions** | ~8% | ~10% | The new app needs access to all apps through accessibility. On iOS it needs Full Access, and reviewers call the setup a dark pattern | "requires us to give them permission to access all of our apps" (Play 1★, 2024-03-03) |
| 7 | **Account and login** | ~7% | ~9% | Sign-in errors, even for paid users. A free school account suddenly asked for payment | "Can't even log in on my account atm" (Play 1★, 2024-10-27) |
| 8 | **Long-form text** | ~3% | ~3% | Unusable on long text. It deletes blank lines in email. Users can't scroll the sentence while correcting | "it is atrocious for anything long-form" (Play 2★, 2026-07-23) |
| 9 | **Battery** | ~3% | ~3% | The widget runs in the background, and Grammarly's own release notes ask users to exempt it from battery limits | "It drains the battery of my cellphone so fast" (Play 1★, 2024-04-17) |
| 10 | **Accessibility users hurt** | ~3% | ~4% | Users with dysgraphia or learning disabilities lost the tool they depended on | "some of us have accesibility issues, myself included, and need the keyboard" (Play 1★, 2024-04-02) |
| 11 | **iOS keyboard typing** (App Store, release notes) | small sample | n/a | Lag and missed keystrokes. Taps registered as swipes or as a nearby key, fixed in [12.2.0](https://releases.sh/release/rel_M51QgzSLfNjrE-0Q4JLrn-grammarly-ai-keyboard-notes-12-2-0). Wrong keyboard height on iOS 26.2. The keyboard was dead on iOS 18 ([TidBITS](https://talk.tidbits.com/t/ios-18-breaks-grammarly-keyboard/28926)). Daily rewrite limits | "Your out of rewrites for today" (App Store, "Gotta do better", May 22) |

## 3. CleverType feature inventory and gap against Unicorn 0.3.0

Sources: the [Play listing](https://play.google.com/store/apps/details?id=com.clevertype.ai.keyboard&hl=en_US&gl=US), [website](https://www.clevertype.co/), [FAQ](https://www.clevertype.co/faq), [pricing](https://www.clevertype.co/pricing), the vendor's [comparison post](https://www.clevertype.co/post/clevertype-vs-gboard-vs-samsung-best-ai-keyboard), [AlternativeTo](https://alternativeto.net/software/clevertype/about/), and features confirmed by reviews.

"Paid" means Premium after the one-time 2,000-word trial. A dash in the CleverType column means the feature is absent there too.

### AI features

| Feature | CleverType | Free or paid in CleverType | Unicorn today | Status |
|---|---|---|---|---|
| One-tap grammar fix (40+ languages) | Yes | Paid | Grammar fix, free | **have** |
| Rephrase, paraphrase, shorten, expand | Yes | Paid | `/improve`, `/shorter`, `/longer`, `/formal`, `/casual` | **have** |
| Tone changer | 13 preset tones (Professional to Gen-Z) plus user-defined tones | Paid | Tone, 8 presets, no custom tones | **partial** |
| Tone detection | Yes (iOS listing) | Paid | No | **missing** |
| Translate while typing | 40+ languages | Paid (reviewers: "upgrade for translation") | Translate, 18 languages, free | **partial** |
| AI chat in the keyboard ("ChatGPT access") | Yes | Paid after trial | Ask Unicorn prompt bar, free | **have** |
| Smart reply | "Clever Reply" reads the last message through accessibility; neutral, positive or negative reply | Paid | `/reply` to copied text (by design, no accessibility) | **partial** |
| Email writer | Yes | Paid | `/email` | **have** |
| Summarise | Yes | Paid | `/summarize` | **have** |
| 30+ ready-made assistants (Humanise, Emojify, phrase-to-emoji, captions, pickup lines) | Yes | Paid | 11 slash commands plus refine chips | **partial** |
| Custom assistants (user's own prompts) | Yes | Paid | No | **missing** |
| App-aware tone (for example, professional on LinkedIn) | Yes (added early 2026) | Paid | Email- and chat-aware | **partial** |
| AI "aggressiveness" setting | Yes (vendor blog) | Not stated | No | **missing** |
| AI voice typing (GPT-4o Transcribe; removes fillers, formats lists) | Yes | Paid by 2026 (Play 2★, 2026-04-12) | Android SpeechRecognizer, no AI clean-up | **partial** |
| Bring your own API key | Removed late 2024 (reviews) | n/a | Not needed (AI is free) | n/a |
| Desktop: Mac app, Chrome, Edge and Firefox extensions, one account | Yes | Paid "Mobile & Desktop" plan | No | **missing** |
| iOS app | Yes | Same model | Android only | **missing** |

### Typing features

| Feature | CleverType | Free or paid in CleverType | Unicorn today | Status |
|---|---|---|---|---|
| Autocorrect and suggestions | Yes (much criticised) | Free | AOSP LatinIME | **have** |
| Personal dictionary and learning new words | Yes (reviews say it learns poorly) | Free | Personal words (learns names and new words) | **have** |
| Text shortcuts | Yes | Free | `omw` → "On my way!" | **have** |
| Glide / swipe typing | Yes (reworked in the Oct 2026 update) | Free | Glide typing (0.5.0), Unicorn's own decoder | **have** |
| Gestures (swipe to delete a word, space-bar cursor) | Yes (reported broken) | Free | Not in 0.3.0 | **missing** |
| Cursor and arrow keys | Yes (navigation arrows) | Free | No | **missing** |
| Themes | Preset themes, custom colours, own-theme builder, AMOLED black | Free | 9 themes plus Classic, which follows light and dark mode | **partial** |
| Photo or background themes | Asked for by users; key borders break on image themes | Free | No | **missing** |
| Keyboard height resize | Height only | Free | No | **missing** |
| One-handed, floating or split mode | — (requested) | — | No | **missing (both)** |
| Number row toggle | — (requested) | — | Always on (0.4.2) | **done, no toggle yet** |
| Key sound and vibration settings | Yes (reported buggy) | Free | Vibration, click, both or off, with strength (0.4.2) | **done** |
| Emoji panel | Yes | Free | Emoji key | **have** |
| Emoji search | Yes (recents bug) | Free | No | **missing** |
| GIFs and stickers | — (requested) | — | No | **missing (both)** |
| Clipboard manager | Yes (a reviewer calls it larger than the stock one) | Free | No | **missing** |
| Multilingual typing, 40+ languages (Fleksy SDK) | Yes, with gaps (Urdu, Pinyin, Tagalog) | Free | English only | **missing** |
| Handwriting input | Yes (a reviewer saw it on by default) | Free | No | **missing** (low value) |
| Voice typing | Yes (cloud) | Paid by 2026 | Yes, free | **have** |
| Works offline (basic typing) | Yes | Free | Yes (on-device engine) | **have** |
| No account needed to type | No, sign-in is required | n/a | Install id only, no sign-in | **have (our advantage)** |
| Incognito typing | Not documented | n/a | Respects the app's incognito and password flags; no manual toggle | **partial** |
| Settings and theme backup or sync | — (requested) | — | Not in 0.3.0 | **missing (both)** |
| Select-all, clear and undo key | Not documented | n/a | Yes | **Unicorn only** |
| Recall of earlier AI prompts, Library, Insights, Near you | No | n/a | Yes | **Unicorn only** |

## 4. Prioritised checklist for Unicorn

Priority: **P0** means complaints that drive uninstalls or 1★ reviews for CleverType or Grammarly, and that we could also hit. **P1** means important gaps or pain that hold back adoption. **P2** means nice-to-have or differentiating features.

Effort: **S** is up to 2 days, **M** is up to 2 weeks, **L** is more than 2 weeks.

### P0

- [ ] **Show the free AI allowance before anyone hits it, and never ship a one-time trial.** Why: the paywall and free limit are behind 55% of CleverType's 1★ reviews. Our backend caps each install at 150 AI requests a day and then returns "You've reached today's AI limit", with no warning before. Show what's left and when it resets in the AI bar, and keep the cap well above normal use. Effort: S
- [ ] **Make the global AI cap fail gracefully.** Why: `DAILY_GLOBAL_AI_LIMIT` (50,000 a day) answers "Unicorn AI is very busy today. Try again tomorrow" to every user at once. A viral day would give everyone CleverType's "oops, something went wrong" 1★ moment together. Add alerting, per-install fair share, a cheaper fallback model, and a retry-later message instead of "tomorrow". Effort: M
- [ ] **Explain Android's input-method warning before the user sees it.** Why: the system's "may collect passwords and credit card numbers" dialog is behind 12% of CleverType's 1★ reviews. Unicorn also saves AI prompts, so this risk is higher for us. Add an onboarding card that predicts the dialog and says plainly what stays on the phone and what is sent (AI text only, on tap). Effort: S
- [ ] **Stay out of sensitive fields, verified by tests.** Why: privacy fear, and banking apps refusing CleverType. For password, OTP, number, PIN, email-address and payment fields, and for an allow-list of banking and wallet apps: no AI bar, no prompt capture, no word learning, no voice. Word learning already skips password and incognito fields, so extend that to capture and AI. Add instrumented tests. Effort: S–M
- [ ] **Add an autocorrect regression suite for the basics.** Why: autocorrect causes 20% of CleverType's complaints and about 40% of Grammarly's. Golden tests: `im→I'm`, `dont→don't`, `itll→it'll`, `thaf→that`, never changing a correct dictionary or personal word (`well`, `spotify`), a tapped suggestion replacing the whole word, backspace undoing the last autocorrect, and auto-capitalisation after `. `. Run them before every release. Effort: M
- [ ] **Never let typing depend on the AI or the network.** Why: the keyboard vanishing or showing blank keys is a top 1★ trigger for both apps. Keep the AI panel and bar failures isolated from the input view, add crash and ANR reporting for the input-method process, and add a watchdog that falls back to plain keys. Effort: M
- [ ] **Never lose the user's text in an AI rewrite.** Why: CleverType's grammar fix dropping most of a paragraph, and Grammarly's "inserts it twice". Add a length and meaning guard on Haiku output (reject results that drop more than about 30% of a long input), a before-and-after preview for long text, and one-tap undo after replacing. Effort: S

### P1

- [x] **Glide typing.** Done in 0.5.0: Unicorn's own decoder (`ai/unicorn/glide`), on by default, with a switch in You > Writing. On synthetic swipes with realistic noise it gets 97.2% of the 1,000 most common words first time (99.9% in the top three) and 93.4% of the 10,000 most common; the previous word lifts sentences to 98.6%. The emulator test injects the same swipes as real touches. `tools/glide-stress.sh` gates every release, with results in `docs/research/glide-typing-report.md`. Not yet tried by a real hand on a phone. Why: CleverType has it, and its bad swipe is a steady 1★ source (5%). Swipe users won't switch to a keyboard without it. AOSP LatinIME ships without a gesture decoder, so we need our own or a licensed one, and it should only ship once it's accurate. Effort: L
- [ ] **Multilingual typing, starting with Spanish (the US's second language) and bilingual English–Spanish suggestions, then Portuguese, French, Hindi/Hinglish and Indonesian.** Why: missing languages are 8% of CleverType complaints. The launch market is now the US (decided 2026-11-03), where about 1 in 8 people speak Spanish at home. Choose the layout separately from the language (QWERTZ, AZERTY), and suggest in two languages at once. Effort: L
- [x] **Keyboard height and position resize, plus one-handed mode.** Done in 0.5.2 as Size and position (⋯ in the toolbar, or You > Look). Drag handles change the height and width, slide the keys sideways and lift them off the bottom, with Reset; each orientation keeps its own size. One-handed docks left or right. Edge taps send a tap just beside narrowed keys to the outer key. Learn my aim moves each letter's touch area up to ¼ key toward where this person lands. QWERTY never changes. `tools/size-check.sh` and `tools/aim-check.sh` check it on the emulator. Not yet tried by a real hand on a phone. Why: CleverType's top "most relevant" review asks to move the keyboard up, and users call it "giant". Effort: M
- [ ] **Clipboard history.** Why: CleverType has one; users notice when it breaks or is missing. Keep it on the phone, expire items after 1 hour by default, and never save from password fields. Effort: M
- [x] **Number row toggle, and sound and haptics settings that work.** Done in 0.4.2: an always-on number row (a toggle to hide it is still open) and You > Writing > "When you tap a key". Why: small, frequently requested features. CleverType has no number row, and its sound and vibration settings are broken. Effort: S
- [ ] **Better voice typing.** Why: voice failures and a paywalled voice are CleverType 1★ reasons. Add a voice-language picker (Hindi in Devanagari, not Urdu), check that it works in Gmail and WhatsApp, and add an optional "clean up" pass on tap (fillers, punctuation) that reuses the AI bar. Effort: M
- [ ] **Emoji search, with results added to recents.** Why: a CleverType bug that drew 1★ reviews, and a basic feature on every major keyboard. Effort: M
- [ ] **Fast, visible AI.** Why: "takes like 10 seconds" (CleverType). Show progress at once, let the user cancel, aim for under 3 seconds at p90, and show a clear retry on timeout. Effort: S
- [ ] **Back up and restore words, shortcuts, themes and settings.** Why: CleverType users lose everything after a reinstall or a new phone, and the reviews saying so get many helpful votes. Use Android Auto Backup, and add a file export in You. Effort: S–M
- [ ] **Custom tones and custom slash commands.** Why: these are CleverType's power-user features, and Premium is the only way to get them there. Making them free is a clear win. Effort: M
- [ ] **Translate in 40+ languages, without dropping text.** Why: parity with CleverType, which says 40+, against our 18. Users complain that parts of the text go missing. Effort: S
- [ ] **A support loop with a human reply.** Why: bot-only support is about 10% of CleverType's 1★ reviews. Add in-app feedback with an optional diagnostics bundle (no typed text), and reply to Play reviews with a real answer, not "email us". Effort: S
- [ ] **No overlays, bubbles or nag banners.** Why: this is Grammarly's 49% lesson. Keep the AI bar as an opt-in, collapsible row, and never cover the text field or the suggestions. Effort: S

### P2

- [ ] **GIF and sticker search.** Why: requested from CleverType, which lacks it, so it's a differentiator. Effort: M
- [ ] **Photo and custom-colour themes with key borders.** Why: CleverType users want this and say its version breaks. Effort: M
- [ ] **Cursor control (space-bar swipe) and arrow keys.** Why: requested by former Fleksy and Gboard users. Effort: S–M
- [ ] **Tablet, foldable split layout and hardware-keyboard support.** Why: CleverType reviewers asked for both. Effort: M
- [ ] **Tone detection ("this sounds harsh").** Why: CleverType and Grammarly both have it. Effort: S
- [ ] **Locale currency on the symbols page (₹, ₱).** Why: a CleverType review asking for ₹ got 10 helpful votes. Effort: S
- [ ] **Accessibility pass: TalkBack, large keys, and dark mode in the companion app.** Why: Grammarly's removal hurt users with disabilities, and a CleverType reviewer asked for a dark settings screen for light sensitivity. Effort: M
- [ ] **Desktop or browser extension.** Why: CleverType's top plan bundles it. This is for after we reach Android product-market fit. Effort: L

### Already ahead, keep it that way

These are advantages over both apps. Don't lose them.

- Typing needs no account.
- AI is free.
- There's no accessibility service or overlay.
- Typing works offline.
- Prompt recall, Library and Insights are things neither competitor has.
