import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {C} from '../theme';

/** Night violet with slow aurora glows drifting behind everything. */
export const Background: React.FC = () => {
	const f = useCurrentFrame();
	const blob = (x: number, y: number, r: number, color: string, phase: number) => (
		<div
			style={{
				position: 'absolute',
				left: x + Math.sin(f / 70 + phase) * 80 - r,
				top: y + Math.cos(f / 90 + phase) * 90 - r,
				width: r * 2,
				height: r * 2,
				borderRadius: '50%',
				background: `radial-gradient(circle, ${color} 0%, transparent 65%)`,
				opacity: 0.55,
			}}
		/>
	);
	return (
		<AbsoluteFill style={{background: `radial-gradient(120% 80% at 50% 30%, #2A1250 0%, ${C.night} 45%, ${C.night2} 100%)`}}>
			{blob(180, 380, 620, '#7430A4', 0)}
			{blob(900, 1250, 700, '#3B2BB5', 2)}
			{blob(500, 1750, 560, '#B0408F', 4)}
		</AbsoluteFill>
	);
};

/** A dark wash at the top so captions stay readable over a punched-in phone. */
export const CaptionShade: React.FC = () => (
	<AbsoluteFill
		style={{
			background: 'linear-gradient(180deg, rgba(14,7,30,0.92) 0%, rgba(14,7,30,0.75) 16%, rgba(14,7,30,0) 30%)',
			pointerEvents: 'none',
		}}
	/>
);

/** A white flash that fades over a few frames: the cut on the beat. */
export const Flash: React.FC<{at: number; strength?: number}> = ({at, strength = 0.45}) => {
	const f = useCurrentFrame() - at;
	if (f < 0 || f > 5) return null;
	return <AbsoluteFill style={{background: '#fff', opacity: strength * (1 - f / 5)}} />;
};
