import { useMemo } from 'react';
import { motion } from 'motion/react';
import { RoughGenerator } from 'roughjs/bin/generator';
import type { Options, PathInfo } from 'roughjs/bin/core';
import { kineticPalette, stableSeed, type KineticAccent } from './palette';
import { useKineticMotion } from './motion';

type MarkProps = {
  seedKey?: string;
  size?: number;
  accent?: KineticAccent;
  className?: string;
};

function options(seedKey: string): Options {
  return { seed: stableSeed(seedKey), roughness: 0.85, bowing: 0.7, strokeWidth: 2, disableMultiStroke: true };
}

function Paths({ paths }: { paths: PathInfo[] }) {
  return <>{paths.map((path, index) => <path key={index} d={path.d} fill="none"
    stroke="currentColor" strokeWidth={path.strokeWidth} strokeLinecap="round" vectorEffect="non-scaling-stroke" />)}</>;
}

export function RoughArrow({ seedKey = 'freelax-arrow', size = 72, accent = 'ink', className = '',
  orientation = 'right', variant = 'curve' }: MarkProps & {
  orientation?: 'right' | 'up-right' | 'down-right'; variant?: 'curve' | 'straight';
}) {
  const paths = useMemo(() => {
    const generator = new RoughGenerator();
    const shaft = variant === 'curve'
      ? generator.path('M8 32 C28 48, 55 39, 86 25', options(seedKey + ':shaft'))
      : generator.line(8, 30, 86, 30, options(seedKey + ':shaft'));
    const endY = variant === 'curve' ? 25 : 30;
    const head = generator.linearPath([[69, endY - 9], [86, endY], [75, endY + 14]], options(seedKey + ':head'));
    return [...generator.toPaths(shaft), ...generator.toPaths(head)];
  }, [seedKey, variant]);
  const angle = orientation === 'up-right' ? -25 : orientation === 'down-right' ? 25 : 0;
  return <svg className={'ku-rough ' + className} viewBox="0 0 100 60" width={size} height={size * 0.6}
    style={{ color: kineticPalette[accent] }} aria-hidden="true" focusable="false">
    <g transform={`rotate(${angle} 50 30)`}><Paths paths={paths} /></g>
  </svg>;
}

export function RoughBurst({ seedKey = 'freelax-burst', size = 48, accent = 'acid', className = '',
  variant = 'rays' }: MarkProps & { variant?: 'rays' | 'burst' }) {
  const paths = useMemo(() => {
    const generator = new RoughGenerator();
    const angles = variant === 'rays' ? [-150, -105, -60] : [-150, -105, -60, -15, 30, 75, 120, 165];
    return angles.flatMap((angle, index) => {
      const radians = angle * Math.PI / 180;
      const inner = 12;
      const outer = index % 2 ? 26 : 29;
      return generator.toPaths(generator.line(32 + Math.cos(radians) * inner, 32 + Math.sin(radians) * inner,
        32 + Math.cos(radians) * outer, 32 + Math.sin(radians) * outer, options(`${seedKey}:${index}`)));
    });
  }, [seedKey, variant]);
  return <svg className={'ku-rough ' + className} viewBox="0 0 64 64" width={size} height={size}
    style={{ color: kineticPalette[accent] }} aria-hidden="true" focusable="false"><Paths paths={paths} /></svg>;
}

export function RoughUnderline({ seedKey = 'freelax-underline', size = 160, accent = 'ink', className = '',
  variant = 'single', active = true, motion: allowMotion = true }: MarkProps & {
  variant?: 'single' | 'double'; active?: boolean; motion?: boolean;
}) {
  const { enabled, transition } = useKineticMotion(allowMotion);
  const paths = useMemo(() => {
    const generator = new RoughGenerator();
    const main = generator.path('M4 10 Q70 3, 155 8', options(seedKey + ':main'));
    const second = variant === 'double' ? generator.toPaths(generator.path('M12 14 Q75 8, 146 12', options(seedKey + ':second'))) : [];
    return [...generator.toPaths(main), ...second];
  }, [seedKey, variant]);
  return <svg className={'ku-rough ' + className} viewBox="0 0 160 20" width={size} height={size / 8}
    style={{ color: kineticPalette[accent] }} aria-hidden="true" focusable="false">
    <motion.g className="ku-moving-underline" initial={false} animate={{ scaleX: enabled && !active ? 0.65 : 1 }}
      style={{ originX: 0 }} transition={transition}><Paths paths={paths} /></motion.g>
  </svg>;
}
