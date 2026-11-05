import { describe, expect, it } from 'vitest';
import { actionText, chainStatusText, stateText, walletErrorText } from './avalanchePresentation';

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

  it('never shows the English text of a wallet error to the user', () => {
    const codes = ['NO_WALLET', 'USER_REJECTED', 'CONNECT_FAILED', 'NO_ACCOUNT', 'CHAIN_UNREADABLE', 'CHAIN_NOT_ALLOWED', 'ACCOUNT_NOT_LINKED', 'WRONG_CHAIN', 'CHAIN_MISMATCH', 'TX_INVALID',
      'NO_HASH', 'SEND_FAILED', 'TYPED_DATA_INVALID', 'BAD_SIGNATURE', 'SIGN_FAILED', 'MESSAGE_INVALID'];
    for (const code of codes) {
      const text = walletErrorText(Object.assign(new Error('The wallet could not do something in English.'), { code }));
      expect(text).toMatch(/[À-ỹ]/);
      expect(text).not.toContain('English');
    }
    expect(walletErrorText(Object.assign(new Error('Wallet connection was declined.'), { code: 'SOMETHING_ELSE' }))).toBe('Không thể hoàn tất thao tác. Hãy thử lại.');
    expect(walletErrorText(Object.assign(new Error('Hãy chọn hạn bàn giao mới.'), { code: 'NO_DATE' }))).toBe('Hãy chọn hạn bàn giao mới.');
  });
});
