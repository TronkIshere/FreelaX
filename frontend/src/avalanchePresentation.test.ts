import { describe, expect, it } from 'vitest';
import { actionText, chainStatusText, stateText } from './avalanchePresentation';

describe('Avalanche presentation text (Vietnamese UI)', () => {
  it('shows every escrow status read from the chain in Vietnamese, never the raw enum', () => {
    for (const status of ['NONE', 'FUNDED', 'SUBMITTED', 'REVISION', 'DISPUTED', 'RELEASED', 'REFUNDED']) {
      expect(chainStatusText(status)).not.toBe(status);
      expect(chainStatusText(status)).toMatch(/[À-ỹ]/);
    }
    expect(chainStatusText('DISPUTED')).toBe('Đang tranh chấp');
  });

  it('shows an unknown chain status as received and an absent one as a dash, never hiding it', () => {
    expect(chainStatusText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(chainStatusText(null)).toBe('—');
    expect(chainStatusText(undefined)).toBe('—');
  });

  it('keeps the unknown-transaction guidance natural and the action labels in Vietnamese', () => {
    expect(stateText('UNKNOWN').detail).toContain('vui lòng không gửi lại');
    expect(actionText('release')).toBe('Giải ngân');
    expect(actionText('mutual-settle')).toContain('Tất toán');
  });
});
