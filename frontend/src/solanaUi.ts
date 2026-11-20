/**
 * R13 (owner decision 2026-11-12): the Solana gateway is not in master (branch `solana`) and the marketplace Solana calls fail
 * closed. Solana controls are hidden unless VITE_SOLANA_ENABLED is exactly "true". Records of older Solana contracts keep
 * their history labels.
 */
export const solanaUiEnabled = () => import.meta.env.VITE_SOLANA_ENABLED === 'true';
