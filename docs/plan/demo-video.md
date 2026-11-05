# Unicorn demo video: plan

Status: proposal, 2026-11-04. Nothing has been built yet. The only things made are a test clip and a 16:9 preview in `docs/plan/demo-video/`, for judging capture quality (see "Test clip findings").

## Decisions so far

The user made these calls on 2026-11-04:

| Question | Decision |
|---|---|
| Where it runs | Several cuts from one shoot: a Google Play cut, a 15 s social cut, and a silent website loop |
| Sound | Captions and music. No voiceover |
| Capture | Emulator, with scripted takes driven over adb |
| Budget | $0, free tools only |
| AI waits | About 0.7 s of the real spinner, then a cut to the real answer, with no label |

Calls I made, which the user can overturn, are marked **(my call)** below.

## Why make it, and what good looks like

People decide about an app in the first few seconds of a store visit, mostly with the sound off. The video has to show one moment of "oh, that's useful" before anything else. That moment should be the real app doing it, not a title card.

Success means:

- someone watching muted understands what Unicorn does within 5 seconds;
- every frame is real app footage that a person could reproduce on their own phone;
- any shot can be re-shot after an app change by re-running one script, without redoing the edit.

### Non-goals

- Explainer-length content, tutorials or onboarding. This is a first impression, not a manual.
- A voiceover in this round.
- Paid ads creative, such as ad variants or A/B tests.

### Anti-goals (failure even if the video looks great)

- The words "keyboard" or "journal" appear anywhere: on screen, in titles, descriptions, file names that get published, or captions.
- Any AI output is edited, staged or typed by hand to look better than what the app produced.
- A fake third-party app appears as if it were real. The fake ChatGPT screen that `harness/` installs as `com.openai.chatgpt` must never be in a shot.
- Hunches are shown as facts or "trending". Every Hunch on screen keeps its "Probably on …'s mind" label and its reason.
- An AI limit, counter or allowance is visible.
- The video shows a feature that hasn't passed a real-phone check (see "Release gate").

## The three cuts

| Cut | Length | Frame | Sound | Where |
|---|---|---|---|---|
| **A: Play** | 30 s | 16:9, 1920×1080 | Music, burned-in captions | YouTube (unlisted), linked from the Play listing |
| **B: Social** | 15 s | 9:16, 1080×1920 | Captions; music added in each app at upload | TikTok, Instagram Reels, YouTube Shorts |
| **C: Website loop** | 8–10 s | Phone screen only, about 1080×1350 | Silent | Hero of the live site |

**Why the Play cut is landscape (my call).** YouTube automatically treats a vertical video under 3 minutes as a Short. Google's help page for the preview video doesn't say whether a Shorts URL works in that field, and third-party guides say it doesn't. A 16:9 frame with the phone screen in the middle and the captions beside it avoids that risk entirely. It also avoids the "black bars on a portrait video" Google warns about. Google allows both orientations, so this costs nothing.

**Why 30 seconds.** Play autoplays only the first 30 seconds, muted. Anything after that is seen only by people who tap play.

## Story

### The opening moment

The video opens on the Tone rewrite. A messy, real-sounding text is in a chat. One tap on 🎭 Tone, and a clean version replaces it in place. It's the most "magic" moment Unicorn has. It reads with the sound off, and it matches the website's hero line ("A little magic in everything you type.") and its example message. Glide typing is the runner-up: it looks good, but people already expect it from their phone, so it says less about Unicorn.

### Features in, and out

In: Tone, Ask Unicorn with /reply, glide typing, Translate, Hunches, the growing Unicorn and its card, and Size and position as a quick beat.

Out of this round:

- **Prompt saving and community trends.** Capture inside the real ChatGPT, Claude, Perplexity, Grok and Google apps hasn't been checked on a real phone, and the emulator only has the fake ChatGPT screen. It comes in a later cut, once it's verified. The honest way to show it then:
  - capture Unicorn's own Library screen after real use on the user's phone;
  - write "the AI apps you use" rather than show other companies' apps or logos.
- **Grammar, the number row, emoji and voice typing.** They're useful, but every phone has them, and 30 seconds is short.
- **The app lock.** It's a good privacy point, but it plays better on the website than in a 30-second clip.

### Cut A: Play, 30 s

On-screen copy follows the website's voice: short, warm and plain, in US English. The captions sit beside the phone, at most 7 words each, and each stays up for at least 2 seconds.

| # | Time | Shot (real app footage) | Feature | On-screen copy |
|---|---|---|---|---|
| 1 | 0:00–0:03 | A chat with a messy text already typed: "hey sry cant make it tmrw nite, smthing came up at wrk. can we do sat instead??" | — | Type the way you text. |
| 2 | 0:03–0:07 | Tap 🎭 Tone. The rewrite card appears, and a tap puts it in the chat. | Tone | Tap once. Send it the way you mean it. |
| 3 | 0:07–0:12 | The friend's new message arrives in the stage chat. Long-press it, tap Copy, tap ✨ Ask Unicorn, type "/reply sure, saturday works", and tap the answer to insert it. The panel's "Replying to the text you copied" stays in shot. | Ask Unicorn | Stuck on a reply? Ask Unicorn. |
| 4 | 0:12–0:16 | Glide "see you at noon", with the trail visible. | Glide typing | Glide to type. |
| 5 | 0:16–0:19 | Tap 🌐 Translate and pick Spanish. The message turns into Spanish. | Translate | Say it in Spanish, too. |
| 6 | 0:19–0:21 | Drag the resize handle, and go one-handed. | Size and position | Sized for your hand. |
| 7 | 0:21–0:25 | The Near you tab: Hunch cards with "Probably on …'s mind", each with its reason. | Hunches | Playful guesses about your town. |
| 8 | 0:25–0:28 | The first-run hatch: the sparkle burst and "Your Unicorn has hatched", then the top of its one-of-a-kind card. | Growing Unicorn | Hatch a Unicorn no one else has. |
| 9 | 0:28–0:30 | End card: logo on lilac, wordmark. | — | Unicorn. A little magic in everything you type. |

Notes:

- No "Download now", "Free", "Best", "#1" or "New". Google's preview-video guidance advises against calls to action, price claims and ranking words.
- Shots 1 to 5 happen in one chat on a neutral "stage" screen (see "Capture"), so the story reads as one conversation.
- **Shot 3 shows the copy step (my call).** On a real phone, /reply can read the chat itself from message notifications, but only for the roughly 20 real messaging apps listed in `RecentMessages.kt`. The stage isn't one of them, and adding it would change app code. Copying the message first is the other real way /reply works, so the shot shows that. An instruction-only /reply would look as if it had read the chat when it hadn't, so it's out.
- **Shot 7 is on Near you, not Today (my call).** Today shows Hunches in a compact list without their reasons, and every Hunch on screen must keep its reason.
- **Shot 8 is the first-run hatch, not a grown Unicorn (my call).** Stardust comes only from sharing anonymous trends from saved prompts. That's the feature this round leaves out, and on the emulator it could only be faked by setting the number by hand. The hatch is real for every new user.
  - Each take needs Unicorn's data cleared on emulator-5556, then `ime enable` and `ime set` again.
  - The card's last line ("It grows sparkles, wings and more while you share anonymous trends.") promises the left-out feature, so the shot is framed or cut before that line.
- **Legibility.** At 16:9 the whole phone is only about 420 px wide, so app text comes out about 18 px tall: readable on a monitor, too small on a phone. Each shot punches in (scales up to 1.6–2×) on the part that matters: the text field, the toolbar, or the result card. App text should be at least 28 px tall at 1080p.
- **AI waits (user decision, 2026-11-04): short real spinner, then cut.** The real wait from tap to answer is about 2.5 s. Each AI shot keeps about 0.7 s of the real spinner, then cuts to the real answer, with no label. The answer itself is never retyped, edited or swapped for a better one from another take.
- Typing may be sped up 2× in the edit. Nothing else is retimed.

### Cut B: Social, 15 s (9:16)

| # | Time | Shot | On-screen copy |
|---|---|---|---|
| 1 | 0:00–0:02 | Full-screen messy text, in close-up | you, texting at 1am: |
| 2 | 0:02–0:06 | 🎭 Tone, then the clean version lands | Unicorn, one tap later: |
| 3 | 0:06–0:09 | /reply answer inserted | Stuck on a reply? Ask Unicorn. |
| 4 | 0:09–0:12 | Unicorn hatching, then the card | Hatch your own Unicorn. |
| 5 | 0:12–0:15 | End card | Unicorn. Free on Android. |

"Free on Android" matches the website. It's fine on social, but it stays out of the Play cut. Captions sit inside each platform's safe area: clear of the bottom 20% and the right-hand button column.

### Cut C: Website loop, 8–10 s

This cut is only shots 1 and 2 of Cut A (messy text, Tone, clean text), cropped to the phone screen, with no captions. The website already has the copy. It loops seamlessly with a 0.5 s cross-fade back to the messy text. Export H.264 MP4 and WebM under 2 MB, plus a poster frame. Putting it on the site is a website change and a deploy, so it waits for the user's go-ahead.

**Where it goes (my suggestion, to settle at the website step).** The hero's right side is the live "Probably on your town's mind" Hunches panel, and it should stay. The loop fits best in the "Fix it before you send it." section, next to or in place of the interactive Tone demo, which uses the same message. That section's slot sets the final crop. About 1080×1350 is the starting guess.

## Capture

### Recommended: scripted emulator takes (the user's choice)

Every shot is a shell script in `tools/demo/` that:

1. resets the scene (app data where needed, the stage chat, the field text, the input method);
2. starts the emulator's own recorder over adb: `adb emu screenrecord start --fps 30 --bit-rate 16000000 <file>.webm`. It gives 1080×2400 VP9, one shot per file;
3. drives the app with timed taps at the real key positions, using the same key map the glide tests use;
4. stops with `adb emu screenrecord stop`, and keeps every take as `raw/<shot>-take<N>.webm`.

**Why the emulator's recorder and not `adb shell screenrecord`.** Measured on the test clip, the emulator's recorder runs on the host and keeps the frames the screen draws:

| Moment | `adb shell screenrecord` | `adb emu screenrecord` |
|---|---|---|
| Panel opening | 13 distinct frames/s | 28 |
| Spinner turning | 4 | 18–27 |

`adb shell screenrecord` encodes inside the emulator and drops frames on this busy host. The text is equally sharp from both.

`tools/demo/shoot.sh all` re-shoots everything after an app change. The edit points at clip files by name, so a re-shoot re-renders without editing.

**Clean status bar:** `tools/demo/clean-bar.sh on|off`, using SystemUI demo mode:

- `settings put global sysui_demo_allowed 1`;
- a fixed clock;
- battery at 100% and not charging;
- full Wi-Fi and cellular bars;
- no notification icons;
- Do Not Disturb on, so no heads-up notification lands mid-take;
- animations at normal speed;
- a fixed locale (en-US) and font scale;
- auto-rotate off.

`off` puts everything back.

The test clip also showed:

- **Gesture navigation.** The clean bar switches the emulator to gesture navigation (the thin pill), which looks current. The three-button bar looks dated.
  - Switching restarts the system UI, which wipes demo mode, so the switch comes first.
  - `off` restores the emulator's exact original state: both overlays on, with three-button on top. Its size checks expect three buttons.
- **The status bar.** The bare test app draws white status icons on a white background. The test clip used `bars -e mode opaque` (a black bar). The stage screen sets a light status bar itself, so the final shots won't need it.

**Glide strokes (my call).** `adb shell input` starts a new process for every touch point, which is far too slow for a natural glide. A small demo instrumentation in the existing `glidetest/` test app draws glides with the human-like paths the glide tests already use (`SwipeSynth`), at finger speed, so the trail looks real. `glidetest/` is a test app and never ships.

**The stage (my call).** Typing shots need a text field in something that looks like a conversation. The honest options are:

- **a neutral, unbranded chat screen in the `glidetest/` test app (recommended):** a plain thread with a made-up first name, no logos, no imitation of any real messenger. Unicorn works in any app's text field, so this shows true behavior without borrowing anyone's brand;
- Google Messages on the emulator: a real app, but it's Google's brand in our store video, and the "don't imply endorsement" rule makes it a risk;
- the bare `glidetest` field as it is: honest, but it looks unfinished.

**Hunches** need two things set before the shot, or they'll be about the wrong place:

- turn on "📍 Make hunches about my neighborhood" on Near you;
- set the emulator's location to a US town with `adb emu geo fix <lon> <lat>`.

Without both, the server guesses from the connection's location, and this machine's connection is in India. The weather and calendar are taken as they are on the day. The town is picked so the guesses read naturally.

**AI results** are real backend answers. Each AI shot is recorded 3–5 times, and the best take is picked as recorded. The text is never retyped or edited.

### Other capture options considered

| Option | Verdict |
|---|---|
| `adb shell screenrecord` on the emulator | It works (1080p, 3 minutes per file), but it drops frames on this host (see above). Keep it as a fallback. |
| Real phone with scrcpy (`--record`) | It gives real-finger glides and real-device feel. Samsung's One UI may not honor SystemUI demo mode, so the status bar may need masking. It stays available later for one or two shots if a real gesture matters. |
| `adb screenrecord` on a real phone | It has the same 3-minute limit, and it can't be scripted as tightly, because a person drives it. |

**Emulator load.** The user's own emulator-5554 runs at the same time and loads the host heavily, which is the main reason for dropped frames. For motion-heavy shots (glides, the hatch animation, resizing), shoot when it's idle, or ask the user to close it for the shoot. This session never touches it.

### Device frame (my call)

Use a generic rounded screen with a soft shadow, drawn in the edit. Don't use a Pixel or Samsung frame. Google's guidance for the feature graphic, which is the video's cover image, advises against device imagery and third-party trademarks. A generic frame also never goes out of date.

## Edit and motion

### Recommended: Remotion, which I build and render end to end

Remotion makes video from React code, and its renderer bundles its own FFmpeg. A `video/` folder (outside the app) holds:

- `script.json`: every caption, its timing, and which clip it sits on. Copy edits are one-line changes and a re-render;
- compositions `Play30` (1920×1080), `Social15` (1080×1920) and `HeroLoop`;
- brand pieces from the website: Bricolage Grotesque for captions, violet #7430A4 and deep #561F7E, lilac #F3EEFA backgrounds, and the line-drawn unicorn from `branding/`;
- the output: MP4 (H.264, AAC), an `.srt` caption file for YouTube, and the website's MP4/WebM loop.

**License:** Remotion is free for individuals and for companies with up to 3 people. If Unicorn becomes a company of 4 or more, it needs a Remotion company license (from about $25 per seat per month, per third-party sources). Check remotion.pro/license before that point.

### Other editing options

| Tool | Who drives it | Verdict |
|---|---|---|
| Remotion + FFmpeg | Claude | Recommended. Repeatable, scriptable, free for this team size, and every cut renders from one source. |
| Motion Canvas | Claude | MIT-licensed and good for animated diagrams, but weaker at cutting screen recordings together. |
| DaVinci Resolve (free) | The user | Best for a hands-on polish pass if the user wants to fine-tune timing by eye. It takes Remotion's output or the raw clips. |
| CapCut | The user | Avoid for this. Its stock music and templates carry mixed commercial-use terms, and its terms grant CapCut a broad license to uploaded content. |
| After Effects | The user | Paid. Overkill for screen-recording edits. |

## Sound

**Music (free).**

- **Cut A** goes on YouTube, so it uses a track from the YouTube Audio Library. Filter for "attribution not required", so no credit is needed.
  - This matters more than it looks: royalty-free tracks from other sites, such as Pixabay, often get Content ID claims on YouTube. A claim puts ads on the video, and Play rejects preview videos with ads.
  - YouTube Audio Library tracks are cleared for YouTube. Their use outside YouTube is unclear, so they stay on YouTube.
- **Cut B:** add music inside each app at upload, from TikTok's Commercial Music Library, Meta's Sound Collection for Reels, or the YouTube Audio Library for Shorts. Business accounts may only use those libraries. The exported file has no music, so one export works everywhere.
- **Cut C:** silent.

**Who picks the music.** The user picks it, because it's a matter of taste, and downloading from the Audio Library needs their YouTube Studio login. I can shortlist styles: a light, plucky acoustic or synth-pop track at about 100–115 BPM with no vocals. I cut the edit to its beat.

**Captions and accessibility.** The burned-in captions carry the story, so the video works muted and for deaf or hard-of-hearing viewers. The YouTube upload gets an `.srt` too (Google recommends captions for preview videos). The captions are high-contrast and at least 48 px tall at 1080p.

There is no voiceover, so blind viewers get little from the video. The Play listing's text and screenshots carry that. A later round could add an audio-described version.

## Google Play preview video rules

From Google's Play Console help ("Add preview assets to showcase your app", support.google.com/googleplay/android-developer/answer/9866151, and "Best practices for your store listing", answer/13393723), checked 2026-11-04.

**Required:**

- The video is supplied as one YouTube video URL. It can't be a playlist or channel URL, and it can't carry a timecode or other parameters.
- Its privacy is public or unlisted, not private.
- Ads are off. Turning off monetization may not be enough if the video contains copyrighted material, such as claimed music. In that case, use another video.
- It isn't age-restricted.
- It's embeddable.
- All content follows the Developer Program Policies (metadata, intellectual property, impersonation). Failing a requirement "may result in removal or suspension".

**Strongly recommended:**

- Only the first 30 seconds autoplay, muted.
- Show the real app within the first 10 seconds, and make about 80% of the video real app footage.
- Use copy for context, because the sound is off.
- Avoid "Best", "#1", "Top", "New", "Discount", "Sale" and "Million Downloads", as well as rankings, testimonials, prices and calls to action ("Download now", "Install now", "Try now").
- Don't compare the app to others, and don't imply endorsement by another company.
- Make the text legible and on screen long enough to read.
- Avoid shots of people tapping the device.
- Don't put black bars beside a portrait video.
- Use captions.
- Localize the video for other markets later. US English only for now.

**Not stated by Google (unconfirmed):**

- whether a YouTube Shorts URL works in this field (third-party guides say no);
- any resolution, frame-rate or maximum-length spec.

We plan for 1080p at 30 fps and a 30-second cut.

**YouTube settings at upload (the user does this):**

- unlisted;
- monetization off;
- "not made for kids";
- no age restriction;
- embedding allowed;
- title and description free of "keyboard" and "journal".

The feature graphic (1024×500) is the video's cover until it plays, so it should match the video's first frame.

## Release gate

Nothing is published until the features shown have passed a real-phone check on the user's Samsung. The emulator footage is real, but the app hasn't been tested on a real phone yet, and a store video makes a promise. Capture and editing can go ahead in parallel.

## Who does what

| Task | Claude | The user |
|---|---|---|
| Approve this plan and the on-screen copy | | ✓ |
| Capture kit in `tools/demo/` (clean bar, shot scripts, glide demo, neutral chat stage) | ✓ | |
| Shoot every take on emulator-5556 and pick the best | ✓ | Reviews the picks |
| Remotion project, all three cuts, captions, `.srt` | ✓ | |
| Pick the music track from the YouTube Audio Library and drop the file in `video/music/` | | ✓ |
| Mix the music to the edit and render the finals | ✓ | |
| Real-phone check of the features shown (release gate) | Writes the checklist | ✓ Runs it on the Samsung |
| Upload Cut A to YouTube, set it up, paste the URL into Play Console | Writes the title and description | ✓ |
| Post Cut B on TikTok, Reels and Shorts, adding music in each app | Writes the captions and hashtags | ✓ |
| Put Cut C on the website | ✓ Builds it | ✓ Approves the deploy |

## Costs

| Item | Cost |
|---|---|
| Remotion (individual) | $0 |
| FFmpeg and DaVinci Resolve | $0 |
| YouTube Audio Library and the platforms' own music libraries | $0 |
| Captions (written by us) | $0 |
| **Total** | **$0** |

**Later, only if wanted:**

| Item | Cost |
|---|---|
| Epidemic Sound Commercial, one month, for one track usable everywhere | about $50 |
| ElevenLabs Starter, for an AI voice with a commercial license | about $6/month |
| A human voice actor or motion editor | $150–500 |

Third-party price checks from October 2026 should be confirmed before buying anything.

## Rough steps

1. **The user** approves this plan and the copy above, or edits it.
2. **Claude** builds the capture kit (`tools/demo/`) and the neutral chat stage in `glidetest/`. No Unicorn app code changes.
3. **Claude** shoots all takes on emulator-5556 under the shared lock, and puts the best takes in a contact sheet for the user.
4. **Claude** builds the Remotion project and renders drafts of all three cuts with placeholder music.
5. **The user** reviews the drafts and picks the music.
6. **Claude** renders the finals, plus the `.srt`, the poster frames and a matching feature-graphic suggestion.
7. **The user** runs the real-phone check (release gate).
8. **The user** uploads and posts. Claude puts Cut C on the website after a go-ahead.

After any app change, a re-shoot is step 3 (`tools/demo/shoot.sh all`) followed by a re-render.

## Test clip findings

The test was recorded on 2026-11-04 on emulator-5556 with Unicorn 0.5.2, in the `glidetest` text field, with the clean status bar on.

| File | What it is |
|---|---|
| `demo-video/poc-tone-capture.mp4` | 9 s, 1080×2400, straight from the emulator's recorder. The only change is one cut inside a still 6 s hold while the results sat on screen. |
| `demo-video/poc-tone-play-preview.mp4` | 10 s, 1920×1080: the same footage in the planned Play-cut look (lilac canvas, rounded screen, captions). It's a rough FFmpeg mock-up, not the Remotion edit. |
| `demo-video/poc-tone.sh` | The scripted take: clean bar, scene, recording, taps, restore. |
| `demo-video/poc-frame.py` | The cut and the preview. |
| `demo-video/raw/` | Every take. `take1.mp4` is `adb shell screenrecord`, `take2.webm` and `emu-recorder.webm` are the emulator's recorder, and the `.times` files hold the event times. |

What it showed:

- **Real and repeatable.** One script run gives a usable take. The rewrite in the clip ("Hey, sorry, I can't make it tomorrow night. Something came up at work. Could we do Saturday instead?") is the backend's real answer, untouched.
- **The real wait is about 2.5 s** from tapping Tone to the results. The text lands in the field about 0.2 s after a card is tapped.
- **Sharpness is pixel-perfect** at 1080×2400. The emulator's recorder keeps motion smooth, about 27 fps while the panel and spinner move.
- **Still to fix:**
  - the bare test app's look: the white status icons and the empty white screen. The stage screen fixes both;
  - small app text at 16:9 (punch-ins);
  - stale suggestions in the suggestion row from earlier tests ("hello", "Ok", "I"). The stage scene should start from fresh app data.
- **Not done here:** glide strokes, which need the planned demo instrumentation, music, and Remotion.
