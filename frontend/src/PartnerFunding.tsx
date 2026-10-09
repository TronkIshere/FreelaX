import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { api } from './api';
import { ActionGroup, SectionHeading } from './components';
import type { BankCode, ClientBankAccount, FundingResponse, Job, User } from './types';

const banks: BankCode[] = ['VIETCOMBANK', 'VIETINBANK', 'BIDV', 'AGRIBANK', 'TECHCOMBANK', 'MBBANK', 'ACB', 'VPBANK', 'SACOMBANK', 'TPBANK'];

export function PartnerFundingPanel({ job, user, onJobUpdated, blocked = false, operationLock, onMutationChange, onChooseBack }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
  onChooseBack: () => void;
}) {
  const contract = job.contract;
  const owner = user.userType === 'CLIENT' && user.id === job.clientUserId;
  const [funding, setFunding] = useState<FundingResponse | null>(null);
  const [bank, setBank] = useState<ClientBankAccount | null>(null);
  const [bankCode, setBankCode] = useState<BankCode>('VIETCOMBANK');
  const [account, setAccount] = useState('');
  const [holder, setHolder] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  const key = useRef<string>(crypto.randomUUID());
  const active = useRef(true);

  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  useEffect(() => { onMutationChange?.(busy); }, [busy, onMutationChange]);
  useEffect(() => {
    if (!contract?.milestoneId) return;
    let live = true;
    const load = async () => {
      try {
        const [state, savedBank] = await Promise.all([
          contract.paymentRail === 'PARTNER_ESCROW_MOCK'
            ? api.partnerFunding(contract.id, contract.milestoneId!) : Promise.resolve(null),
          owner ? api.clientBank() : Promise.resolve(null),
        ]);
        if (live) { setFunding(state); setBank(savedBank); setError(''); }
        if (state?.fundingStatus === 'SUCCEEDED' && job.status === 'AWAITING_PAYMENT') {
          const fresh = await api.job(job.id);
          if (live) onJobUpdated(fresh);
        }
      } catch { if (live) setError('Chưa đối chiếu được trạng thái ký quỹ. Hãy thử lại.'); }
      finally { if (live) setLoading(false); }
    };
    void load();
    return () => { live = false; };
  }, [contract?.id, contract?.milestoneId, contract?.paymentRail, job.id, job.status, owner, tick]);
  useEffect(() => {
    if (!funding || funding.fundingStatus === 'SUCCEEDED' || funding.fundingStatus === 'FAILED') return;
    const timer = window.setTimeout(() => setTick(value => value + 1), 5000);
    return () => window.clearTimeout(timer);
  }, [funding, tick]);

  async function saveBank(event: React.FormEvent) {
    event.preventDefault();
    if (!owner || blocked || operationLock?.current || busy) return;
    if (!/^[0-9]{6,34}$/.test(account) || holder.trim().length < 2) { setError('Kiểm tra số tài khoản và tên chủ tài khoản.'); return; }
    setBusy(true); setError('');
    try {
      const result = await api.saveClientBank({ bankCode, bankAccountNumber: account, bankAccountHolderName: holder.trim() });
      if (active.current) { setBank(result); setAccount(''); setHolder(''); }
    } catch { if (active.current) setError('Không lưu được tài khoản ngân hàng.'); }
    finally { if (active.current) setBusy(false); }
  }

  async function fund() {
    if (!owner || !contract?.milestoneId || !bank?.ready || blocked || busy || operationLock?.current) return;
    if (contract.status !== 'PENDING_FUNDING' || job.status !== 'AWAITING_PAYMENT') return;
    setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
    try {
      const result = await api.fundPartner(contract.id, contract.milestoneId, key.current);
      if (active.current) { setFunding(result); setTick(value => value + 1); onJobUpdated(await api.job(job.id)); }
    } catch { if (active.current) { setError('Chưa xác nhận được tiền ở đối tác mock. Giữ nguyên lần gửi và đối soát lại.'); setTick(value => value + 1); } }
    finally { if (operationLock) operationLock.current = false; if (active.current) setBusy(false); }
  }

  if (!contract || (!owner && job.freelancerId !== user.id)) return null;
  return <section className="funding-document" id="funding" aria-label="Ký quỹ đối tác mock">
    <SectionHeading title="Ký quỹ qua đối tác" aside="MVP mock · Không chuyển tiền thật" />
    <p>Client gửi đủ {String(contract.amount)} USD tới tài khoản ký quỹ của đối tác mô phỏng. Freelancer chỉ bắt đầu khi đối tác xác nhận đã nhận tiền và Job hiển thị “Đã ký quỹ”.</p>
    <p role="status">{loading ? 'Đang đối soát…' : funding?.fundingStatus === 'SUCCEEDED' ? 'Đã ký quỹ' : funding ? 'Đang chờ đối tác xác nhận và đối soát' : 'Chưa có lệnh ký quỹ'}</p>
    <p>FreelaX thu 3% từ Freelancer khi giải ngân thành công. Hoàn tiền trước giải ngân trả đủ USD cho Client và không thu phí.</p>
    {owner && <>
      {bank?.ready ? <p>Tài khoản Client: {bank.bankCode} · {bank.maskedAccountNumber}</p>
        : <form className="bank-form" onSubmit={saveBank}>
          <label>Ngân hàng<select value={bankCode} onChange={e => setBankCode(e.target.value as BankCode)}>{banks.map(code => <option key={code}>{code}</option>)}</select></label>
          <label>Số tài khoản<input required pattern="[0-9]{6,34}" value={account} onChange={e => setAccount(e.target.value)} /></label>
          <label>Tên chủ tài khoản<input required minLength={2} value={holder} onChange={e => setHolder(e.target.value)} /></label>
          <ActionGroup><button className="button" disabled={busy || blocked}>Lưu tài khoản</button></ActionGroup>
        </form>}
      {contract.status === 'PENDING_FUNDING' && <ActionGroup>
        <button className="button" type="button" disabled={loading || busy || blocked || !bank?.ready || !!funding} onClick={() => void fund()}>Gửi lệnh ký quỹ mock</button>
        {!funding && contract.paymentRail !== 'PARTNER_ESCROW_MOCK' && <button className="button button-secondary" type="button" disabled={busy} onClick={onChooseBack}>Quay lại</button>}
      </ActionGroup>}
    </>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" type="button" disabled={busy} onClick={() => setTick(value => value + 1)}>Đối soát lại</button>
  </section>;
}
