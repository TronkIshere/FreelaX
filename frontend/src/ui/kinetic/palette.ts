export const kineticPalette = {
  cream: '#FFF7E8',
  ink: '#17212B',
  vermilion: '#F15A3D',
  acid: '#F5D12F',
  mint: '#B8DFC4',
  cobalt: '#3567E8',
} as const;

export type KineticAccent = keyof typeof kineticPalette;
export type KineticSurface = Exclude<KineticAccent, 'ink'>;

// Rough.js requires a nonzero seed in the positive 31-bit range. No clock/random input.
export function stableSeed(identity: string): number {
  let hash = 2166136261;
  for (let i = 0; i < identity.length; i++) {
    hash = Math.imul(hash ^ identity.charCodeAt(i), 16777619);
  }
  return (hash & 0x7fffffff) || 1;
}
