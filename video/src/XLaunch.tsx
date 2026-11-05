import React from 'react';
import {AbsoluteFill, Audio, interpolate, Sequence, staticFile} from 'remotion';
import {Background, CaptionShade, Flash} from './components/Background';
import {Kinetic} from './components/Kinetic';
import {Box, PhoneShot, Tap, Zoom} from './components/Phone';
import {AmbientSparkles, Burst, GlitterWipe} from './components/Sparkles';
import {AiIcons, EndCard, Intro, TitleCard} from './scenes/Scenes';
import {BEAT_S, bar, FPS} from './theme';

/**
 * Unicorn's launch video for X (docs/plan/demo-video-x.md): 1080x1920, about 57 s, cut to "Take this Higher"
 * (Mixkit, 124 BPM). Every phone shot is a real emulator take (tools/demo/), cut into clips by tools/demo/cut.py.
 */
const BEAT = BEAT_S * FPS;

type Shot = {
	clip: string;
	from: number;
	to: number;
	caption: string;
	zooms?: Zoom[];
	boxes?: Box[];
	taps?: Tap[];
	burst?: {f: number; x: number; y: number};
};

// Act 1 starts on the intro; the drop (bar 14) starts the writing tools; the breakdown (bar 22) the social act
const SHOTS: Shot[] = [
	{
		clip: 'home', from: bar(1), to: bar(2), caption: 'One tap',
		zooms: [{f: 0, s: 2.6, x: 413, y: 953, d: 0}, {f: 4, s: 1, x: 540, y: 1200, d: 12}],
		taps: [{f: 27, x: 413, y: 953}],
	},
	{
		clip: 'setup', from: bar(2), to: bar(3), caption: 'Set up in *seconds*',
		zooms: [{f: 0, s: 1.25, x: 540, y: 900, d: 0}, {f: 22, s: 1.6, x: 540, y: 670, d: 7}],
		boxes: [{from: 25, to: 58, x: 30, y: 600, w: 1020, h: 130}],
		taps: [{f: 30, x: 890, y: 666}],
	},
	{
		clip: 'hatch', from: bar(3), to: bar(5), caption: 'Your own Unicorn\n*hatches*',
		zooms: [{f: 0, s: 1, d: 0}, {f: 40, s: 1.18, x: 540, y: 1000, d: 60}],
		burst: {f: 52, x: 540, y: 860},
	},
	{
		clip: 'google', from: bar(7), to: bar(9), caption: 'Ask any AI…',
		zooms: [{f: 0, s: 1.75, x: 540, y: 330, d: 0}, {f: 84, s: 1.15, x: 540, y: 600, d: 8}],
		boxes: [{from: 2, to: 82, x: 110, y: 150, w: 950, h: 110}],
		taps: [{f: 82, x: 1020, y: 2190}],
	},
	{
		clip: 'library', from: bar(9), to: bar(9) + 73, caption: '…it\'s *saved*\nin Unicorn',
		zooms: [{f: 0, s: 1, x: 540, y: 1200, d: 0}, {f: 16, s: 1.9, x: 540, y: 595, d: 8}],
		boxes: [{from: 20, to: 73, x: 30, y: 545, w: 1020, h: 105}],
		taps: [{f: 4, x: 540, y: 2270}],
	},
	{
		clip: 'search', from: bar(9) + 73, to: bar(11) + 30, caption: 'Find it *by meaning*',
		zooms: [{f: 0, s: 1.7, x: 540, y: 480, d: 0}],
		boxes: [{from: 0, to: 33, x: 30, y: 345, w: 1020, h: 120}, {from: 36, to: 73, x: 30, y: 545, w: 1020, h: 105}],
	},
	{
		clip: 'recall', from: bar(11) + 30, to: bar(13), caption: 'Asked before?\nUnicorn *remembers*',
		zooms: [{f: 0, s: 1.35, x: 540, y: 900, d: 0}, {f: 18, s: 2.1, x: 430, y: 1375, d: 7}],
		boxes: [{from: 20, to: 86, x: 120, y: 1325, w: 640, h: 105}],
	},
	{
		clip: 'tone', from: bar(14), to: bar(14) + 87, caption: 'Fix the *tone*.\nOne tap.',
		zooms: [{f: 0, s: 1.6, x: 540, y: 1205, d: 0}, {f: 8, s: 1.9, x: 846, y: 1298, d: 5}, {f: 30, s: 1.45, x: 540, y: 1760, d: 6}, {f: 66, s: 1.35, x: 540, y: 1100, d: 6}],
		boxes: [{from: 9, to: 30, x: 760, y: 1258, w: 175, h: 82}, {from: 33, to: 66, x: 25, y: 1700, w: 1030, h: 120}],
		taps: [{f: 11, x: 846, y: 1298}, {f: 67, x: 540, y: 1760}, {f: 78, x: 990, y: 1210}],
	},
	{
		clip: 'reply', from: bar(14) + 87, to: bar(14) + 174, caption: 'Stuck on a reply?\n*/reply*',
		zooms: [{f: 0, s: 1.6, x: 430, y: 1079, d: 0}, {f: 22, s: 1.45, x: 540, y: 1430, d: 5}, {f: 54, s: 1.45, x: 540, y: 1990, d: 5}, {f: 77, s: 1.2, x: 540, y: 1100, d: 5}],
		boxes: [{from: 2, to: 21, x: 20, y: 1030, w: 860, h: 100}, {from: 34, to: 53, x: 20, y: 1440, w: 420, h: 85}, {from: 56, to: 76, x: 25, y: 1935, w: 1030, h: 115}],
		taps: [{f: 6, x: 430, y: 1079, long: true}, {f: 24, x: 175, y: 1378}, {f: 36, x: 233, y: 1482}, {f: 77, x: 540, y: 1990}],
	},
	{
		clip: 'email', from: bar(14) + 174, to: bar(14) + 247, caption: '*/email* writes it\nfor you',
		zooms: [{f: 0, s: 1.3, x: 540, y: 1450, d: 0}, {f: 33, s: 1.3, x: 540, y: 1700, d: 5}, {f: 54, s: 1.45, x: 540, y: 640, d: 6}],
		boxes: [{from: 56, to: 73, x: 15, y: 330, w: 1050, h: 620}],
	},
	{
		clip: 'grammar', from: bar(14) + 247, to: bar(14) + 291, caption: '*Grammar*, fixed',
		zooms: [{f: 0, s: 1.6, x: 540, y: 1210, d: 0}, {f: 21, s: 1.5, x: 540, y: 1760, d: 4}, {f: 34, s: 1.6, x: 540, y: 1210, d: 4}],
		boxes: [{from: 22, to: 34, x: 25, y: 1690, w: 1030, h: 140}, {from: 35, to: 44, x: 20, y: 1150, w: 900, h: 120}],
	},
	{
		clip: 'translate', from: bar(14) + 291, to: bar(14) + 335, caption: '*Translate* anything',
		zooms: [{f: 0, s: 1.6, x: 540, y: 1235, d: 0}, {f: 21, s: 1.5, x: 540, y: 1780, d: 4}, {f: 34, s: 1.6, x: 540, y: 1235, d: 4}],
		boxes: [{from: 22, to: 34, x: 25, y: 1790, w: 1030, h: 95}, {from: 35, to: 44, x: 20, y: 1180, w: 900, h: 110}],
	},
	{
		clip: 'glide', from: bar(14) + 335, to: bar(14) + 408, caption: '*Glide* to type',
		zooms: [{f: 0, s: 1.35, x: 540, y: 1700, d: 0}, {f: 58, s: 1.1, x: 540, y: 1500, d: 8}],
	},
	{
		clip: 'resize', from: bar(14) + 408, to: bar(14) + 437, caption: '*Resize* it',
		zooms: [{f: 0, s: 1.15, x: 540, y: 1950, d: 0}],
	},
	{
		clip: 'onehanded', from: bar(14) + 437, to: bar(22), caption: 'Go *one-handed*.\nIt learns your aim.',
		zooms: [{f: 0, s: 1.15, x: 540, y: 1950, d: 0}],
	},
	{
		clip: 'card', from: bar(22), to: bar(22) + 87, caption: 'Flex your\n*one-of-a-kind* Unicorn',
		zooms: [{f: 0, s: 1, x: 540, y: 1200, d: 0}, {f: 18, s: 1.22, x: 540, y: 1098, d: 14}, {f: 64, s: 1.6, x: 540, y: 1949, d: 6}],
		boxes: [{from: 66, to: 87, x: 30, y: 1880, w: 1020, h: 130}],
		taps: [{f: 71, x: 540, y: 1949}],
		burst: {f: 12, x: 540, y: 1050},
	},
	{
		clip: 'hunches', from: bar(22) + 87, to: bar(22) + 174, caption: 'Playful *hunches*\nabout your town',
		zooms: [{f: 0, s: 1, x: 540, y: 1000, d: 0}, {f: 8, s: 1.7, x: 540, y: 560, d: 8}],
		boxes: [{from: 10, to: 87, x: 25, y: 360, w: 1030, h: 380}],
	},
	{
		clip: 'trends', from: bar(22) + 174, to: bar(26), caption: 'Share anonymously.\nSee what your *town* asks AI.',
		zooms: [{f: 0, s: 1.5, x: 540, y: 1400, d: 0}],
		boxes: [{from: 6, to: 58, x: 25, y: 1030, w: 1030, h: 800}],
	},
];

export const TOTAL = bar(26) + 134;

/** Act changes get a glitter wipe; every other cut a short flash on the beat. */
const WIPES = [bar(5), bar(13), bar(14), bar(22), bar(26)];

export const XLaunch: React.FC = () => {
	return (
		<AbsoluteFill>
			<Background />
			<AmbientSparkles count={40} />
			<Sequence durationInFrames={bar(1)}>
				<Intro dur={bar(1)} />
			</Sequence>
			{SHOTS.map((s) => (
				<Sequence key={s.clip} from={s.from} durationInFrames={s.to - s.from}>
					<PhoneShot clip={s.clip} zooms={s.zooms} boxes={s.boxes} taps={s.taps} />
					{s.burst ? <Burst at={s.burst.f} x={s.burst.x} y={s.burst.y} count={80} seed={`b-${s.clip}`} /> : null}
					<CaptionShade />
					<Kinetic text={s.caption} at={1} dur={s.to - s.from} size={s.caption.length > 34 ? 64 : 76} y={s.caption.includes('\n') ? 215 : 190} />
				</Sequence>
			))}
			<Sequence from={bar(5)} durationInFrames={bar(7) - bar(5)}>
				<AiIcons dur={bar(7) - bar(5)} beat={BEAT} />
			</Sequence>
			<Sequence from={bar(13)} durationInFrames={bar(14) - bar(13)}>
				<TitleCard dur={bar(14) - bar(13)} />
			</Sequence>
			<Sequence from={bar(26)} durationInFrames={TOTAL - bar(26)}>
				<EndCard dur={TOTAL - bar(26)} />
			</Sequence>
			<AmbientSparkles count={14} seed="front" opacity={0.6} />
			{SHOTS.filter((s) => !WIPES.includes(s.from)).map((s) => (
				<Flash key={`f-${s.clip}`} at={s.from} strength={0.32} />
			))}
			{WIPES.map((w) => (
				<GlitterWipe key={`w-${w}`} at={w - 6} dur={14} seed={`w-${w}`} />
			))}
			<Audio
				src={staticFile('music/take-this-higher.mp3')}
				volume={(f) => interpolate(f, [0, 6, TOTAL - 45, TOTAL], [0, 0.9, 0.9, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'})}
			/>
		</AbsoluteFill>
	);
};
