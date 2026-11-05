import React from 'react';
import {AbsoluteFill, Easing, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {Kinetic} from '../components/Kinetic';
import {Burst, Star, Swirl} from '../components/Sparkles';
import {C, display} from '../theme';

/** Unicorn's app icon: the white rounded square with the line-drawn unicorn. */
const AppIcon: React.FC<{size: number; shineAt?: number}> = ({size, shineAt}) => {
	const f = useCurrentFrame();
	const shine = shineAt === undefined ? -1 : interpolate(f, [shineAt, shineAt + 18], [-0.6, 1.6], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<div
			style={{
				width: size,
				height: size,
				borderRadius: size * 0.23,
				overflow: 'hidden',
				position: 'relative',
				background: '#fff',
				boxShadow: `0 0 ${size * 0.35}px rgba(190,140,255,0.85), 0 ${size * 0.08}px ${size * 0.25}px rgba(0,0,0,0.45)`,
			}}
		>
			<Img src={staticFile('brand/icon.png')} style={{width: size, height: size}} />
			{shine > -0.6 && shine < 1.6 ? (
				<div
					style={{
						position: 'absolute',
						inset: 0,
						background: `linear-gradient(115deg, transparent ${shine * 100 - 18}%, rgba(255,255,255,0.0) ${shine * 100 - 10}%, rgba(255,246,214,0.95) ${shine * 100}%, rgba(255,255,255,0) ${shine * 100 + 10}%, transparent ${shine * 100 + 18}%)`,
						mixBlendMode: 'screen',
					}}
				/>
			) : null}
		</div>
	);
};

/** 0 to 3.8 s: glitter spirals in and becomes the app icon, then the camera dives into it. */
export const Intro: React.FC<{dur: number}> = ({dur}) => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pop = spring({frame: f - 42, fps, config: {damping: 9, stiffness: 140, mass: 0.7}});
	const dive = interpolate(f, [dur - 12, dur], [1, 9], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.in(Easing.cubic)});
	const flash = interpolate(f, [dur - 5, dur], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill>
			<Swirl x={540} y={860} until={48} count={110} />
			<div
				style={{
					position: 'absolute',
					left: 540 - 170,
					top: 860 - 170,
					transform: `scale(${pop * dive}) rotate(${(1 - pop) * -25}deg)`,
					opacity: f < 42 ? 0 : 1,
				}}
			>
				<AppIcon size={340} shineAt={58} />
			</div>
			<Burst at={44} x={540} y={860} count={90} seed="intro" power={1.2} />
			<Kinetic text={'Meet *Unicorn*'} at={62} y={1250} size={104} dur={dur - 8} />
			<AbsoluteFill style={{background: '#fff', opacity: flash}} />
		</AbsoluteFill>
	);
};

const AI_APPS: Array<{file: string; name: string}> = [
	{file: 'ai/chatgpt.png', name: 'ChatGPT'},
	{file: 'ai/claude.png', name: 'Claude'},
	{file: 'ai/perplexity.png', name: 'Perplexity'},
	{file: 'ai/google.png', name: 'Google AI'},
	{file: 'ai/grok.png', name: 'Grok'},
];

/**
 * The five AI apps fly in, one per beat, then pour into Unicorn. Their icons are trademarks of their makers; this
 * layer is the one to swap for plain name chips if any maker objects (docs/plan/demo-video-x.md).
 */
export const AiIcons: React.FC<{dur: number; beat: number}> = ({dur, beat}) => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const gather = 4.6 * beat;
	const pull = interpolate(f, [gather, gather + 10], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.in(Easing.cubic)});
	const uni = spring({frame: f - gather - 8, fps, config: {damping: 10, stiffness: 160}});
	return (
		<AbsoluteFill>
			{AI_APPS.map((app, i) => {
				const at = i * beat;
				const s = spring({frame: f - at, fps, config: {damping: 11, stiffness: 170, mass: 0.7}});
				// A gentle arc across the middle of the frame
				const tx = 140 + i * 200;
				const ty = 960 + (i - 2) * (i - 2) * 30;
				// Each one rises into its own place, so they never cross on the way in
				const fromX = tx;
				const x = tx;
				const y = interpolate(s, [0, 1], [ty + 760, ty]);
				const gx = x + (540 - x) * pull;
				const gy = y + (1060 - y) * pull;
				const size = 160 * (1 - pull * 0.7);
				if (f < at) return null;
				return (
					<React.Fragment key={app.name}>
						<Star x={gx + (fromX - tx)} y={gy + 110} size={26} color={C.gold} opacity={(1 - s) * 0.9} rotate={f * 9} />
						<div style={{position: 'absolute', left: gx - size / 2, top: gy - size / 2, width: size, height: size, opacity: 1 - pull * 0.9}}>
							<Img
								src={staticFile(app.file)}
								style={{
									width: size,
									height: size,
									borderRadius: size * 0.24,
									boxShadow: `0 0 40px rgba(255,255,255,0.35), 0 12px 30px rgba(0,0,0,0.45)`,
								}}
							/>
							<div
								style={{
									position: 'absolute',
									top: size + 14,
									left: -60,
									right: -60,
									textAlign: 'center',
									color: '#fff',
									fontFamily: display,
									fontWeight: 700,
									fontSize: 30,
									opacity: 1 - pull,
									textShadow: '0 2px 12px rgba(0,0,0,0.6)',
								}}
							>
								{app.name}
							</div>
						</div>
						<Burst at={at + 4} x={tx} y={ty} count={18} seed={`ai-${i}`} power={0.5} />
					</React.Fragment>
				);
			})}
			<div style={{position: 'absolute', left: 540 - 130, top: 1060 - 130, transform: `scale(${uni})`, opacity: f > gather + 8 ? 1 : 0}}>
				<AppIcon size={260} shineAt={Math.round(gather + 16)} />
			</div>
			<Burst at={Math.round(gather + 9)} x={540} y={1060} count={90} seed="ai-gather" power={1.1} />
			<Kinetic text={'ChatGPT. Claude. Perplexity.\nGoogle AI. Grok.'} at={0} stagger={Math.round(beat * 0.8)} size={70} dur={Math.round(gather + 6)} />
			{f >= gather + 6 ? <Kinetic text={'All your AI prompts.\n*One place.*'} at={Math.round(gather + 8)} size={86} dur={dur} /> : null}
		</AbsoluteFill>
	);
};

/** On the music's dip: the bridge from saving prompts to writing. */
export const TitleCard: React.FC<{dur: number}> = ({dur}) => (
	<AbsoluteFill>
		<Kinetic text={'And AI that\n*writes* for you'} at={2} y={900} size={118} stagger={4} dur={dur} />
	</AbsoluteFill>
);

/** The end card on the music's return: icon, name, the X tagline and where to get it. */
export const EndCard: React.FC<{dur: number}> = ({dur}) => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pop = spring({frame: f, fps, config: {damping: 9, stiffness: 150, mass: 0.7}});
	const pill = spring({frame: f - 26, fps, config: {damping: 12, stiffness: 160}});
	const out = interpolate(f, [dur - 10, dur], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{opacity: out}}>
			<div style={{position: 'absolute', left: 540 - 150, top: 560, transform: `scale(${pop})`}}>
				<AppIcon size={300} shineAt={20} />
			</div>
			<Burst at={1} x={540} y={710} count={110} seed="end" power={1.4} />
			<Kinetic text="Unicorn" at={8} y={1010} size={150} stagger={0} />
			<Kinetic text={'The *social AI* keyboard'} at={14} y={1170} size={74} />
			<div
				style={{
					position: 'absolute',
					left: 540 - 250,
					top: 1290,
					width: 500,
					height: 104,
					borderRadius: 52,
					border: `4px solid ${C.gold}`,
					background: 'rgba(242,193,78,0.12)',
					boxShadow: `0 0 36px rgba(242,193,78,0.55)`,
					color: '#fff',
					fontFamily: display,
					fontWeight: 800,
					fontSize: 46,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
					transform: `scale(${pill})`,
				}}
			>
				Free on Android
			</div>
		</AbsoluteFill>
	);
};
