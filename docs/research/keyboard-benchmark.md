# Keyboard benchmark: iPhone, Samsung, Gboard and Unicorn

November 2026. This compares what the leading built-in keyboards do with what people expect from them, and what Unicorn has. It ends with the measurable targets and a checklist in priority order.

Sources: Apple and Samsung feature pages, press coverage of iOS 26 and One UI 8 (linked at the end), and long-standing behaviour of these keyboards. Rows marked *(verify)* are features I could not confirm in a 2026 source.

## What people expect, in order

The pattern across iOS 26 complaints, Samsung Keyboard reviews and Gboard forums:

1. **It types what I meant.** Tap accuracy and autocorrect are the whole product. iOS 26's fast-typing bug ("keys register the wrong letter") became a news story and took Apple until iOS 26.4 to fix. People notice even small regressions.
2. **It never lags or disappears.** Lag, the keyboard not showing, and missed keystrokes are the most common 1★ reasons.
3. **Autocorrect I can trust and undo.** It must not "correct" names and slang. Undoing a correction should be one backspace, and it must learn from that.
4. **It knows my words.** Names, places, slang and other languages, learned without any setup.
5. **Swipe (glide) typing.** Most Android users swipe at least sometimes, and it's expected by default on Gboard and Samsung.
6. **My languages, mixed.** In India especially: Hinglish or Tenglish (Hindi or Telugu written in English letters), bilingual suggestions, and switching without a key. iOS 26 added bilingual suggestions in 10 languages.
7. **Clipboard, emoji search, GIFs and stickers**, one tap away.
8. **Comfort settings:** keyboard height, one-handed or floating mode, a number row, key sound and vibration strength, and long-press for symbols.
9. **AI writing that's built in and free.** Samsung's Writing Assist is now free in every app and keyboard (One UI 7 and up), and Apple has Writing Tools. Paying for basics (CleverType, Grammarly Premium) is a common complaint.
10. **Privacy:** no cloud for typing, a clear incognito mode, and nothing taken from password fields.

## Feature matrix

| Area | iPhone (iOS 26) | Samsung Keyboard (One UI 8) | Gboard | Unicorn 0.3.1 |
|---|---|---|---|---|
| Layout and look | Liquid Glass keyboard (in updated apps), light/dark | Themes, high-contrast, custom key colours | Themes, photo backgrounds | Classic iPhone-like layout plus 8 themes ✅ |
| Autocorrect and suggestions | On-device language model, inline predictions | Predictive text, auto-replace | Strong, personalized | AOSP engine: good basics, no neural model ⚠️ |
| Learns new words | Yes, silently | Yes, plus "personalized" toggle | Yes, plus personal dictionary | Yes, plus a Your words page and undo-learning ✅ (0.3.1) |
| Text shortcuts | Text replacement | Text shortcuts | Personal dictionary shortcuts | Yes, with presets ✅ |
| Swipe or glide typing | QuickPath | Yes | Yes | ❌ No open-source engine yet |
| Multilingual / bilingual | Bilingual suggestions, 10 languages | Many languages, swipe to switch | 900+ languages, transliteration (Hinglish) | English only ❌ |
| Voice typing | On-device dictation with punctuation and emoji | Samsung voice input / Google | Google voice, offline packs | Android speech recognizer ✅ (no punctuation or emoji commands) |
| Clipboard | System paste suggestions only | Clipboard panel with pinned items | Clipboard with pins and screenshots | ❌ |
| Emoji search, GIFs, stickers | Emoji search, Genmoji, Memoji stickers | Emoji, stickers, GIFs, Bitmoji | Emoji Kitchen, GIF and sticker search | Emoji panel only, no search ❌ |
| AI writing | Writing Tools (proofread, rewrite, summarize) | Writing Assist (tone, spelling, translate, chat assist) | Proofread (Pixel), Gemini | Grammar, Tone, Translate, Ask Unicorn with /commands, email-aware, reply from notifications ✅ (free) |
| Translate while typing | Live Translation in Messages | Chat translation | Built-in translate | Translate tool (18 languages) ✅ |
| One-handed or floating | One-handed | One-handed, floating, split | One-handed, floating | ❌ |
| Height and number row | No | Size slider, number row | Height setting, number row | ❌ |
| Cursor control | Space bar trackpad | Space bar swipe | Space bar swipe, swipe-to-delete | AOSP space swipe? *(verify)* |
| Undo and select | Shake or 3-finger undo | Text editing panel | Text editing panel | Select-all / clear / undo key ✅ |
| Handwriting | Scribble (iPad) | Handwriting mode | Handwriting layout | ❌ |
| Incognito and privacy | On-device | Incognito in private browsing | Incognito mode, federated learning | On-device words; no incognito toggle ⚠️ |
| Recall of your AI prompts | No | No | No | Yes ✅ (unique) |

## Benchmarks to hit

Measured on a mid-range phone (Snapdragon 7-series or Dimensity 8000-class), release build.

| Metric | Target | Why / reference | How to measure |
|---|---|---|---|
| Keyboard appears after tapping a field | ≤ 150 ms warm, ≤ 400 ms cold | People call anything slower "laggy" | Perfetto trace, `onStartInputView` to first frame |
| Touch to letter on screen | ≤ 30 ms (p95 ≤ 50 ms) | Feels instant at 60–120 Hz | High-speed video or `InputEvent` to `commitText` trace |
| Suggestions update after a key | ≤ 40 ms p95 | Must never trail fast typists | Instrument `performUpdateSuggestionStripSync` |
| Dropped or wrong-key taps at 6 keys/s | < 0.5% | The iOS 26 complaint | Scripted tap replay through `adb` or uiautomator |
| Autocorrect accuracy | ≥ 92% of typos fixed, ≤ 1% of correct words changed | Below this, people turn autocorrect off | Replay a typo corpus (for example Birkbeck plus Indian names) |
| Names left alone | 0 corrections after one undo | "It keeps changing my name" | Test list of 200 Indian names |
| Next-word prediction hit | ≥ 25% top-3 | Gboard-class | Held-out chat corpus |
| Memory in use | ≤ 120 MB PSS while typing | Low-end phones kill heavy keyboards | `dumpsys meminfo ai.unicorn.app` |
| Crash-free sessions | ≥ 99.9% | Play vitals "bad behaviour" threshold is 1.09% | Play Console vitals |
| ANR rate | < 0.2% | Play vitals threshold is 0.47% | Play Console vitals |
| Battery | < 1% per hour of typing | — | Battery historian |
| AI tool answer | first answer ≤ 2.5 s p90 | Writing Assist feels about this fast | Worker logs plus client timer |
| Download size | ≤ 20 MB | Play listing conversion | Bundle explorer |

## Checklist for Unicorn, in priority order

P0: needed before a public launch. P1: needed to match Samsung and Gboard. P2: delight and differentiation.

### P0
- [ ] **Speed and accuracy benchmarks** above: build a replay harness and run it on every release. *(M)*
- [ ] **Never correct names**: learned words and anything undone once are never autocorrected again (partly done in 0.3.1). Test with an Indian names list. *(S)*
- [ ] **Crash and ANR reporting** (privacy-safe, no text). *(S)*
- [ ] **Incognito**: automatic in private browser tabs, plus a manual toggle in the bar. *(S)*
- [ ] **Clipboard panel**: recent items with pin and auto-expiry, never from password fields. *(M)*
- [ ] **Keyboard height and a number row setting.** *(S)*
- [ ] **Space bar cursor control and swipe-left-to-delete words.** *(S, check what AOSP already does)*

### P1
- [ ] **Swipe typing**: evaluate open gesture decoders (for example the one in FUTO Keyboard, or train our own on the AOSP proximity model). *(L)*
- [ ] **Hinglish and Tenglish transliteration**, plus Hindi and Telugu dictionaries. This matters most for an India launch. *(L)*
- [ ] **Emoji search** and recent emoji, plus GIF/sticker search via a licensed provider (Tenor or Giphy with keys). *(M)*
- [ ] **Voice typing polish**: punctuation commands ("comma", "new line"), live text in the field, offline language packs. *(M)*
- [ ] **One-handed and floating modes.** *(M)*
- [ ] **Key sound, vibration strength and popup-on-press settings** in You. *(S)*
- [ ] **Long-press for symbols** shown on the keys (hint labels as an option). *(S)*

### P2
- [ ] **Neural next-word prediction** on-device (small LSTM or transformer, under 10 MB). *(L)*
- [ ] **Handwriting layout.** *(L)*
- [ ] **AI smart compose**: grey inline completion while typing, accepted with a swipe. *(M)*
- [ ] **Screen-aware replies** through the Android assistant API (the user sets Unicorn as their digital assistant), which avoids accessibility-service policy problems. *(L)*
- [ ] **Emoji Kitchen-style combos**, stickers from your Unicorn card. *(M)*

## Sources

- [9to5Mac: iOS 26.4 fixes iPhone keyboard](https://9to5mac.com/2026/03/18/ios-26-4-fixes-iphone-keyboard/)
- [TechRadar: iOS 26's autocorrect is broken](https://www.techradar.com/phones/ios/youre-not-bad-at-typing-ios-26s-autocorrect-is-broken-and-theres-still-no-fix-in-ios-26-2)
- [AppleInsider: keyboard accuracy bug quashed in iOS 26.4](https://appleinsider.com/articles/26/03/18/keyboard-accuracy-bug-quashed-in-ios-264)
- [Apple Discussions: iOS 26 keyboard issues](https://discussions.apple.com/thread/256253035)
- [Nerdschalk: 36 new features in iOS 26](https://nerdschalk.com/36-new-features-in-ios-26-that-you-might-have-missed-but-apple-made-sure-you-knew/)
- [Samsung: Writing Assist on Galaxy](https://www.samsung.com/us/support/answer/ANS10000943/)
- [PhoneArena: One UI 7 frees Galaxy AI writing tools from Samsung's keyboard](https://www.phonearena.com/news/Samsungs-One-UI-7-frees-Galaxy-AI-writing-tools-from-its-own-keyboard_id167226)
- [SamMobile: One UI 8 brings Text to Image to Samsung Keyboard](https://www.sammobile.com/news/one-ui-8-galaxy-ai-text-to-image-samsung-keyboard/)
