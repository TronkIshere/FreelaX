import { createContext, useContext, useState, useSyncExternalStore, type FocusEvent } from 'react';
import type { Transition, Variants } from 'motion/react';
import { kineticPalette } from './palette';

export const kineticMotion = {
  duration: 0.16,
  ease: [0.22, 1, 0.36, 1] as [number, number, number, number],
  arrow: 4,
  ledger: 3,
  tilt: 1,
  lift: 1,
  shadow: 6,
  raisedShadow: 8,
} as const;

export const KineticMotionContext = createContext(true);

function preference() {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
}

function subscribePreference(notify: () => void) {
  const query = preference();
  query?.addEventListener('change', notify);
  return () => query?.removeEventListener('change', notify);
}

// Subscribe directly: Motion 14's useReducedMotion captures only the mount preference.
// Keep mounted primitives static immediately when the OS preference changes, too.
export function useKineticMotion(motion = true) {
  const inherited = useContext(KineticMotionContext);
  const reduced = useSyncExternalStore(subscribePreference, () => preference()?.matches ?? true, () => true);
  const enabled = motion && inherited && !reduced;
  const transition: Transition = { type: 'tween', duration: enabled ? kineticMotion.duration : 0, ease: kineticMotion.ease };
  return { enabled, transition };
}

type Effect = 'card' | 'compactCard' | 'ledger' | 'arrow' | 'thumbnail' | 'sticker' | 'underline';
const shadow = (offset: number) => `${offset}px ${offset}px 0px ${kineticPalette.ink}`;
const effects = {
  card: { rest: { x: 0, y: 0, boxShadow: shadow(kineticMotion.shadow) },
    active: { x: -kineticMotion.lift, y: -kineticMotion.lift, boxShadow: shadow(kineticMotion.raisedShadow) } },
  compactCard: { rest: { x: 0, y: 0, boxShadow: shadow(3) }, active: { x: -1, y: -1, boxShadow: shadow(4) } },
  ledger: { rest: { x: 0 }, active: { x: kineticMotion.ledger } },
  arrow: { rest: { x: 0 }, active: { x: kineticMotion.arrow } },
  thumbnail: { rest: { rotate: 0, y: 0 }, active: { rotate: kineticMotion.tilt, y: -1 } },
  sticker: { rest: { rotate: 0 }, active: { rotate: kineticMotion.tilt } },
  underline: { rest: { scaleX: 0.65 }, active: { scaleX: 1 } },
} as const;

export function kineticVariants(effect: Effect, enabled: boolean): Variants {
  const { rest, active } = effects[effect];
  return { rest, active: enabled ? active : rest };
}

// Keyboard focus within a document/row gets the same restrained feedback as hover.
export function useKineticFocus() {
  const [focused, setFocused] = useState(false);
  return {
    focused,
    onFocusCapture: () => setFocused(true),
    onBlurCapture: (event: FocusEvent<HTMLElement>) => {
      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false);
    },
  };
}
