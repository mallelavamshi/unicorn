"""Throwaway proof of concept for docs/plan/demo-video.md: turns one raw emulator take into
(1) the capture as it came off the emulator, cut down to under 10 s, and (2) a 16:9 preview of the planned Play-cut
look (lilac background, rounded phone screen, captions beside it).

    python -I docs/plan/demo-video/poc-frame.py RAW SEGMENTS HOLD CAPTION_SWITCH

SEGMENTS ("a-b,c-d", seconds) are the parts of the take both outputs play, so a still hold can be shortened without
touching anything that moves. The preview also opens on the first frame held for HOLD seconds. CAPTION_SWITCH is when,
in the preview, the second caption replaces the first.
The real edit is planned in Remotion; this only shows the direction with FFmpeg and Pillow.
"""
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FF = imageio_ffmpeg.get_ffmpeg_exe()
HERE = Path(__file__).resolve().parent
W, H = 1920, 1080
LILAC, INK, VIOLET = (243, 238, 250), (28, 16, 53), (116, 48, 164)
PHONE_H = 940
PHONE_W = round(PHONE_H * 1080 / 2400 / 2) * 2
PHONE_X, PHONE_Y = 440, (H - PHONE_H) // 2
RADIUS = 46
BOLD = "C:/Windows/Fonts/seguisb.ttf"
REGULAR = "C:/Windows/Fonts/segoeui.ttf"


def background(path: Path) -> None:
    """Lilac canvas with the phone's soft shadow and a small wordmark baked in."""
    img = Image.new("RGB", (W, H), LILAC)
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (PHONE_X, PHONE_Y + 18, PHONE_X + PHONE_W, PHONE_Y + PHONE_H + 18), RADIUS, fill=95)
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    img.paste((86, 31, 126), (0, 0), shadow)
    glyph = Image.open(HERE.parents[2] / "branding" / "unicorn-glyph.png").convert("RGBA")
    glyph.thumbnail((64, 64))
    img.paste(glyph, (1520, 950), glyph)
    ImageDraw.Draw(img).text((1594, 958), "Unicorn", font=ImageFont.truetype(BOLD, 40), fill=VIOLET)
    img.save(path)


def mask(path: Path) -> None:
    m = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, PHONE_W - 1, PHONE_H - 1), RADIUS, fill=255)
    m.save(path)


def caption(path: Path, lines: list[str]) -> None:
    img = Image.new("RGBA", (760, 400), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(BOLD, 76)
    y = 0
    for line in lines:
        draw.text((0, y), line, font=font, fill=INK)
        y += 96
    img.save(path)


def cut(raw: Path, parts: list[tuple[float, float]], hold: float, out: Path, crf: int) -> float:
    """Plays [parts] of [raw] back to back at 30 fps, after its first frame held for [hold] s; returns the length."""
    trims = "".join(f"[0:v]trim={a}:{b},setpts=PTS-STARTPTS,fps=30[s{i}];" for i, (a, b) in enumerate(parts))
    if hold > 0:
        trims = trims.replace("[s0];", f",tpad=start_duration={hold}:start_mode=clone[s0];", 1)
    joins = "".join(f"[s{i}]" for i in range(len(parts)))
    subprocess.run([FF, "-y", "-v", "error", "-i", str(raw), "-filter_complex",
                    f"{trims}{joins}concat=n={len(parts)}:v=1:a=0,format=yuv420p[v]", "-map", "[v]",
                    "-c:v", "libx264", "-crf", f"{crf}", "-preset", "slow", "-movflags", "+faststart", str(out)],
                   check=True)
    return hold + sum(b - a for a, b in parts)


def main() -> None:
    raw, segments, hold, sw = Path(sys.argv[1]), sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
    parts = [tuple(map(float, s.split("-"))) for s in segments.split(",")]
    work = HERE / "raw" / "frame-parts"
    work.mkdir(parents=True, exist_ok=True)
    background(work / "bg.png")
    mask(work / "mask.png")
    caption(work / "c1.png", ["Type the way", "you text."])
    caption(work / "c2.png", ["Tap once.", "Send it the way", "you mean it."])

    # 1. The capture itself (re-encoded to H.264 only so every player opens it; near-lossless)
    cut(raw, parts, 0, HERE / "poc-tone-capture.mp4", crf=14)
    # 2. The cut the preview plays
    edit = work / "edit.mp4"
    length = cut(raw, parts, hold, edit, crf=12)

    # 3. The 16:9 preview
    raw, start = edit, 0.0
    graph = (
        f"[1:v]fps=30,scale={PHONE_W}:{PHONE_H}:flags=lanczos,format=rgba[ph];"
        f"[2:v]format=gray,scale={PHONE_W}:{PHONE_H}[m];"
        f"[ph][m]alphamerge[phone];"
        f"[0:v][phone]overlay={PHONE_X}:{PHONE_Y}[base];"
        f"[3:v]format=rgba,fade=t=in:st=0.2:d=0.4:alpha=1,fade=t=out:st={sw - 0.35:.2f}:d=0.35:alpha=1[c1];"
        f"[4:v]format=rgba,fade=t=in:st={sw:.2f}:d=0.4:alpha=1[c2];"
        f"[base][c1]overlay=1040:390:enable='lt(t,{sw:.2f})'[b1];"
        f"[b1][c2]overlay=1040:350:enable='gte(t,{sw:.2f})',format=yuv420p[out]"
    )
    subprocess.run([FF, "-y", "-v", "error",
                    "-loop", "1", "-t", f"{length}", "-i", str(work / "bg.png"),
                    "-ss", f"{start}", "-t", f"{length}", "-i", str(raw),
                    "-loop", "1", "-t", f"{length}", "-i", str(work / "mask.png"),
                    "-loop", "1", "-t", f"{length}", "-i", str(work / "c1.png"),
                    "-loop", "1", "-t", f"{length}", "-i", str(work / "c2.png"),
                    "-filter_complex", graph, "-map", "[out]", "-r", "30",
                    "-c:v", "libx264", "-crf", "16", "-preset", "slow", "-movflags", "+faststart",
                    str(HERE / "poc-tone-play-preview.mp4")], check=True)
    print(HERE / "poc-tone-capture.mp4")
    print(HERE / "poc-tone-play-preview.mp4")


if __name__ == "__main__":
    main()
