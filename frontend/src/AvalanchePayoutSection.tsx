import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { ActionGroup } from './components';
import type { AvalanchePayoutView } from './avalancheTypes';
import { shortHash, tokenLabel, usdcFromBaseUnits } from './avalanchePresentation';
import { EvmWallet, WalletError } from './evmWallet';

/** Stable reason codes from the backend, shown in plain words. Unknown codes are shown as the code itself. */
export const UNSUPPORTED_REASON_TEXT: Readonly<Record<string, string>> = {
  AVALANCHE_PAYOUT_DISABLED: 'Chi trả trên Avalanche đang tắt trong môi trường này.',
  PARTNER_NOT_CONFIGURED: 'Chưa có đối tác chi trả nào hỗ trợ mạng và token này.',
  PARTNER_NETWORK_UNSUPPORTED: 'Đối tác chi trả không hỗ trợ mạng này.',
  PARTNER_TOKEN_UNSUPPORTED: 'Đối tác chi trả không hỗ trợ token này.',
  PARTNER_TOKEN_NOT_CONFIGURED: 'Đối tác chi trả chưa được cấu hình token nào.',
  PARTNER_CAPABILITY_UNAVAILABLE: 'Đối tác chi trả không hỗ trợ yêu cầu này.',
  SIMULATED_PARTNER_OUTSIDE_LOCAL_SIMULATION: 'Đối tác mô phỏng chỉ chạy trong môi trường mô phỏng cục bộ.',
  CONTRACT_NOT_ON_AVALANCHE_RAIL: 'Hợp đồng này không dùng Avalanche; không chuyển sang mạng khác.',
};

export const PAYOUT_STATUS_TEXT: Readonly<Record<string, string>> = {
  AWAITING_USER_TRANSFER: 'Chờ bạn chuyển USDC từ ví của bạn',
  TRANSFER_SUBMITTED: 'Đã ghi nhận giao dịch chuyển, đang chờ xác nhận trên chuỗi',
  TRANSFER_FAILED: 'Giao dịch chuyển không hợp lệ; bạn có thể chuyển lại',
  TRANSFER_CONFIRMED: 'Đã xác nhận chuyển trên chuỗi; chưa có xác nhận chi trả',
  FIAT_PAYOUT_PENDING: 'Đã xác nhận chuyển trên chuỗi; đối tác đang xử lý chi trả',
  FIAT_PAYOUT_CONFIRMED: 'Đối tác báo đã chi trả',
  FIAT_PAYOUT_FAILED: 'Đối tác báo chi trả thất bại',
};

function message(error: unknown): string {
  if (error instanceof WalletError || error instanceof ApiError) return error.message;
  return 'Không thể hoàn tất thao tác. Hãy thử lại.';
}

type Props = { contractId: string; expectedChainId: number; simulated: boolean; walletFactory?: () => EvmWallet };

/**
 * Freelancer-only, after the escrow released. Non-custodial: the user's own wallet sends the backend-built transfer to the
 * partner deposit address; FreelaX never holds or moves the funds. Shows the on-chain and fiat confirmations separately.
 */
export function AvalanchePayoutSection({ contractId, expectedChainId, simulated, walletFactory }: Props) {
  const [payout, setPayout] = useState<AvalanchePayoutView | null | undefined>(undefined);
  const [unsupported, setUnsupported] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

  const load = useCallback(async () => {
    try {
      const p = await api.avalanchePayout(contractId);
      if (alive.current) setPayout(p ?? null);
    } catch (cause) {
      if (alive.current) { setPayout(null); setError(message(cause)); }
    }
  }, [contractId]);
  useEffect(() => { void load(); }, [load]);

  async function run(work: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { await work(); } catch (cause) { if (alive.current) setError(message(cause)); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }

  const request = () => run(async () => {
    const result = await api.avalanchePayoutRequest(contractId);
    if (result.outcome === 'UNSUPPORTED_RAIL' || !result.payout) {
      setUnsupported(result.reason ?? 'PARTNER_CAPABILITY_UNAVAILABLE');
      return;
    }
    setUnsupported(null);
    setPayout(result.payout);
  });

  const transfer = () => run(async () => {
    const tx = payout?.preparedTransfer;
    if (!payout || !tx) throw new WalletError('Không có giao dịch chuyển nào đang chờ.', 'NO_TRANSFER');
    if (tx.chainId !== expectedChainId) throw new WalletError('Giao dịch chuyển nhắm tới mạng khác.', 'CHAIN_MISMATCH');
    const wallet = (walletFactory ?? (() => new EvmWallet()))();
    const { address } = await wallet.connect(expectedChainId);
    if (address.toLowerCase() !== payout.fromAddress.toLowerCase()) {
      throw new WalletError('Hãy dùng đúng ví đã nhận tiền giải ngân.', 'WRONG_ACCOUNT');
    }
    const hash = await wallet.sendPrepared({ chainId: tx.chainId, to: tx.to, data: tx.data, value: tx.value }, address, expectedChainId);
    setPayout(await api.avalanchePayoutTransfer(payout.id, hash));
  });

  const refresh = () => run(async () => {
    if (!payout) return;
    setPayout(await api.avalanchePayoutRefresh(payout.id));
  });

  if (payout === undefined) return null;
  const token = tokenLabel(simulated);
  return <div className="avalanche-payout" data-testid="avalanche-payout" aria-label="Chi trả (mô phỏng)">
    <strong>Chi trả {simulated ? '(mô phỏng)' : '(testnet)'}</strong>
    {error && <p role="alert">{error}</p>}
    {unsupported && <p role="status" data-testid="payout-unsupported">
      Không hỗ trợ chi trả: {UNSUPPORTED_REASON_TEXT[unsupported] ?? unsupported}
    </p>}
    {!payout && <ActionGroup>
      <button className="button" disabled={busy} onClick={() => void request()}>Yêu cầu chi trả</button>
    </ActionGroup>}
    {payout && <>
      <p role="status" data-status={payout.status}>{PAYOUT_STATUS_TEXT[payout.status] ?? payout.status}</p>
      <dl className="avalanche-chain-facts">
        <div><dt>Số tiền</dt><dd>{usdcFromBaseUnits(payout.amountBaseUnits) ?? '—'} {token}</dd></div>
        <div><dt>Địa chỉ nhận của đối tác</dt><dd><code>{shortHash(payout.depositAddress)}</code></dd></div>
        <div><dt>Phí do đối tác báo</dt><dd>{payout.partnerReportedFee ?? 'không có'}</dd></div>
        {payout.platformFeeUsd != null && <div><dt>Phí nền tảng (mô phỏng, quy tắc rail Solana)</dt>
          <dd data-testid="platform-fee">{payout.platformFeeUsd} USD</dd></div>}
        {payout.taxableAmountVnd != null && <div><dt>Thu nhập chịu thuế (mô phỏng, quy tắc rail Solana)</dt>
          <dd data-testid="taxable-vnd">{payout.taxableAmountVnd.toLocaleString('vi-VN')} VND (tỷ giá {payout.taxUsdToVndRate ?? '—'})</dd></div>}
        <div><dt>Xác nhận chuyển trên chuỗi</dt><dd data-testid="onchain-confirmed">{payout.onChainTransferConfirmed ? 'Đã xác nhận' : 'Chưa'}</dd></div>
        <div><dt>Xác nhận chi trả của đối tác</dt><dd data-testid="fiat-confirmed">{payout.fiatPayoutConfirmed ? 'Đã xác nhận' : 'Chưa'}</dd></div>
        {payout.transferTxHash && <div><dt>Giao dịch chuyển</dt><dd><code>{shortHash(payout.transferTxHash)}</code></dd></div>}
        {payout.failureReason && <div><dt>Mã lỗi</dt><dd>{payout.failureReason}</dd></div>}
      </dl>
      <small>{payout.partnerSimulated ? 'Đối tác mô phỏng: không có tiền thật được chi trả. ' : ''}
        Xác nhận chuyển trên chuỗi không có nghĩa là đã chi trả. {payout.taxTreatment === 'SOLANA_RAIL_RULES_SIMULATED'
          ? 'Phí và thuế là số liệu mô phỏng theo quy tắc của rail Solana; chúng không trừ vào số tiền ví của bạn chuyển.'
          : 'Thuế cho chi trả Avalanche chưa được xác định.'}</small>
      <ActionGroup>
        {payout.preparedTransfer && <button className="button" disabled={busy} onClick={() => void transfer()}>Chuyển {token} bằng ví</button>}
        {!payout.preparedTransfer && payout.status !== 'FIAT_PAYOUT_CONFIRMED' && payout.status !== 'FIAT_PAYOUT_FAILED'
          && <button className="button button-secondary" disabled={busy} onClick={() => void refresh()}>Làm mới chi trả</button>}
      </ActionGroup>
    </>}
  </div>;
}
