import React from 'react';
import {AbsoluteFill, Easing, interpolate, OffthreadVideo, staticFile, useCurrentFrame} from 'remotion';
import {C} from '../theme';

/** The phone screen's place on the 1080x1920 canvas. Clips are 1080x2400 phone pixels. */
export const PHONE = {w: 660, h: (660 * 2400) / 1080, left: 210, top: 1920 - (660 * 2400) / 1080 - 66};
const K = PHONE.w / 1080;

/** From frame f, the camera moves (over d frames) to scale s around phone point (x, y). */
export type Zoom = {f: number; s: number; x?: number; y?: number; d?: number};
/** A glowing box around phone rectangle (x, y, w, h), shown from..to. */
export type Box = {from: number; to: number; x: number; y: number; w: number; h: number};
/** A tap ring at phone point (x, y); long rings hold like a long-press. */
export type Tap = {f: number; x: number; y: number; long?: boolean};

const ease = Easing.bezier(0.2, 0.85, 0.15, 1);

const camera = (zooms: Zoom[], frame: number) => {
	let s = 1;
	let x = 540;
	let y = 1200;
	for (const z of zooms) {
		const d = z.d ?? 7;
		const t = d <= 0 ? (frame >= z.f ? 1 : 0) : interpolate(frame, [z.f, z.f + d], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: ease});
		if (frame < z.f) break;
		s = s + (z.s - s) * t;
		x = x + ((z.x ?? x) - x) * t;
		y = y + ((z.y ?? y) - y) * t;
	}
	return {s, x, y};
};

export const PhoneShot: React.FC<{
	clip: string;
	zooms?: Zoom[];
	boxes?: Box[];
	taps?: Tap[];
}> = ({clip, zooms = [], boxes = [], taps = []}) => {
	const frame = useCurrentFrame();
	const {s, x, y} = camera(zooms, frame);
	// The focal point slides toward the middle of the frame as the camera punches in
	const px = PHONE.left + x * K;
	const py = PHONE.top + y * K;
	const pull = Math.min(1, Math.max(0, (s - 1) / 0.8)) * 0.85;
	const dx = (540 - px) * pull;
	const dy = (1120 - py) * pull;
	// Each shot arrives with a quick zoom-through
	const enter = interpolate(frame, [0, 6], [1, 0], {extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic)});
	const float = Math.sin(frame / 22) * 4;
	return (
		<AbsoluteFill>
			<div
				style={{
					position: 'absolute',
					left: PHONE.left,
					top: PHONE.top + float,
					width: PHONE.w,
					height: PHONE.h,
					borderRadius: 58,
					overflow: 'hidden',
					transformOrigin: `${x * K}px ${y * K}px`,
					transform: `translate(${dx}px, ${dy}px) scale(${s * (1 + enter * 0.14)})`,
					filter: enter > 0.02 ? `blur(${enter * 10}px)` : undefined,
					boxShadow: `0 0 0 3px rgba(255,255,255,0.10), 0 40px 90px rgba(0,0,0,0.55), 0 0 140px rgba(116,48,164,0.6)`,
					background: '#000',
				}}
			>
				<OffthreadVideo src={staticFile(`clips/${clip}.mp4`)} muted style={{width: PHONE.w, height: PHONE.h}} />
				{boxes.map((b, i) => {
					const local = frame - b.from;
					if (frame < b.from || frame > b.to) return null;
					const pop = interpolate(local, [0, 5], [1.25, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
					const fade = interpolate(frame, [b.from, b.from + 3, b.to - 3, b.to], [0, 1, 1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
					const border = 7 / s;
					return (
						<div
							key={i}
							style={{
								position: 'absolute',
								left: b.x * K,
								top: b.y * K,
								width: b.w * K,
								height: b.h * K,
								borderRadius: 22 / Math.sqrt(s),
								border: `${border}px solid ${C.gold}`,
								boxShadow: `0 0 ${24 / s}px ${C.gold}, inset 0 0 ${16 / s}px rgba(242,193,78,0.35)`,
								transform: `scale(${pop})`,
								opacity: fade,
							}}
						/>
					);
				})}
				{taps.map((t, i) => {
					const local = frame - t.f;
					const life = t.long ? 22 : 13;
					if (local < 0 || local > life) return null;
					const p = local / life;
					const r = (t.long ? 70 : 50 + 60 * p) * K;
					return (
						<div
							key={i}
							style={{
								position: 'absolute',
								left: t.x * K - r,
								top: t.y * K - r,
								width: r * 2,
								height: r * 2,
								borderRadius: '50%',
								border: `${5 / s}px solid rgba(255,255,255,${0.95 - p * 0.8})`,
								background: `rgba(255,255,255,${t.long ? 0.28 : 0.3 * (1 - p)})`,
								boxShadow: '0 0 20px rgba(255,255,255,0.6)',
							}}
						/>
					);
				})}
			</div>
		</AbsoluteFill>
	);
};
