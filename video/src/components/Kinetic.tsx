import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, display} from '../theme';
import {Star} from './Sparkles';

/**
 * Words that slam in one after another, with a springy overshoot and a soft glow. "\n" breaks the line and
 * *word* turns a word gold with a sparkle. [dur] fades it out over its last frames.
 */
export const Kinetic: React.FC<{
	text: string;
	at?: number;
	dur?: number;
	size?: number;
	y?: number;
	stagger?: number;
}> = ({text, at = 0, dur, size = 78, y = 230, stagger = 3}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const out = dur ? interpolate(frame, [dur - 5, dur], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}) : 1;
	let index = 0;
	const lines = text.split('\n').map((line) => line.split(' ').filter(Boolean));
	return (
		<div
			style={{
				position: 'absolute',
				left: 40,
				right: 40,
				top: y,
				transform: 'translateY(-50%)',
				textAlign: 'center',
				fontFamily: display,
				fontWeight: 800,
				fontSize: size,
				lineHeight: 1.08,
				letterSpacing: -1.5,
				opacity: out,
			}}
		>
			{lines.map((words, li) => (
				<div key={li} style={{whiteSpace: 'nowrap'}}>
					{words.map((raw, wi) => {
						const i = index++;
						const gold = raw.startsWith('*');
						const word = raw.replace(/\*/g, '');
						const local = frame - at - i * stagger;
						const s = spring({frame: local, fps, config: {damping: 11, stiffness: 190, mass: 0.6}});
						const opacity = interpolate(local, [0, 3], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
						return (
							<span
								key={wi}
								style={{
									display: 'inline-block',
									position: 'relative',
									margin: `0 ${size * 0.12}px`,
									opacity,
									transform: `translateY(${(1 - s) * 40}px) scale(${interpolate(s, [0, 1], [1.7, 1])})`,
									filter: `blur(${Math.max(0, (1 - s) * 10)}px)`,
									color: gold ? C.gold : '#FFFFFF',
									textShadow: gold
										? `0 0 28px rgba(242,193,78,0.75), 0 4px 18px rgba(0,0,0,0.45)`
										: `0 0 30px rgba(168,110,255,0.75), 0 4px 18px rgba(0,0,0,0.5)`,
								}}
							>
								{word}
								{gold && local > 4 ? (
									<Star
										x={size * (0.25 + word.length * 0.52)}
										y={size * 0.1}
										size={size * 0.22 * (0.8 + 0.4 * Math.sin(local / 3))}
										color={C.gold}
										opacity={interpolate(local, [4, 9], [0, 1], {extrapolateRight: 'clamp'})}
										rotate={local * 4}
									/>
								) : null}
							</span>
						);
					})}
				</div>
			))}
		</div>
	);
};
