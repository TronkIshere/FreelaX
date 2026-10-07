import { motion } from 'motion/react';
import type { ReactNode } from 'react';
import { kineticVariants, useKineticMotion } from './motion';
import { kineticPalette, stableSeed, type KineticAccent, type KineticSurface } from './palette';

// An empty overlay: it cannot wrap text, inputs, or amounts in a textured surface.
export function PrintTexture({ className = '' }: { className?: string }) {
  return <span className={'ku-print-texture ' + className} aria-hidden="true" />;
}

export function CutPaperShape({ variant = 'rectangle', accent = 'cobalt', size = 'md', rotation = 0,
  texture = false, className = '' }: {
  variant?: 'rectangle' | 'notch' | 'fold' | 'strip'; accent?: KineticAccent;
  size?: 'sm' | 'md' | 'lg'; rotation?: -6 | -3 | 0 | 3 | 6; texture?: boolean; className?: string;
}) {
  return <span className={`ku-paper ku-paper--${variant} ku-paper--${size} ${className}`}
    style={{ backgroundColor: kineticPalette[accent], transform: `rotate(${rotation}deg)` }} aria-hidden="true">
    {texture && <PrintTexture />}
  </span>;
}

type ThumbnailVariant = 'slash' | 'steps' | 'split' | 'fold';
const thumbnailVariants: ThumbnailVariant[] = ['slash', 'steps', 'split', 'fold'];
const thumbnailAccents: KineticSurface[] = ['cobalt', 'vermilion', 'acid', 'mint'];
const thumbnailPaths = {
  // One paper-band / folded-corner grammar, with controlled changes in angle and crop.
  slash: ['M-5 42L69 0L76 16L4 60Z', 'M32 47L59 25L73 63Z'],
  steps: ['M-5 48L67 5L76 24L3 65Z', 'M-3 2L27 2L9 29Z'],
  split: ['M-6 31L67 -4L75 14L2 50Z', 'M38 42L67 26L68 66Z'],
  fold: ['M-5 39L68 0L75 20L3 60Z', 'M-3 7L30 3L12 32Z'],
};

export function KineticThumbnail({ identity, variant, texture = false, className = '' }: {
  identity: string; variant?: ThumbnailVariant; texture?: boolean; className?: string;
}) {
  // Identity stays stable when a server list is reordered. It is not a status or illustration of the job.
  const index = variant ? thumbnailVariants.indexOf(variant) : stableSeed(identity) % thumbnailVariants.length;
  const selected = thumbnailVariants[index];
  const paths = thumbnailPaths[selected];
  return <span className={'ku-thumbnail ' + className} data-variant={selected} aria-hidden="true">
    <svg viewBox="0 0 64 64" focusable="false">
      <path fill={kineticPalette[thumbnailAccents[index]]} d="M1 2L61 0L64 61L0 64Z" />
      <path fill={kineticPalette.cream} d={paths[0]} />
      <path fill={kineticPalette.ink} d={paths[1]} />
    </svg>
    {texture && <PrintTexture />}
  </span>;
}

export function TapeSticker({ children, variant = 'acid', rotation = -2, motion: allowMotion = true, className = '' }: {
  children: ReactNode; variant?: 'acid' | 'cream' | 'cobalt' | 'vermilion'; rotation?: -2 | -1 | 0 | 1 | 2;
  motion?: boolean; className?: string;
}) {
  const { enabled, transition } = useKineticMotion(allowMotion);
  return <span className={'ku-tape ' + className} style={{ transform: `rotate(${rotation}deg)` }}>
    <motion.span className={`ku-label ku-surface--${variant}`} data-motion={enabled ? 'on' : 'off'}
      initial={false} variants={kineticVariants('sticker', enabled)} whileHover={enabled ? 'active' : undefined}
      transition={transition}>{children}</motion.span>
  </span>;
}

export function KineticLabel({ children, variant = 'acid', className = '' }: {
  children: ReactNode; variant?: KineticSurface; className?: string;
}) {
  return <span className={`ku-label ku-surface--${variant} ${className}`}>{children}</span>;
}
