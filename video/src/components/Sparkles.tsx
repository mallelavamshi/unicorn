import React from 'react';
import {AbsoluteFill, random, useCurrentFrame} from 'remotion';
import {C, H, W} from '../theme';

const STAR = 'M0,-1 C0.12,-0.12 0.12,-0.12 1,0 C0.12,0.12 0.12,0.12 0,1 C-0.12,0.12 -0.12,0.12 -1,0 C-0.12,-0.12 -0.12,-0.12 0,-1 Z';

/** A four-point glitter star with a soft glow. */
export const Star: React.FC<{x: number; y: number; size: number; color: string; opacity: number; rotate?: number}> = ({
	x,
	y,
	size,
	color,
	opacity,
	rotate = 0,
}) => {
	if (opacity <= 0.01 || size <= 0.5) return null;
	return (
		<svg
			width={size * 2}
			height={size * 2}
			viewBox="-1.2 -1.2 2.4 2.4"
			style={{
				position: 'absolute',
				left: x - size,
				top: y - size,
				opacity,
				transform: `rotate(${rotate}deg)`,
				filter: `drop-shadow(0 0 ${size * 0.6}px ${color})`,
				overflow: 'visible',
			}}
		>
			<path d={STAR} fill={color} />
			<circle r={0.18} fill="#fff" />
		</svg>
	);
};

const palette = ['#FFFFFF', C.gold, '#F7D9FF', C.pink, '#CDB8FF'];

/** Slow-drifting, twinkling glitter over the whole frame. */
export const AmbientSparkles: React.FC<{count?: number; seed?: string; opacity?: number}> = ({
	count = 46,
	seed = 'ambient',
	opacity = 1,
}) => {
	const f = useCurrentFrame();
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			{new Array(count).fill(0).map((_, i) => {
				const r = (k: string) => random(`${seed}-${i}-${k}`);
				const speed = 0.25 + r('v') * 0.9;
				const y = (((r('y') * (H + 200) - f * speed) % (H + 200)) + H + 200) % (H + 200) - 100;
				const x = r('x') * W + Math.sin(f / 40 + i) * 12;
				const tw = Math.max(0, Math.sin(f * (0.06 + r('w') * 0.12) + r('p') * 6.28));
				return (
					<Star
						key={i}
						x={x}
						y={y}
						size={5 + r('s') * 16}
						color={palette[i % palette.length]}
						opacity={opacity * tw ** 3}
						rotate={f * (r('r') - 0.5) * 2}
					/>
				);
			})}
		</AbsoluteFill>
	);
};

/** A burst of glitter flying out from (x, y), starting at frame [at]. */
export const Burst: React.FC<{at: number; x: number; y: number; count?: number; seed?: string; power?: number}> = ({
	at,
	x,
	y,
	count = 70,
	seed = 'burst',
	power = 1,
}) => {
	const f = useCurrentFrame() - at;
	if (f < 0 || f > 60) return null;
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			{new Array(count).fill(0).map((_, i) => {
				const r = (k: string) => random(`${seed}-${i}-${k}`);
				const angle = r('a') * Math.PI * 2;
				const v = (10 + r('v') * 34) * power;
				// Fast out, slowing down (sum of a geometric series), with a little gravity
				const dist = (v * (1 - 0.88 ** f)) / 0.12;
				const life = 22 + r('l') * 30;
				const t = f / life;
				if (t > 1) return null;
				return (
					<Star
						key={i}
						x={x + Math.cos(angle) * dist}
						y={y + Math.sin(angle) * dist + 0.25 * f * f * 0.3}
						size={(6 + r('s') * 18) * (1 - t * 0.6)}
						color={palette[i % palette.length]}
						opacity={1 - t ** 2}
						rotate={f * 8 * (r('r') - 0.5)}
					/>
				);
			})}
		</AbsoluteFill>
	);
};

/** Glitter that spirals in from the edges and gathers at (x, y) by frame [until]. */
export const Swirl: React.FC<{x: number; y: number; until: number; count?: number; seed?: string}> = ({
	x,
	y,
	until,
	count = 90,
	seed = 'swirl',
}) => {
	const f = useCurrentFrame();
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			{new Array(count).fill(0).map((_, i) => {
				const r = (k: string) => random(`${seed}-${i}-${k}`);
				const start = r('t') * until * 0.5;
				const t = Math.min(1, Math.max(0, (f - start) / (until - start)));
				if (t >= 1) return null;
				const ease = 1 - (1 - t) ** 3;
				const radius = (700 + r('d') * 500) * (1 - ease);
				const angle = r('a') * Math.PI * 2 + ease * (3 + r('s') * 3);
				return (
					<Star
						key={i}
						x={x + Math.cos(angle) * radius}
						y={y + Math.sin(angle) * radius}
						size={4 + r('z') * 14}
						color={palette[i % palette.length]}
						opacity={Math.min(1, t * 4) * (0.6 + 0.4 * Math.sin(f / 3 + i))}
						rotate={f * 6}
					/>
				);
			})}
		</AbsoluteFill>
	);
};

/** A diagonal band of glitter sweeping across the frame (top left to bottom right): the act-change wipe. */
export const GlitterWipe: React.FC<{at: number; dur?: number; seed?: string}> = ({at, dur = 14, seed = 'wipe'}) => {
	const f = useCurrentFrame() - at;
	if (f < 0 || f > dur) return null;
	const p = f / dur;
	// The band is the line x + y = d, moving from the top-left corner past the bottom-right one
	const d = -300 + p * (W + H + 600);
	const pct = (d / (W + H)) * 100;
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			<AbsoluteFill
				style={{
					background: `linear-gradient(135deg, transparent ${pct - 9}%, rgba(255,236,255,0.55) ${pct}%, transparent ${pct + 9}%)`,
				}}
			/>
			{new Array(90).fill(0).map((_, i) => {
				const r = (k: string) => random(`${seed}-${i}-${k}`);
				const x = r('x') * W;
				const y = d - x + (r('j') - 0.5) * 300;
				return (
					<Star
						key={i}
						x={x}
						y={y}
						size={6 + r('s') * 20}
						color={palette[i % palette.length]}
						opacity={Math.sin(p * Math.PI)}
						rotate={f * 12}
					/>
				);
			})}
		</AbsoluteFill>
	);
};
