import React from 'react';
import {Composition} from 'remotion';
import {FPS, H, W} from './theme';
import {TOTAL, XLaunch} from './XLaunch';

export const RemotionRoot: React.FC = () => (
	<Composition id="XLaunch" component={XLaunch} durationInFrames={TOTAL} fps={FPS} width={W} height={H} />
);
