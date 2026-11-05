import {loadFont} from '@remotion/google-fonts/BricolageGrotesque';

// The website's display face, so the video and the site read as one brand
export const {fontFamily: display} = loadFont('normal', {weights: ['500', '700', '800'], subsets: ['latin']});

export const C = {
	night: '#1C1035',
	night2: '#0B0618',
	violet: '#7430A4',
	deep: '#561F7E',
	gold: '#F2C14E',
	lilac: '#F3EEFA',
	pink: '#F2A7C3',
	mint: '#8FE3CC',
	blue: '#3B5BDB',
};

export const FPS = 30;
export const W = 1080;
export const H = 1920;

/** "Take this Higher" (Mixkit) runs at 124 BPM; its bars start at 1.883 s. */
export const BEAT_S = 60 / 124;
export const bar = (n: number) => Math.round((1.883 + n * 4 * BEAT_S) * FPS);
export const sec = (s: number) => Math.round(s * FPS);
