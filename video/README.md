# Unicorn videos

The launch video for X is planned in `docs/plan/demo-video-x.md`.

- **Format:** vertical, 1080×1920, about 57 s, 30 fps.
- **Build:** rendered with [Remotion](https://www.remotion.dev) 4.0.538 from real emulator takes.
- **License:** Remotion is free for individuals and companies of up to 3 people. Above that, it needs a company license.

## Re-shooting after an app change

All phone footage comes from emulator-5556, driven by scripts.

1. Take `V:\nidi\unicorn\.shared.lock`.
2. Run `bash tools/demo/clean-bar.sh on`. It sets 9:41, full battery, no notifications and gesture navigation.
3. Run `bash tools/demo/shot.sh <name>` for each shot, or a `setup-*` file to prepare the app off camera. The steps are in `tools/demo/shots/<name>.txt`.
   - The test app `glidetest/` plays them: `StageActivity` is the plain chat screen, and `DemoShots` taps, types and glides on Unicorn's real keys.
   - Each take lands in `video/takes/<name>.webm`, with its step times in `<name>.log`.
4. Run `bash tools/demo/clean-bar.sh off` and release the lock.

**Order matters.**

- `firstrun` clears Unicorn's data, so it goes first.
- Then:
  1. `setup-theme` (the Aurora key theme);
  2. `setup-clear`;
  3. `google`;
  4. `setup-extra`;
  5. `library`;
  6. `recall`.
- After that, the chat shots in any order.

## Cutting and rendering

1. Edit the segment times in `video/cuts.json` if a re-shoot moved them, using the step logs.
2. Run `python -I tools/demo/cut.py`. It writes the clips to `video/public/clips/`.
3. From `video/`, run `npx remotion render src/index.ts XLaunch out/unicorn-x-launch.mp4 --codec h264 --crf 16`.

`npx remotion studio src/index.ts` opens a live preview with a timeline. Captions, zoom boxes and timing are in `src/XLaunch.tsx`, and the bar grid is in `src/theme.ts`.

## Music

"Take this Higher" by Michael Ramir C., from Mixkit (https://mixkit.co/free-stock-music/edm/, track 1012), is in `public/music/take-this-higher.mp3`.

The Mixkit Stock Music Free License was read on 2026-11-04:

- **Allowed:** commercial and non-commercial projects, "Social Media video posts" and "Online marketing ads". No credit is required.
- **Not allowed:** CDs, DVDs, TV and radio broadcasts, video games, and remixing the track into a music-only work.

If YouTube or another platform raises a Content ID claim, forward it to team@mixkit.co.

## Rules the footage keeps

- Every phone shot is the real app. AI answers are real and unedited.
- AI waits keep about 0.7 s of the real spinner, then cut to the answer.
- Typing and gestures may be sped up. Nothing else is retimed.
- The five AI apps' icons, in `public/ai/` from their Google Play listings, are their makers' trademarks. They're only in the `AiIcons` scene, so they can be swapped for name chips.
- The word "keyboard" is allowed in this X video only. The Play listing, the website and the app keep the no-"keyboard" rule.
