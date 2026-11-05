"""Demo video (docs/plan/demo-video-x.md): cuts the raw emulator takes into the exact-length clips the edit plays.

    python -I tools/demo/cut.py [name ...]

Reads video/cuts.json and writes video/public/clips/<name>.mp4: 1080x2400, 30 fps, H.264. Each clip plays its
segments [from, to, speed] back to back, then is cut, or holds its last frame, to exactly `frames` frames. With
names, only those clips are cut.
"""
import json
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
VIDEO = ROOT / "video"
FF = imageio_ffmpeg.get_ffmpeg_exe()


def cut(name: str, spec: dict) -> None:
    src = VIDEO / "takes" / f"{spec['take']}.webm"
    out = VIDEO / "public" / "clips" / f"{name}.mp4"
    out.parent.mkdir(parents=True, exist_ok=True)
    parts = []
    for i, (a, b, speed) in enumerate(spec["segs"]):
        parts.append(f"[0:v]trim=start={a}:end={b},setpts=(PTS-STARTPTS)/{speed},fps=30[s{i}]")
    n = len(spec["segs"])
    frames = spec["frames"]
    graph = ";".join(parts) + ";" + "".join(f"[s{i}]" for i in range(n)) + \
        f"concat=n={n}:v=1:a=0,tpad=stop_mode=clone:stop_duration=5,trim=end_frame={frames},format=yuv420p[v]"
    subprocess.run([FF, "-y", "-v", "error", "-i", str(src), "-filter_complex", graph, "-map", "[v]",
                    "-c:v", "libx264", "-crf", "15", "-preset", "fast", "-r", "30", "-movflags", "+faststart",
                    str(out)], check=True)
    played = sum((b - a) / s for a, b, s in spec["segs"]) * 30
    note = "" if played >= frames else f"  (holds the last frame for {frames - played:.0f})"
    print(f"{name:10} {frames:4} frames from {spec['take']}{note}")


def main() -> None:
    cuts = {k: v for k, v in json.loads((VIDEO / "cuts.json").read_text(encoding="utf-8")).items() if not k.startswith("_")}
    for name in sys.argv[1:] or cuts:
        cut(name, cuts[name])


if __name__ == "__main__":
    main()
