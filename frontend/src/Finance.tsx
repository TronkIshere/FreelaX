import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { WalletCards, Coins, LockKeyhole, Clock3, RotateCcw, CircleCheck, CalendarDays, ArrowRight, ArrowLeft, ArrowUpRight, Landmark, CircleAlert, Check, BriefcaseBusiness, FileText, Info, RefreshCw } from 'lucide-react';
import { motion } from 'motion/react';
import { JobCategoryPlate, JobIdentityCluster } from './ui/JobRowIdentity';
import { RoughBurst, RoughUnderline, useKineticMotion } from './ui/kinetic';
import { contractMoneyStages, legacyMoneyStages, resolveMoneySpine, type MoneyStage } from './financeSpine';
import { ApiError, api } from './api';
import { taxPresentation } from './taxPresentation';
import { EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import { Pagination } from './Jobs';
import { checkoutLabel, clientPaymentLabel, decimal, exportLabel, maskedBank, offRampLabel,
  paymentTerminal, rateSource, safeExplorerUrl, syncableTaxStatuses, usdc, vnd,
  financePath, contractFinanceLabel, contractFinanceNeedsRefresh, contractFinanceTone, financialCopy,
  settlementMoneyLabel, settlementStageLabel, releaseOwned } from './financeStatus';
import { cancellationLabel, date, fundingLabel, money, refundLabel } from './status';
import type { ContractFinance } from './financeStatus';
import type { EscrowFundingView, FundingResponse, Job, JobPaymentStatus, Page, PaymentFlowTimeline, TaxRecord, User } from './types';
import { connectSolanaWallet, signEscrowTransaction } from './escrowWallet';

const POLL_MS = 15000;
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'Không thể tải dữ liệu từ Marketplace.';
const missingTax = (cause: unknown) => cause instanceof ApiError && (cause.status === 404 || cause.code === 4010);
const stamp = (value: string | null | undefined) => value ? value.slice(0, 16).replace('T', ' · ') : '—';

function FinanceNav() {
  const { pathname } = useLocation();
  const taxActive = pathname === '/finance/tax-records' || pathname.startsWith('/finance/tax-records/');
  return <nav className="finance-nav" aria-label="Khu vực tài chính">
    <Link className={'finance-folder-tab' + (!taxActive ? ' finance-folder-tab--active' : '')}
      aria-current={!taxActive ? 'page' : undefined} to="/finance">
      <span className="finance-tab-index" aria-hidden="true">01</span>
      <BriefcaseBusiness size={20} aria-hidden="true" /><span>Theo công việc</span>
    </Link>
    <Link className={'finance-folder-tab' + (taxActive ? ' finance-folder-tab--active' : '')}
      aria-current={taxActive ? 'page' : undefined} to="/finance/tax-records">
      <span className="finance-tab-index" aria-hidden="true">02</span>
      <FileText size={20} aria-hidden="true" /><span>Chứng từ thuế</span>
    </Link>
  </nav>;
}

type PaymentEntry = { data: JobPaymentStatus | null; error: string; contract?: ContractFinance;
  escrow?: EscrowFundingView | null; flow?: PaymentFlowTimeline | null };

// The current API has no batch settlement/refund projection. Reads are bounded to the returned page.
async function readContractFinance(job: Job): Promise<ContractFinance> {
  const [settlement, cancellation] = await Promise.allSettled([
    api.settlement(job.contract!.id), api.cancellation(job.contract!.id),
  ]);
  return { settlement: settlement.status === 'fulfilled' ? settlement.value : null,
    cancellation: cancellation.status === 'fulfilled' ? cancellation.value : null,
    error: [settlement, cancellation].flatMap(item => item.status === 'rejected' ? [message(item.reason)] : []).join(' · ') };
}

function FinanceList({ user }: { user: User }) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Job> | null>(null);
  const [payments, setPayments] = useState<Record<string, PaymentEntry>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setPayments({});
    api.myJobs(page, 20).then(async data => {
      if (!active) return;
      setResult(data);
      const financial = data.data.filter(job => job.contract || job.status === 'COMPLETED');
      const entries = await Promise.all(financial.map(async job => {
        if (job.contract) {
          if (job.contract.paymentRail === 'UNIFIED_USDC_PAYOUT') {
            const flow = job.contract.milestoneId
              ? await api.paymentFlow(job.contract.id, job.contract.milestoneId)
                .then(data => ({ data, error: '' }), cause => ({ data: null, error: message(cause) }))
              : { data: null, error: 'Thiếu Milestone ID.' };
            return [job.id, { data: null, error: flow.error, flow: flow.data }] as const;
          }
          if (job.contract.paymentRail === 'SOLANA_ESCROW') {
            const escrow = job.contract.milestoneId
              ? await api.escrowFunding(job.contract.id, job.contract.milestoneId)
                .then(data => ({ data, error: '' }), cause => ({ data: null, error: message(cause) }))
              : { data: null, error: 'Thiếu Milestone ID.' };
            return [job.id, { data: null, error: escrow.error, escrow: escrow.data }] as const;
          }
          const contract = await readContractFinance(job);
          const downstream = job.checkoutOrderId && contract.settlement?.moneyStatus === 'SUCCEEDED'
            ? await api.paymentStatus(job.id).then(data => ({ data, error: '' }), cause => ({ data: null, error: message(cause) }))
            : { data: null, error: '' };
          return [job.id, { ...downstream, contract }] as const;
        }
        try {
          return [job.id, { data: await api.paymentStatus(job.id), error: '' }] as const;
        } catch (cause) {
          return [job.id, { data: null, error: message(cause) }] as const;
        }
      }));
      if (active) {
        setPayments(Object.fromEntries(entries));
        setLoading(false);
      }
    }, cause => { if (active) { setError(message(cause)); setLoading(false); } });
    return () => { active = false; };
  }, [page, attempt]);

  const freelancer = user.userType === 'FREELANCER';
  const financial = result?.data.filter(job => job.contract || job.status === 'COMPLETED') ?? [];
  return <section className={'finance-list-page' + (freelancer ? ' finance-list-page--income' : '')}>
    <PageHeading eyebrow={freelancer ? 'Freelancer / Thu nhập' : 'Client / Thanh toán'}
      title={<><span className="finance-title-start">{freelancer ? 'Thu nhập theo ' : 'Thanh toán theo '}
        <RoughBurst seedKey="finance:left" accent="vermilion" size={38} className="finance-rays finance-rays--left" /></span>
        <span className="finance-title-emphasis">công việc.<RoughUnderline seedKey="finance:underline" size={260} />
          <RoughBurst seedKey="finance:right" accent="ink" size={38} className="finance-rays finance-rays--right" /></span></>}
      description={freelancer ? 'Theo dõi release, chi trả và chứng từ theo từng công việc.'
        : 'Theo dõi thanh toán, release và hoàn tiền theo từng công việc.'} />
    <FinanceNav />
    {loading && <StatePanel kind="loading" title="Đang tải hồ sơ tài chính" body="Đang đối chiếu công việc và trạng thái thanh toán." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải công việc" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      <FinanceSummary count={financial.length} freelancer={freelancer} />
      <header className="finance-ledger-heading"><h2>{freelancer ? 'Lịch sử thu nhập' : 'Danh sách thanh toán'}</h2>
        <span>{financial.length} hồ sơ tài chính trên trang này</span></header>
      {financial.length === 0 ? <StatePanel kind="empty" title="Chưa có hồ sơ tài chính trên trang này"
        body="Chuyển trang để xem các công việc khác. Hồ sơ hợp đồng đang xử lý tiền cũng xuất hiện tại đây." /> :
        <div className="finance-ledger">
          <div className="finance-ledger-header" aria-hidden="true"><span>Công việc</span><span>Giá trị</span>
            <span>Trạng thái</span><span>Ngày cập nhật</span><span>Thao tác</span></div>
          <div className="finance-ledger-rows">{financial.map(job => <FinanceLedgerRow key={job.id} job={job} entry={payments[job.id]} />)}</div>
        </div>}
      <div className="finance-list-tools"><Pagination page={result} onPage={setPage} />
        <button className="text-button finance-refresh" type="button" onClick={() => setAttempt(value => value + 1)}>Làm mới từ Marketplace</button></div>
      <FinanceProcess freelancer={freelancer} />
    </>}
  </section>;
}

// No monetary aggregates are exposed by the current API. The three process tiles
// are a reading key, not invented totals or interactive filters.
function FinanceSummary({ count, freelancer }: { count: number; freelancer: boolean }) {
  const steps = [
    { key: 'funding', label: 'Thanh toán Client', title: 'Funding', note: 'Xác nhận theo từng hồ sơ', Icon: LockKeyhole, tone: 'active' },
    { key: 'release', label: freelancer ? 'Giải ngân cho Freelancer' : 'Release cho Freelancer', title: 'Release',
      note: 'Xác nhận theo từng hồ sơ', Icon: CircleCheck, tone: 'done' },
    { key: 'refund', label: 'Hoàn tiền cho Client', title: 'Hoàn tiền', note: 'Xác nhận theo từng hồ sơ', Icon: RotateCcw, tone: 'refund' },
  ];
  if (!freelancer) [steps[1], steps[2]] = [steps[2], steps[1]];
  const TotalIcon = freelancer ? Coins : WalletCards;
  return <section className="finance-summary" aria-label="Hồ sơ và các chặng tài chính">
    <article className="finance-summary-card finance-color-pending">
      <TotalIcon aria-hidden="true" className="finance-summary-icon" strokeWidth={1.8} />
      <div><h2>Hồ sơ trên trang</h2><strong className="finance-summary-count">{count}</strong><p>hồ sơ tài chính</p></div>
    </article>
    {steps.map(({ key, label, title, note, Icon, tone }) => <article key={key} className={'finance-summary-card finance-color-' + tone}>
      <Icon aria-hidden="true" className="finance-summary-icon" strokeWidth={1.8} />
      <div><h2>{label}</h2><strong className="finance-summary-stage">{title}</strong><p>{note}</p></div>
    </article>)}
  </section>;
}

function financeRowTone(job: Job, entry?: PaymentEntry) {
  if (job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT') {
    const step = (kind: string) => entry?.flow?.steps.find(s => s.kind === kind)?.status;
    return step('USD_REFUND') === 'CONFIRMED' ? 'refund'
      : step('VND_PAYOUT') === 'CONFIRMED' ? 'done' : 'active';
  }
  if (job.contract?.paymentRail === 'SOLANA_ESCROW') {
    return entry?.escrow?.status === 'Released' ? 'done' : entry?.escrow?.status === 'Refunded' ? 'refund' : 'active';
  }
  if (entry?.contract?.cancellation?.refundStatus === 'SUCCEEDED') return 'refund';
  // Cancellation without a successful refund must not look like received money.
  if (job.contract?.status === 'CANCELLED' || entry?.contract?.cancellation?.cancellationStatus === 'CANCELLED') return 'neutral';
  if (job.contract) return contractFinanceTone(job, entry?.contract);
  if (entry?.error) return 'error';
  if (entry?.data?.checkoutOrderStatus === 'CAPTURED') return 'done';
  return entry?.data?.checkoutOrderStatus === 'FAILED' ? 'error' : 'pending';
}

function financeUpdatedAt(entry?: PaymentEntry) {
  const data = entry?.data;
  return [entry?.contract?.settlement?.updatedAt, entry?.contract?.cancellation?.updatedAt,
    ...entry?.flow?.steps.map(step => step.confirmedAt) || [],
    data?.clientPaymentSubmittedAt, data?.clientPaymentConfirmedAt, data?.withdrawalSubmittedAt,
    data?.withdrawalConfirmedAt, data?.simulatedPayoutAt, data?.offRampCompletionSubmittedAt, data?.offRampCompletedAt]
    .filter((value): value is string => !!value && Number.isFinite(Date.parse(value)))
    .sort((a, b) => Date.parse(b) - Date.parse(a))[0];
}

function FinanceLedgerRow({ job, entry }: { job: Job; entry?: PaymentEntry }) {
  const status = entry?.data;
  const tone = financeRowTone(job, entry);
  const Icon = tone === 'refund' ? RotateCcw : tone === 'done' ? CircleCheck : tone === 'active' ? Clock3 : LockKeyhole;
  const updatedAt = financeUpdatedAt(entry);
  return <article className={'finance-ledger-row finance-color-' + tone} aria-label={job.title}>
    <div className="finance-ledger-job"><JobIdentityCluster job={job} /><div>
      <h3><Link to={financePath(job.id)}>{job.title}</Link></h3><JobCategoryPlate job={job} />
      {!!job.skills?.length && <ul className="finance-job-skills" aria-label="Kỹ năng công việc">{job.skills.map(skill => <li key={skill}>{skill}</li>)}</ul>}
    </div></div>
    <div className="finance-ledger-value"><span className="finance-mobile-label">Giá trị công việc</span><strong>{money(job.budgetUsd)}</strong>
      {status?.amountUsdcReceived != null && <span>{usdc(status.amountUsdcReceived)}</span>}
      {status?.estimatedAmountVnd != null && <span>{vnd(status.estimatedAmountVnd)} dự kiến</span>}</div>
    <div className="finance-ledger-state"><span className="finance-status-badge"><Icon size={19} aria-hidden="true" />
      {job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT' ? 'Unified: ' + (entry?.flow?.steps.find(s => s.status === 'PENDING' || s.status === 'UNKNOWN')?.kind || (tone === 'done' ? 'VND_PAID' : tone === 'refund' ? 'USD_REFUNDED' : 'Đang đối soát')) : job.contract?.paymentRail === 'SOLANA_ESCROW' ? 'Solana escrow: ' + (entry?.escrow?.status || 'Đang đối soát') : job.contract ? contractFinanceLabel(job, entry?.contract) : status ? checkoutLabel(status.checkoutOrderStatus) : entry?.error || 'Chưa có dữ liệu'}</span>
      <span className="finance-payout-label">{job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT' ? entry?.flow?.steps.find(s => s.kind === 'VND_PAYOUT')?.status === 'CONFIRMED' ? 'Đối tác mock đã xác nhận VND' : 'VND chưa xác nhận' : job.contract?.paymentRail === 'SOLANA_ESCROW' ? 'Token vault on-chain' : job.contract?.paymentRail === 'PARTNER_ESCROW_MOCK' ? entry?.contract?.settlement?.moneyStatus === 'SUCCEEDED' ? 'Đối tác mock đã chi ' + String(entry.contract.settlement.partnerPayoutVnd) + ' VND · phí FreelaX ' + String(entry.contract.settlement.platformFeeUsd) + ' USD' : 'Chưa giải ngân VND' : entry?.contract?.settlement ? 'Chi trả: ' + settlementStageLabel(entry.contract.settlement.offRampStatus)
        : status ? offRampLabel(status.offRampStatus) : 'Chưa có dữ liệu chi trả'}</span>
      {(entry?.flow?.simulation || status?.simulation || entry?.contract?.settlement?.simulation || entry?.contract?.cancellation?.simulation) && <b className="simulation-mark">Mô phỏng</b>}
      {(entry?.error || entry?.contract?.error) && <span className="finance-row-error" role="alert">{entry.error || entry.contract?.error}</span>}
    </div>
    <div className="finance-ledger-date"><span className="finance-mobile-label">Ngày cập nhật</span>
      <CalendarDays size={17} aria-hidden="true" />{updatedAt ? <time dateTime={updatedAt}>{stamp(updatedAt)}</time> : <span>Chưa có cập nhật</span>}</div>
    <Link className="finance-detail-link" to={financePath(job.id)} aria-label={'Xem chi tiết: ' + job.title}>Xem chi tiết <ArrowRight size={22} aria-hidden="true" /></Link>
  </article>;
}

function FinanceProcess({ freelancer }: { freelancer: boolean }) {
  const Icon = freelancer ? Coins : WalletCards;
  return <section className="finance-process" aria-labelledby="finance-process-title">
    <Icon className="finance-process-icon" aria-hidden="true" strokeWidth={1.8} />
    <div><h2 id="finance-process-title">{freelancer ? 'Quy trình nhận tiền' : 'Quy trình thanh toán'}</h2>
      <p>{freelancer ? 'Đối chiếu release, chi trả và chứng từ trong hồ sơ của từng công việc.'
        : 'Đối chiếu funding, release và hoàn tiền trong hồ sơ của từng công việc.'}</p>
      <details><summary>Tìm hiểu thêm <ArrowRight size={20} aria-hidden="true" /></summary>
        <p>{financialCopy.fundingVsRelease} {financialCopy.releaseSimulation} {financialCopy.taxVsCertificate}</p></details>
    </div>
  </section>;
}

function MoneySpine({ stages, contract = false }: { stages: MoneyStage[]; contract?: boolean }) {
  const { current } = resolveMoneySpine(stages);
  const { enabled, transition } = useKineticMotion();
  const previous = useRef<string[] | null>(null);
  const snapshot = stages.map(stage => stage.key + ':' + stage.tone + ':' + stage.status);
  const changed = snapshot.map((value, index) => enabled && !!previous.current && previous.current[index] !== value);
  useEffect(() => { previous.current = snapshot; });
  const icons = [WalletCards, Coins, ArrowUpRight, Landmark, FileText];
  return <section className="money-spine-section" aria-labelledby="money-spine-title">
    <div className="money-spine-heading"><h2 id="money-spine-title">5 giai đoạn dòng tiền</h2><span>{contract ? 'Hồ sơ hợp đồng' : 'Hồ sơ thanh toán'} / Marketplace</span></div>
    <ol className={'money-spine ' + (contract ? 'contract-money-spine' : 'finance-stages')} aria-label="Năm chặng tài chính">{stages.map((stage, index) => {
      const isCurrent = current === index;
      const completedPath = stages.slice(0, index + 1).every(item => item.tone === 'done');
      const intoCurrent = completedPath && current === index + 1;
      const Icon = icons[index];
      // Status copy/icons follow source-derived tone, never isCurrent.
      const StateIcon = stage.tone === 'done' ? CircleCheck : stage.tone === 'error' ? CircleAlert : Clock3;
      return <li key={stage.key} className={'money-spine-item stage-' + stage.tone + (isCurrent ? ' money-spine-current' : '')}
        data-stage={stage.key} data-state={stage.tone} aria-current={isCurrent ? 'step' : undefined}>
        <div className="money-spine-track" aria-hidden="true">
          <motion.span className="money-spine-node" initial={false} animate={{ scale: changed[index] && stage.tone === 'done' ? [1, 1.12, 1] : 1 }} transition={transition}>
            {stage.tone === 'done' ? <Check size={24} strokeWidth={3} /> : stage.tone === 'error' ? <CircleAlert size={24} /> : String(index + 1).padStart(2, '0')}
          </motion.span>
          {index < stages.length - 1 && <motion.span className={'money-spine-connector ' + (completedPath ? 'connector-completed' : 'connector-upcoming') + (intoCurrent ? ' connector-current' : '')}
            initial={false} animate={{ scaleY: changed[index] && completedPath ? [.85, 1] : 1 }} transition={{ ...transition, duration: enabled ? .24 : 0 }} />}
        </div>
        <motion.article className={'money-stage-record' + (!contract ? ' finance-stage' : '')} initial={false}
          animate={{ y: changed[index] && isCurrent ? [0, -3, 0] : 0 }} transition={transition}>
          <span className="money-stage-index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
          <div className="money-stage-body"><header className="money-stage-heading"><Icon className="money-stage-icon" aria-hidden="true" strokeWidth={1.8} />
            <div><h3>{stage.title}</h3><p>{stage.note}</p></div>
            <div className="money-stage-state"><span className="money-stage-badge"><StateIcon size={19} aria-hidden="true" />
              {stage.applicable === false ? 'Không áp dụng' : stage.tone === 'done' ? 'Đã xác nhận' : stage.tone === 'error' ? 'Cần kiểm tra' : stage.tone === 'active' ? 'Đang đối soát' : 'Chờ bằng chứng'}</span>
              {isCurrent && <span className="money-current-caption">Giai đoạn hiện tại {index + 1}/5</span>}</div>
          </header>
          <p className="money-stage-status">{stage.status}</p>
          {stage.timestamp && <time className="money-stage-date" dateTime={stage.timestamp}><CalendarDays size={15} aria-hidden="true" />{stamp(stage.timestamp)}</time>}
          {!!stage.facts.length && <FactGrid facts={stage.facts} />}
          {stage.error && <p className="finance-stage-error" role="alert">{stage.error}</p>}</div>
        </motion.article>
      </li>;
    })}</ol>
  </section>;
}

function FinanceJobStatement({ job, payment, tax, record, simulated = false }: { job: Job; payment: JobPaymentStatus | null;
  tax: TaxRecord | null; record?: ContractFinance; simulated?: boolean }) {
  const updated = [financeUpdatedAt({ data: payment, error: '', contract: record }), tax?.updatedAt]
    .filter((value): value is string => !!value && Number.isFinite(Date.parse(value)))
    .sort((a, b) => Date.parse(b) - Date.parse(a))[0];
  return <section className="finance-job-statement" aria-label="Hồ sơ tài chính công việc">
    <div className="finance-statement-art"><JobIdentityCluster job={job} /></div>
    <div className="finance-statement-job"><JobCategoryPlate job={job} /><h2>{job.title}</h2>
      {updated && <span className="finance-case-updated"><CalendarDays size={19} aria-hidden="true" />Cập nhật bản ghi <time dateTime={updated}>{stamp(updated)}</time></span>}</div>
    <div className="finance-statement-value"><span>{job.contract ? 'Giá trị hợp đồng' : 'Giá trị công việc'}</span>
      <strong>{job.contract ? decimal(job.contract.amount) + ' ' + job.contract.currency : money(job.budgetUsd)}</strong>
      {payment?.amountUsdcReceived != null && <span>{usdc(payment.amountUsdcReceived)}</span>}
      {payment?.estimatedAmountVnd != null && <span>{vnd(payment.estimatedAmountVnd)} dự kiến</span>}</div>
    <div className="finance-case-environment">{(simulated || payment?.simulation === true) && <strong className="simulation-mark">Mô phỏng</strong>}
      {payment?.network?.toLowerCase() === 'devnet' && <strong className="network-mark">DEVNET</strong>}
      {!simulated && payment?.simulation !== true && payment?.network && <span>Mạng: {payment.network}</span>}</div>
  </section>;
}

function FinancialCaseSummary({ job, payment, stages, tax, refresh, busy = false, children, stale = false, simulated = false }: {
  job: Job; payment: JobPaymentStatus | null; stages: MoneyStage[]; tax: TaxRecord | null; refresh: () => void; busy?: boolean;
  children?: React.ReactNode; stale?: boolean; simulated?: boolean;
}) {
  const { current, resolved } = resolveMoneySpine(stages);
  const focus = current < 0 ? null : stages[current];
  return <aside className="finance-case-sidebar" aria-label="Tóm tắt hồ sơ tiền">
    <section className="finance-case-summary"><header><WalletCards size={36} aria-hidden="true" /><div><h2>Tóm tắt hồ sơ tiền</h2><p>Một công việc / một hồ sơ</p></div></header>
      <dl><div><dt>Công việc</dt><dd>{job.title}</dd></div><div><dt>Danh mục</dt><dd><JobCategoryPlate job={job} /></dd></div>
        <div><dt>{job.contract ? 'Giá trị hợp đồng' : 'Giá trị công việc'}</dt><dd>{job.contract ? decimal(job.contract.amount) + ' ' + job.contract.currency : money(job.budgetUsd)}</dd></div>
        {payment?.amountUsdcReceived != null && <div><dt>USDC theo bản ghi</dt><dd>{usdc(payment.amountUsdcReceived)}</dd></div>}
        {payment?.estimatedAmountVnd != null && <div><dt>VND dự kiến</dt><dd>{vnd(payment.estimatedAmountVnd)}</dd></div>}</dl>
      <div className={'finance-case-focus stage-' + (focus?.tone || 'done')} aria-live="polite"><span>{stale ? 'Cần đối chiếu lại' : resolved ? 'Các chặng đã xác nhận' : 'Giai đoạn hiện tại'}</span>
        <strong>{focus ? focus.title : resolved ? 'Hồ sơ đã đối chiếu' : 'Hồ sơ không có chặng chi trả tiếp theo'}</strong>
        {focus && <span>{focus.status} · Bước {current + 1}/5</span>}</div>
      {children}
    </section>
    <section className="finance-case-actions"><h2><FileText size={23} aria-hidden="true" />Bằng chứng liên quan</h2>
      <Link className="button button-caution" to={tax ? '/finance/tax-records/' + encodeURIComponent(tax.id) : '/finance/tax-records'}>Xem chứng từ thuế <ArrowUpRight size={22} aria-hidden="true" /></Link>
      <Link className="button button-secondary" to={'/work/' + encodeURIComponent(job.id)}>Hồ sơ công việc <ArrowRight size={22} aria-hidden="true" /></Link>
      <button className="text-button finance-refresh" type="button" disabled={busy} onClick={refresh}><RefreshCw size={17} aria-hidden="true" />{busy ? 'Đang đối chiếu…' : stale ? 'Đối chiếu lại' : 'Làm mới từ Marketplace'}</button>
    </section>
    <section className="finance-case-note"><h2><Info size={23} aria-hidden="true" />Môi trường & bằng chứng</h2>
      {(simulated || payment?.simulation === true) && <strong className="simulation-mark">Mô phỏng</strong>}
      {payment?.network?.toLowerCase() === 'devnet' && <strong className="network-mark">DEVNET</strong>}
      <p>{simulated || payment?.simulation === true ? 'Bản ghi mô phỏng không xác nhận chuyển khoản ngân hàng thật.' : 'Trạng thái hiển thị theo bằng chứng Marketplace đã trả về.'}</p>
      <p>{financialCopy.taxVsCertificate}</p>
    </section>
  </aside>;
}

function TechnicalEvidence({ payment, tax }: { payment: JobPaymentStatus; tax: TaxRecord | null }) {
  const groups: { title: string; entries: [string, string | null | undefined][]; explorer?: string | null }[] = [
    { title: 'Checkout', entries: [['Checkout ID', payment.checkoutOrderId]] },
    { title: 'Mock on-ramp', entries: [['Purchase ID', payment.onRampPurchaseId],
      ['Chữ ký on-ramp', payment.onRampTransactionSignature], ['Receipt PDA', payment.onRampReceiptPda],
      ['Client public key', payment.onRampClientPublicKey]], explorer: payment.explorerUrl },
    { title: 'Tỷ giá và hóa đơn', entries: [['Rate ID', payment.rateId],
      ['Chữ ký tỷ giá', payment.rateTransactionSignature], ['Rate PDA', payment.rateSnapshotPda],
      ['Invoice ID', payment.invoiceId], ['Invoice PDA', payment.invoicePda],
      ['Chữ ký hóa đơn', payment.invoiceTransactionSignature]] },
    { title: 'Thanh toán on-chain', entries: [['Chữ ký thanh toán', payment.paymentTransactionSignature],
      ['Payment mint', payment.paymentMint], ['Freelancer public key', payment.freelancerPublicKey]],
      explorer: payment.paymentExplorerUrl },
    { title: 'Rút on-chain', entries: [['Withdrawal ID', payment.withdrawalId],
      ['Withdrawal PDA', payment.withdrawalPda], ['Chữ ký rút', payment.withdrawalTransactionSignature],
      ['Treasury public key', payment.treasuryPublicKey], ['Treasury USDC ATA', payment.treasuryUsdcAta]],
      explorer: payment.withdrawalExplorerUrl },
    { title: 'Hoàn tất mô phỏng và thuế', entries: [['Off-ramp reference', payment.offRampReference],
      ['Chữ ký hoàn tất', payment.offRampCompletionSignature], ['Tax record ID', tax?.id],
      ['MISA certificate ID', tax?.misaCertificateId]],
      explorer: payment.offRampCompletionExplorerUrl },
  ];
  return <details className="technical-evidence">
    <summary>Bằng chứng kỹ thuật · ID, chữ ký và địa chỉ</summary>
    <div className="technical-groups">{groups.map(group => {
      const entries = group.entries.filter((entry): entry is [string, string] => !!entry[1]);
      const explorer = safeExplorerUrl(group.explorer, payment.network);
      if (entries.length === 0 && !explorer) return null;
      return <section key={group.title}><h3>{group.title}</h3><dl>{entries.map(([label, value]) =>
        <div key={label}><dt>{label}</dt><dd><code>{value}</code></dd></div>)}</dl>
        {explorer && <a href={explorer} target="_blank" rel="noopener noreferrer">
          Xem giao dịch này trên Solana Explorer · DEVNET ↗</a>}</section>;
    })}</div>
  </details>;
}

function ContractEvidence({ initialJob }: { initialJob: Job }) {
  const [job, setJob] = useState(initialJob);
  const [record, setRecord] = useState<ContractFinance>({ settlement: null, cancellation: null, error: '' });
  const [funding, setFunding] = useState<FundingResponse | null>(null);
  const [fundingError, setFundingError] = useState('');
  const [tax, setTax] = useState<TaxRecord | null>(null);
  const [taxError, setTaxError] = useState('');
  const [payment, setPayment] = useState<JobPaymentStatus | null>(null);
  const [paymentError, setPaymentError] = useState('');
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let active = true;
    setBusy(true);
    async function read() {
      const current = attempt || tick ? await api.job(initialJob.id) : initialJob;
      const contract = current.contract!;
      const financial = await readContractFinance(current);
      const [nextFunding, nextTax] = await Promise.all([
        contract.milestoneId ? api.funding(contract.id, contract.milestoneId)
          .then(data => ({ data, error: '' }), cause => ({ data: null, error: message(cause) }))
          : Promise.resolve({ data: null, error: '' }),
        financial.settlement || releaseOwned(current) ? api.taxRecordForJob(current.id).then(data => ({ data, error: '' }), cause => ({ data: null,
          error: missingTax(cause) ? '' : message(cause) })) : Promise.resolve({ data: null, error: '' }),
      ]);
      // The legacy projection supplies downstream amounts/bank evidence, never release/refund proof.
      const nextPayment = current.checkoutOrderId && (financial.settlement || releaseOwned(current))
        ? await api.paymentStatus(current.id).then(data => ({ data, error: '' }), cause => ({ data: null, error: message(cause) }))
        : { data: null, error: '' };
      if (!active) return;
      setJob(current);
      setRecord(previous => ({ ...financial,
        settlement: financial.settlement ?? (financial.error ? previous.settlement : null),
        cancellation: financial.cancellation ?? (financial.error ? previous.cancellation : null) }));
      setFunding(previous => nextFunding.error ? previous : nextFunding.data); setFundingError(nextFunding.error);
      setTax(previous => nextTax.error ? previous : nextTax.data); setTaxError(nextTax.error);
      setPayment(previous => nextPayment.error ? previous : nextPayment.data); setPaymentError(nextPayment.error);
    }
    read().catch(cause => { if (active) setRecord(previous => ({ ...previous, error: message(cause) })); })
      .finally(() => { if (active) { setReady(true); setBusy(false); } });
    return () => { active = false; };
  }, [initialJob, attempt, tick]);
  useEffect(() => {
    if (!ready || busy || !contractFinanceNeedsRefresh(job, record)) return;
    const refreshVisible = () => { if (document.visibilityState !== 'hidden') setTick(value => value + 1); };
    const timer = window.setTimeout(refreshVisible, 30000);
    document.addEventListener('visibilitychange', refreshVisible);
    return () => { window.clearTimeout(timer); document.removeEventListener('visibilitychange', refreshVisible); };
  }, [ready, busy, job, record, tick, attempt]);
  const { settlement, cancellation } = record;
  const refreshed = () => setAttempt(value => value + 1);
  const stages = contractMoneyStages(job, record, funding, tax, fundingError, taxError);
  const simulated = !!(settlement?.simulation || cancellation?.simulation || funding?.simulation);
  const technicalFacts = [
    { label: 'Tham chiếu release', value: settlement?.releaseReference },
    { label: 'Tham chiếu hoàn tiền', value: cancellation?.refundReference },
    { label: 'Tham chiếu on-chain', value: settlement?.onChainReference },
    { label: 'Tham chiếu chi trả VND', value: settlement?.offRampReference },
    { label: 'Tham chiếu chứng từ', value: settlement?.taxReference },
    { label: 'Lỗi release', value: settlement?.lastError },
    { label: 'Lỗi on-chain', value: settlement?.onChainError },
    { label: 'Lỗi VND', value: settlement?.offRampError },
    { label: 'Lỗi thuế', value: settlement?.taxError },
    { label: 'Lỗi hoàn tiền', value: cancellation?.lastError },
    { label: 'Lỗi bản ghi chi trả', value: paymentError },
  ].filter(fact => fact.value);
  return <>
    {!ready && <StatePanel kind="loading" title="Đang đối chiếu hồ sơ hợp đồng" body="Đọc funding, release và đề nghị hủy từ Marketplace." />}
    {ready && <>
      <FinanceJobStatement job={job} payment={paymentError ? null : payment} tax={taxError ? null : tax} record={record} simulated={simulated} />
      <div className="finance-case-layout">
      <div className="finance-case-main"><MoneySpine stages={stages} contract />
        {record.error && <StatePanel kind="error" title="Chưa thể đối chiếu đầy đủ" body={record.error + ' Bằng chứng đã đọc được giữ lại; đối chiếu lại để cập nhật.'} />}
        {fundingError && <p role="alert">Không thể cập nhật funding: {fundingError}</p>}
        {paymentError && <p className="finance-inline-error" role="alert">Chưa thể cập nhật bằng chứng chi trả: {paymentError}</p>}
        {cancellation && <section className="cancellation-document" aria-label="Đề nghị hủy và hoàn tiền">
          <SectionHeading title={cancellationLabel(cancellation.cancellationStatus)} />
          <p>{cancellation.cancellationStatus === 'REQUESTED' ? 'Đề nghị đang chờ quyết định; công việc tiếp tục, chưa có hủy cuối cùng.' :
            cancellation.cancellationStatus === 'REJECTED' ? 'Đề nghị bị từ chối; công việc tiếp tục, không có hoàn tiền từ đề nghị này.' :
            cancellation.refundStatus === 'SUCCEEDED' ? cancellation.simulation ? financialCopy.refundSimulation : 'Marketplace đã xác nhận hoàn tiền. Không suy ra chuyển khoản ngân hàng từ bản ghi này.' :
            cancellation.cancellationStatus === 'REFUND_PENDING' ? 'Hoàn tiền chưa được xác nhận; chưa phải hủy và hoàn tiền cuối cùng.' : 'Hợp đồng đã hủy; không suy ra hoàn tiền nếu chưa có bản ghi.'}</p>
          <FactGrid facts={[{ label: 'Lý do', value: cancellation.reason || 'Chưa có lý do' },
            { label: 'Hoàn tiền', value: refundLabel(cancellation.refundStatus) },
            { label: 'Giá trị đề nghị', value: decimal(cancellation.amount) + ' ' + cancellation.currency },
            { label: 'Cập nhật đề nghị', value: stamp(cancellation.updatedAt) }]} />
        </section>}
        {settlement && <section className="settlement-document" aria-label="Xử lý sau release">
          <SectionHeading title="Dữ liệu hỗ trợ sau release" description="Lỗi ở chặng sau không đảo ngược release đã xác nhận." />
          <FactGrid facts={[
            ...(payment?.amountUsdcReceived != null && !paymentError ? [{ label: 'USDC theo bản ghi chi trả', value: usdc(payment.amountUsdcReceived) }] : []),
            ...(payment?.estimatedAmountVnd != null && !paymentError ? [{ label: 'VND dự kiến', value: vnd(payment.estimatedAmountVnd) }] : []),
            ...(payment?.payoutBankAccountNumber && !paymentError ? [{ label: 'Ngân hàng', value: (payment.payoutBankCode || '') + ' · ' + maskedBank(payment.payoutBankAccountNumber) }] : []),
          ]} />
        </section>}
        <EvidenceDisclosure summary="Tham chiếu và lỗi kỹ thuật">
          {technicalFacts.length ? <FactGrid facts={technicalFacts.map(fact => ({ label: fact.label, value: <code>{fact.value}</code> }))} />
            : <p className="metadata">Chưa có tham chiếu kỹ thuật.</p>}
        </EvidenceDisclosure>
      </div>
      <FinancialCaseSummary job={job} payment={paymentError ? null : payment} stages={stages} tax={taxError ? null : tax}
        refresh={refreshed} busy={busy} stale={!!record.error} simulated={simulated}>
      <section className={'finance-statement finance-primary finance-primary-' + contractFinanceTone(job, record)} aria-label="Tiền chính của hợp đồng">
        <SectionHeading title={contractFinanceLabel(job, record)} aside={settlement?.simulation || cancellation?.simulation || funding?.simulation ? 'Mô phỏng' : undefined} />
        <p>{cancellation?.refundStatus === 'SUCCEEDED' && cancellation.simulation ? financialCopy.refundSimulation :
          settlement?.moneyStatus === 'SUCCEEDED' && settlement.simulation ? financialCopy.releaseSimulation :
          'Funding, release và hoàn tiền là ba bản ghi riêng. Bản ghi mô phỏng không xác nhận chuyển khoản ngân hàng thật.'}</p>
        <FactGrid facts={[
          { label: 'Giá trị hợp đồng', value: decimal(job.contract!.amount) + ' ' + job.contract!.currency },
          { label: 'Funding', value: funding ? fundingLabel(funding.fundingStatus) : fundingError ? 'Chưa đọc được funding' : 'Chưa có bản ghi funding' },
          { label: 'Release', value: settlement ? settlementMoneyLabel(settlement.moneyStatus) : record.error ? 'Chưa xác minh release' : 'Chưa có bản ghi release' },
          { label: 'Hoàn tiền', value: cancellation ? refundLabel(cancellation.refundStatus) : record.error ? 'Chưa xác minh hoàn tiền' : refundLabel(null) },
        ]} />
      </section>
      <section className="finance-tax-callout" aria-label="Chứng từ thực tế">
        <div><h2>{tax ? tax.statusLabel || tax.status : taxError ? 'Chưa đọc được chứng từ' : settlement || releaseOwned(job) ? 'Chưa có chứng từ' : 'Chứng từ sau chi trả'}</h2>
          {taxError && <p role="alert">Chưa thể đọc chứng từ: {taxError}</p>}</div>
        <Link className="button button-secondary" to={tax ? '/finance/tax-records/' + encodeURIComponent(tax.id) : '/finance/tax-records'}>Xem chứng từ</Link>
      </section>
      </FinancialCaseSummary></div>
    </>}
  </>;
}

function EscrowFinanceEvidence({ job }: { job: Job }) {
  const contract = job.contract!;
  const [escrow, setEscrow] = useState<EscrowFundingView | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!contract.milestoneId) return;
    let active = true;
    setLoading(true);
    api.escrowFunding(contract.id, contract.milestoneId)
      .then(value => { if (active) { setEscrow(value); setError(''); } })
      .catch(cause => { if (active) setError(message(cause)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [contract.id, contract.milestoneId, tick]);
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') setTick(value => value + 1);
    }, 30000);
    return () => window.clearInterval(timer);
  }, []);
  const cluster = import.meta.env.VITE_SOLANA_CLUSTER;
  const explorer = (signature: string | null) => signature && (cluster === 'devnet' || cluster === 'mainnet-beta')
    ? 'https://explorer.solana.com/tx/' + encodeURIComponent(signature) + (cluster === 'devnet' ? '?cluster=devnet' : '') : null;
  const signature = (label: string, value: string | null) => <div><dt>{label}</dt><dd>{value ? <><code>{value}</code>{explorer(value) && <a href={explorer(value)!} target="_blank" rel="noopener noreferrer"> Xem Solana Explorer ↗</a>}</> : 'Chưa có giao dịch'}</dd></div>;
  return <section className="settlement-document" aria-label="Tài chính escrow Solana">
    <SectionHeading title="Escrow Solana" aside={cluster || 'Mạng chưa cấu hình'} />
    <p>Tiền được giữ trong vault Solana. Trạng thái release hoặc refund chỉ được ghi nhận sau khi chain xác nhận.</p>
    {loading && <p role="status">Đang đối soát escrow…</p>}
    {error && <p role="alert">{error}</p>}
    {escrow && <><FactGrid facts={[
      { label: 'Trạng thái chain', value: escrow.status },
      { label: 'Trạng thái quyết toán', value: escrow.settlementStatus },
      { label: 'Escrow PDA', value: escrow.escrowAddress },
      { label: 'Vault ATA', value: escrow.vaultAddress || 'Chưa xác minh' },
      { label: 'Số dư vault (base units)', value: escrow.vaultBalanceBaseUnits || 'Chưa xác minh' },
      { label: 'Token mint', value: escrow.mint || 'Chưa xác minh' },
      { label: 'Số tiền token', value: escrow.amountBaseUnits ? (Number(escrow.amountBaseUnits) / 1_000_000).toFixed(6) : 'Chưa xác minh' },
      { label: 'Hạn review on-chain', value: escrow.reviewDueAt ? new Date(Number(escrow.reviewDueAt) * 1000).toLocaleString('vi-VN') : 'Chưa có' },
    ]} /><dl className="reference-list">{signature('Funding', escrow.fundSignature)}
      {signature('Release', escrow.releaseSignature)}{signature('Refund', escrow.refundSignature)}
      {signature('Quyết định tranh chấp', escrow.resolutionSignature)}</dl></>}
    <button className="text-button" disabled={loading} onClick={() => setTick(value => value + 1)}>Đối soát lại</button>
  </section>;
}

function UnifiedFinanceEvidence({ job, user }: { job: Job; user: User }) {
  const contract = job.contract!;
  const [flow, setFlow] = useState<PaymentFlowTimeline | null>(null);
  const [build, setBuild] = useState<Awaited<ReturnType<typeof api.prepareUnifiedWithdrawal>> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // Kept apart from load errors so a timeline refresh does not hide why an action failed.
  const [actionError, setActionError] = useState('');
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!contract.milestoneId) return;
    let active = true;
    void api.paymentFlow(contract.id, contract.milestoneId).then(next => {
      if (active) { setFlow(next); setError(''); }
    }).catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [contract.id, contract.milestoneId, tick]);
  useEffect(() => {
    if (!flow || ['CONFIRMED', 'FAILED'].includes(flow.steps.find(s => s.kind === 'VND_PAYOUT')?.status || '')
      || ['CONFIRMED', 'FAILED'].includes(flow.steps.find(s => s.kind === 'USD_REFUND')?.status || '')) return;
    const timer = window.setTimeout(() => setTick(value => value + 1), 5000);
    return () => window.clearTimeout(timer);
  }, [flow, tick]);
  const status = (kind: string) => flow?.steps.find(step => step.kind === kind);
  const payout = status('USDC_RELEASE')?.status === 'CONFIRMED';
  const refund = status('USDC_REFUND')?.status === 'CONFIRMED';
  const stepNames: Record<string, string> = {
    USD_ORDER: 'Lệnh nạp USD', USD_RECEIVED: 'Đối tác xác nhận USD',
    CLIENT_USDC: 'USDC vào ví Client', ESCROW: 'USDC khóa trong vault',
    WORK_ACCEPTED: 'Công việc được duyệt', USDC_RELEASE: 'USDC đến ví Freelancer',
    USDC_REFUND: 'USDC hoàn về ví Client', WITHDRAWAL: 'USDC gửi đến treasury',
    VND_PAYOUT: 'VND chi cho Freelancer', PLATFORM_FEE: 'Phí FreelaX',
    USD_REFUND: 'USD hoàn về Client',
  };
  const statusNames: Record<string, string> = {
    CONFIRMED: 'Đã xác nhận', PENDING: 'Đang chờ', PROCESSING: 'Đang xử lý',
    UNKNOWN: 'Chưa rõ · đang đối soát', FAILED: 'Thất bại',
    NOT_STARTED: 'Chưa bắt đầu', AWAITING_CLIENT: 'Chờ Client xác nhận',
  };
  const visibleSteps = flow?.steps.filter(step => payout
    ? !['USDC_REFUND', 'USD_REFUND'].includes(step.kind)
    : refund ? !['WORK_ACCEPTED', 'USDC_RELEASE', 'VND_PAYOUT', 'PLATFORM_FEE'].includes(step.kind)
      : true) || [];
  const permitted = payout ? user.id === job.freelancerId : refund && user.id === job.clientUserId;
  const withdrawal = status('WITHDRAWAL');
  async function prepare() {
    if (!contract.milestoneId || busy) return;
    setBusy(true); setActionError('');
    try { setBuild(await api.prepareUnifiedWithdrawal(contract.id, contract.milestoneId)); }
    catch (cause) { setActionError(message(cause)); setTick(value => value + 1); }
    finally { setBusy(false); }
  }
  async function sign() {
    if (!contract.milestoneId || !build || busy) return;
    setBusy(true); setActionError('');
    try {
      const connected = await connectSolanaWallet();
      if (connected.address !== build.wallet) throw new Error('Ví đang kết nối không khớp ví đã đăng ký.');
      const signed = await signEscrowTransaction(connected.wallet, build.transactionBase64);
      setFlow(await api.submitUnifiedWithdrawal(contract.id, contract.milestoneId,
        build.buildSessionId, signed));
      setBuild(null); setTick(value => value + 1);
    } catch (cause) { setActionError(message(cause)); setTick(value => value + 1); }
    finally { setBusy(false); }
  }
  return <section className="settlement-document" aria-label="Luồng tài chính thống nhất">
    <SectionHeading title="Luồng thanh toán của Job" aside="Mô phỏng" />
    {error && <p role="alert" className="form-error">{error}</p>}
    {actionError && <p role="alert" className="form-error">{actionError}</p>}
    {!flow && <p role="status">Đang đọc timeline thanh toán…</p>}
    {flow && <>
      <p>Mã luồng: <code>{flow.paymentFlowId}</code> · {flow.grossUsd} USD → {flow.escrowUsdc} USDC.</p>
      <p>Phí Freelancer chịu: {flow.platformFeeUsd} USD tương đương USDC. Job hoàn thành sau release; VND chỉ ghi đã chi khi sao kê đối tác xác nhận.</p>
      <dl className="reference-list">{visibleSteps.map(step => <div key={step.kind}>
        <dt>{stepNames[step.kind] || step.kind}</dt><dd>
          {statusNames[step.status] || step.status}
          {step.amount != null && step.currency ? ` · ${step.amount} ${step.currency}` : ''}
          {(step.reference || step.transactionSignature || step.evidenceSource) && <details>
            <summary>Reference và nguồn xác nhận</summary>
            {step.reference && <p>Reference: <code>{step.reference}</code></p>}
            {step.transactionSignature && <p>Giao dịch: <code>{step.transactionSignature}</code></p>}
            {step.evidenceSource && <p>Nguồn: {step.evidenceSource}</p>}
          </details>}
        </dd></div>)}</dl>
      {withdrawal?.vndRate && <p>Quote off-ramp: 1 USDC = {withdrawal.vndRate} VND · phí {withdrawal.feeUsdc} USDC · Freelancer dự kiến nhận {withdrawal.payoutVnd} VND · hết hạn {stamp(withdrawal.quoteExpiresAt)}.</p>}
      {permitted && withdrawal?.status !== 'CONFIRMED' && !withdrawal?.transactionSignature && !build &&
        <button className="button" disabled={busy} onClick={() => void prepare()}>
          {payout ? 'Chuẩn bị đổi USDC sang VND' : 'Chuẩn bị hoàn USD sau khi gửi USDC về treasury'}
        </button>}
      {build && <div className="approval-confirm" role="group" aria-label="Xác nhận withdrawal">
        <p>Ví {build.wallet} sẽ ký chuyển {build.grossUsdc} USDC tới treasury mô phỏng. {build.kind === 'PAYOUT'
          ? `Phí ${build.feeUsdc} USDC; dự kiến nhận ${build.payoutVnd} VND.`
          : 'USD chỉ được ghi hoàn sau khi withdrawal và sao kê USD được xác nhận.'}</p>
        <p>Quote hết hạn: {stamp(build.quoteExpiresAt)}.</p>
        <button className="button" disabled={busy} onClick={() => void sign()}>Ký withdrawal bằng ví</button>
        <button className="button button-secondary" disabled={busy} onClick={() => setBuild(null)}>Hủy</button>
      </div>}
      <button className="text-button" disabled={busy} onClick={() => setTick(value => value + 1)}>Đối soát lại</button>
    </>}
  </section>;
}

function JobEvidence({ jobId, user }: { jobId: string; user: User }) {
  const [job, setJob] = useState<Job | null>(null);
  const [payment, setPayment] = useState<JobPaymentStatus | null>(null);
  const [tax, setTax] = useState<TaxRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [jobError, setJobError] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [taxError, setTaxError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [pollTick, setPollTick] = useState(0);

  useEffect(() => {
    let active = true;
    setJobError('');
    setPaymentError('');
    setTaxError('');
    api.job(jobId).then(async currentJob => {
      if (!active) return;
      setJob(currentJob);
      if (currentJob.contract) {
        setPayment(null); setTax(null); setLoading(false); return;
      }
      const results = await Promise.allSettled([Promise.resolve(currentJob), api.paymentStatus(jobId), api.taxRecordForJob(jobId)]);
      if (!active) return;
      const [jobResult, paymentResult, taxResult] = results;
      if (jobResult.status === 'fulfilled') setJob(jobResult.value);
      else setJobError(message(jobResult.reason));
      if (paymentResult.status === 'fulfilled') setPayment(paymentResult.value);
      else { setPayment(null); setPaymentError(message(paymentResult.reason)); }
      if (taxResult.status === 'fulfilled') setTax(taxResult.value);
      else if (missingTax(taxResult.reason)) setTax(null);
      else { setTax(null); setTaxError(message(taxResult.reason)); }
      setLoading(false);
    }).catch(cause => { if (active) { setJobError(message(cause)); setLoading(false); } });
    return () => { active = false; };
  }, [jobId, attempt]);

  useEffect(() => {
    if (!payment || paymentError || paymentTerminal(payment)) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      if (document.visibilityState === 'hidden') {
        if (active) setPollTick(value => value + 1);
        return;
      }
      const [nextPayment, nextTax] = await Promise.allSettled([api.paymentStatus(jobId), api.taxRecordForJob(jobId)]);
      if (!active) return;
      if (nextPayment.status === 'fulfilled') setPayment(nextPayment.value);
      else { setPayment(null); setPaymentError(message(nextPayment.reason)); }
      if (nextTax.status === 'fulfilled') { setTax(nextTax.value); setTaxError(''); }
      else if (missingTax(nextTax.reason)) { setTax(null); setTaxError(''); }
      else { setTax(null); setTaxError(message(nextTax.reason)); }
      setPollTick(value => value + 1);
    }, POLL_MS);
    return () => { active = false; window.clearTimeout(timer); };
  }, [payment, paymentError, jobId, pollTick]);

  const retry = () => setAttempt(value => value + 1);
  if (loading && !job) return <StatePanel kind="loading" title="Đang tải bằng chứng thanh toán"
    body="Đang đối chiếu trạng thái tài chính của công việc từ Marketplace." />;
  if (!job || jobError) return <StatePanel kind="error" title="Không thể tải công việc" body={jobError}
    action={{ label: 'Thử lại', onClick: retry }} />;
  const participant = user.userType === 'CLIENT' ? job.clientUserId === user.id : job.freelancerId === user.id;
  if (!participant) return <StatePanel kind="error" title="Không có quyền xem"
    body="Bằng chứng tài chính chỉ dành cho người tham gia công việc này." />;
  const stages = payment ? legacyMoneyStages(job, payment, tax) : [];
  if (taxError && stages.length) stages[4] = { ...stages[4], tone: 'error', status: 'Chưa đọc được chứng từ', error: taxError };
  return <article className="finance-detail-page">
    <PageHeading eyebrow={user.userType === 'CLIENT' ? 'Client / Thanh toán' : 'Freelancer / Thu nhập'}
      title={<><span className="finance-detail-title-start">Chi tiết <RoughBurst className="finance-detail-rays finance-detail-rays--left" size={35} accent="vermilion" seedKey="finance-detail:left" /></span>
        <span className="finance-detail-title-emphasis">hồ sơ tiền.<RoughUnderline size={230} seedKey="finance-detail:underline" />
          <RoughBurst className="finance-detail-rays finance-detail-rays--right" size={35} accent="ink" seedKey="finance-detail:right" /></span></>}
      description={user.userType === 'FREELANCER'
        ? 'Theo dõi release, chi trả và chứng từ của công việc này.'
        : 'Theo dõi dòng tiền của một công việc, từ thanh toán đến chứng từ thuế.'} />
    <div className="finance-detail-back"><Link to="/finance"><ArrowLeft size={22} aria-hidden="true" />
      {user.userType === 'FREELANCER' ? 'Lịch sử thu nhập' : 'Danh sách thanh toán'}</Link></div>
    {job.contract?.paymentRail === 'SOLANA_ESCROW' && <EscrowFinanceEvidence job={job} />}
    {job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT' && <UnifiedFinanceEvidence job={job} user={user} />}
    {job.contract && job.contract.paymentRail !== 'SOLANA_ESCROW'
      && job.contract.paymentRail !== 'UNIFIED_USDC_PAYOUT' && <ContractEvidence initialJob={job} />}
    {!payment && !job.contract && <StatePanel kind="error" title="Chưa thể đọc trạng thái thanh toán"
      body={paymentError || 'Marketplace chưa trả dữ liệu.'} action={{ label: 'Tải lại', onClick: retry }} />}
    {payment && <>
      <FinanceJobStatement job={job} payment={payment} tax={tax} />
      <div className="finance-case-layout"><div className="finance-case-main">
      <MoneySpine stages={stages} />
      {taxError && <p className="finance-inline-error" role="alert">Chưa thể đọc chứng từ: {taxError}</p>}
      <TechnicalEvidence payment={payment} tax={tax} />
      <details className="finance-support" aria-label="Chi tiết chi trả">
        <summary>Dữ liệu chi trả & tỷ giá</summary>
        <dl>
          <div><dt>Checkout Client</dt><dd>{checkoutLabel(payment.checkoutOrderStatus)}</dd></div>
          <div><dt>Thanh toán on-chain</dt><dd>{clientPaymentLabel(payment.clientPaymentStatus)}
            {payment.network?.toLowerCase() === 'devnet' && ' · DEVNET'}</dd></div>
          <div><dt>Chi trả VND</dt><dd>{offRampLabel(payment.offRampStatus)}</dd></div>
          <div><dt>VND trước phí off-ramp</dt><dd>{vnd(payment.amountVndBeforeOffRampFee)}</dd></div>
          <div><dt>Phí off-ramp</dt><dd>{vnd(payment.offRampFeeVnd)}</dd></div>
          <div><dt>Nguồn tỷ giá USDC/VND</dt><dd>{rateSource(payment.usdcToVndRateSource)}
            {payment.usdcToVndRate != null && ' · ' + decimal(payment.usdcToVndRate, 4)}</dd></div>
          <div><dt>Điểm đến ngân hàng</dt><dd>{payment.payoutBankCode || 'Chưa có ngân hàng'} · {maskedBank(payment.payoutBankAccountNumber)}</dd></div>
          <div><dt>Thu nhập chịu thuế</dt><dd>{vnd(payment.taxableAmountVnd)}</dd></div>
          <div><dt>Xuất chứng từ theo job</dt><dd>{exportLabel(payment.taxExportStatus)}</dd></div>
          <div><dt>Ngày mô phỏng chi trả</dt><dd>{stamp(payment.simulatedPayoutAt)}</dd></div>
          <div><dt>Ngày hoàn tất bản ghi</dt><dd>{stamp(payment.offRampCompletedAt)}</dd></div>
        </dl>
      </details>
      </div><FinancialCaseSummary job={job} payment={payment} stages={stages} tax={tax} refresh={retry}>
      <section className="finance-tax-callout" aria-label="Chứng từ thuế">
        <div><span className="finance-category">Chứng từ thuế</span>
          <h2>{tax ? tax.statusLabel || tax.status : taxError ? 'Chưa đọc được chứng từ' : 'Chưa có chứng từ'}</h2>
          <p>{tax ? 'Trạng thái chứng từ được trả về từ Marketplace/MISA.' : taxError ? 'Marketplace chưa trả được bằng chứng chứng từ.' :
            'Không có bản ghi chứng từ cho công việc này; điều đó không xác nhận miễn thuế.'}</p></div>
      </section>
      </FinancialCaseSummary></div>
    </>}
  </article>;
}

export function FinanceHome({ user }: { user: User }) {
  const [params] = useSearchParams();
  const jobId = params.get('jobId');
  return jobId ? <JobEvidence key={jobId} jobId={jobId} user={user} /> : <FinanceList user={user} />;
}

function TaxStatus({ record }: { record: TaxRecord }) {
  const { tone, label } = taxPresentation(record);
  const Icon = tone === 'accepted' ? CircleCheck : tone === 'error' || tone === 'attention' ? CircleAlert : tone === 'closed' ? FileText : Clock3;
  return <span className={'tax-status tax-tone-' + tone}><Icon size={20} aria-hidden="true" />{label}</span>;
}

function TaxHeading({ detail = false }: { detail?: boolean }) {
  return <PageHeading eyebrow="Tài chính / Chứng từ thuế"
    title={<>{detail ? 'Hồ sơ' : 'Chứng từ'} <span className="tax-title-emphasis">{detail ? 'chứng từ.' : 'theo công việc.'}<RoughUnderline /></span></>}
    description={detail ? 'Đối chiếu số liệu thuế, trạng thái chứng từ và bằng chứng từ Marketplace.'
      : 'Theo dõi từng hồ sơ thuế. Kết quả xuất sang MISA và trạng thái cơ quan thuế được ghi nhận riêng.'} />;
}

export function TaxRecordsPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<TaxRecord> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.taxRecords(page).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(message(cause)); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, attempt]);
  return <article className="tax-page tax-ledger-page">
    <TaxHeading />
    <FinanceNav />
    {loading && <StatePanel kind="loading" title="Đang tải chứng từ" body="Marketplace đang trả danh sách chứng từ của tài khoản." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải chứng từ" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      {result.data.length === 0 ? <StatePanel kind="empty" title="Chưa có chứng từ trên trang này"
        body="Chưa có bản ghi chứng từ trên trang này. Điều đó không xác nhận công việc được miễn thuế." /> :
        <section aria-label="Sổ chứng từ thuế"><header className="tax-ledger-heading"><h2>Sổ chứng từ thuế</h2><span>{result.data.length} hồ sơ trên trang này</span></header>
        <div className="tax-ledger-columns" aria-hidden="true"><span>Công việc / chứng từ</span><span>Trạng thái chứng từ</span><span>Thu nhập chịu thuế</span><span>Hồ sơ</span></div>
        <div className="tax-list">{result.data.map(record => <article key={record.id} className={'tax-list-row tax-tone-' + taxPresentation(record).tone}>
          <div className="tax-ledger-identity"><span className="tax-document-mark" aria-hidden="true"><FileText size={42} /></span><div><span className="finance-category">Hồ sơ thuế / công việc</span>
            <h2><Link to={'/finance/tax-records/' + record.id}>{record.jobTitle || 'Công việc ' + record.jobId.slice(0, 8)}</Link></h2>
            <span className="tax-ledger-date">Tạo {date(record.createdAt)}</span>
            {record.certificateNumber && <span className="tax-certificate-number">Số chứng từ: {record.certificateNumber}</span>}</div></div>
          <div className="tax-ledger-state"><TaxStatus record={record} /></div>
          <div className="tax-ledger-amount"><span className="cell-label">Thu nhập chịu thuế</span><strong>{vnd(record.taxableIncomeVnd)}</strong></div>
          <Link className="tax-record-link" to={'/finance/tax-records/' + record.id}>Xem chi tiết <ArrowUpRight size={20} aria-hidden="true" /></Link>
        </article>)}</div></section>}
      <nav className="pagination" aria-label="Phân trang chứng từ"><span>{result.totalElements} chứng từ · Trang {result.totalPages ? page + 1 : 0}/{result.totalPages}</span>
        <div><button className="button button-secondary" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trang trước</button>
          <button className="button button-secondary" disabled={page >= result.totalPages - 1} onClick={() => setPage(page + 1)}>Trang sau</button></div></nav>
    </>}
  </article>;
}

export function TaxRecordDetail() {
  const { taxRecordId } = useParams();
  const [record, setRecord] = useState<TaxRecord | null>(null);
  const [payment, setPayment] = useState<JobPaymentStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const busyRef = useRef(false);

  useEffect(() => {
    if (!taxRecordId) return;
    let active = true;
    setLoading(true);
    setError('');
    setPayment(null);
    api.taxRecord(taxRecordId).then(async data => {
      if (!active) return;
      setRecord(data);
      try {
        const result = await api.paymentStatus(data.jobId);
        if (active) { setPayment(result); setPaymentError(''); }
      } catch (cause) {
        if (active) { setPayment(null); setPaymentError(message(cause)); }
      }
      if (active) setLoading(false);
    }, cause => { if (active) { setRecord(null); setError(message(cause)); setLoading(false); } });
    return () => { active = false; };
  }, [taxRecordId, attempt]);

  const act = useCallback(async (operation: 'sync' | 'retry') => {
    if (!record || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setActionError('');
    setNotice('');
    try {
      const updated = operation === 'sync' ? await api.syncTaxRecord(record.id) : await api.retryTaxExport(record.id);
      setRecord(updated);
      setNotice(operation === 'sync' ? 'Đã đồng bộ trạng thái từ Marketplace.' : 'Đã gửi yêu cầu lập lại chứng từ.');
    } catch (cause) {
      setActionError(message(cause));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, [record]);

  const download = useCallback(async (format: 'pdf' | 'xml') => {
    if (!record || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setActionError('');
    try {
      const blob = await api.downloadTaxFile(record.id, format);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'chung-tu-khau-tru-' + record.id + '.' + format;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 30000);
    } catch (cause) {
      setActionError(message(cause));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, [record]);

  if (loading && !record) return <StatePanel kind="loading" title="Đang tải chứng từ" body="Đang đối chiếu bản ghi thuế từ Marketplace." />;
  if (!record) return <StatePanel kind="error" title="Không thể tải chứng từ" body={error || 'Không có bản ghi.'}
    action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />;
  const payoutComplete = payment?.offRampStatus === 'COMPLETED';
  const canSync = payoutComplete && !!record.misaCertificateId && syncableTaxStatuses.has(record.status);
  const canRetry = payoutComplete && record.status === 'EXPORT_FAILED';
  return <article className="tax-page tax-case-page">
    <TaxHeading detail />
    <FinanceNav />
    <div className="finance-back"><Link to="/finance/tax-records">← Danh sách chứng từ</Link>
      <Link to={financePath(record.jobId)}>Bằng chứng thanh toán</Link></div>
    <section className={'tax-statement tax-tone-' + taxPresentation(record).tone} aria-label="Tình trạng chứng từ">
      <div className="tax-statement-identity"><span className="tax-case-document" aria-hidden="true"><FileText size={68} /></span><div><span className="finance-category">Hồ sơ thuế / công việc</span>
      <h2>{record.jobTitle || 'Công việc ' + record.jobId.slice(0, 8)}</h2>
      <TaxStatus record={record} /></div></div>
      {payment?.simulation === true && <p className="simulation-mark">Mô phỏng · luồng chi trả; chứng từ phản ánh dữ liệu hệ thống.</p>}
      <div className="tax-amounts"><div><span>Thu nhập chịu thuế</span><strong>{vnd(record.taxableIncomeVnd)}</strong></div>
        <div><span>Thuế đã khấu trừ</span><strong>{vnd(record.taxWithheldVnd)}</strong></div></div>
    </section>
    <div className="tax-case-layout"><section className="tax-fact-ledger" aria-label="Số liệu hồ sơ thuế"><h2>Số liệu và mốc chứng từ</h2><p>Thu nhập chịu thuế và thuế đã khấu trừ là hai giá trị riêng. Tỷ giá dưới đây dùng cho bản ghi thuế.</p>
      <dl className="tax-facts">
        <div><dt>Giá trị công việc</dt><dd>{record.amountUsd == null ? '—' : money(Number(record.amountUsd))}</dd></div>
        <div><dt>Tỷ giá USD/VND dùng cho thuế</dt><dd>{record.usdToVndRate == null ? '—' : decimal(record.usdToVndRate, 4)}
          {' · '}{rateSource(record.rateSource)}</dd></div>
        <div><dt>Quan sát tỷ giá</dt><dd>{stamp(record.rateObservedAt)}</dd></div>
        <div><dt>Ngày phát hành</dt><dd>{stamp(record.issuedAt)}</dd></div>
        <div><dt>Ngày gửi cơ quan thuế</dt><dd>{stamp(record.submittedAt)}</dd></div>
        <div><dt>Đồng bộ gần nhất</dt><dd>{stamp(record.lastSyncedAt)}</dd></div>
      </dl>
    </section>
    <section className={'tax-actions tax-tone-' + taxPresentation(record).tone} aria-label="Tác vụ chứng từ">
      <div><h2><FileText size={26} aria-hidden="true" />Chứng từ và trạng thái</h2>
        <p>Mã chứng từ không xác nhận cơ quan thuế đã chấp nhận. Trạng thái trên hồ sơ là căn cứ đối chiếu.</p></div>
      {paymentError && <p className="finance-inline-error" role="alert">Chưa xác minh được trạng thái chi trả; thao tác đồng bộ/lập lại tạm khóa: {paymentError}</p>}
      {loading && <p role="status">Đang đối chiếu trạng thái chi trả từ Marketplace.</p>}
      <div className="tax-action-buttons">
        {canSync && <button className="button button-secondary" type="button" disabled={busy || loading} onClick={() => void act('sync')}>Đồng bộ trạng thái</button>}
        {canRetry && <button className="button button-secondary" type="button" disabled={busy || loading} onClick={() => void act('retry')}>Lập lại chứng từ</button>}
        {record.misaCertificateId && <>
          <button className="button" type="button" disabled={busy} onClick={() => void download('pdf')}>Tải PDF</button>
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => void download('xml')}>Tải XML</button>
        </>}
        {!canSync && !canRetry && !record.misaCertificateId && <span>Chưa có tệp chứng từ để tải.</span>}
      </div>
      <p className="tax-action-context">Đồng bộ khi chứng từ có mã MISA và chi trả đã hoàn tất; lập lại chỉ khi xuất chứng từ thất bại.</p>
      {notice && <p className="lifecycle-success" role="status">{notice}</p>}
      {actionError && <p className="finance-inline-error" role="alert">{actionError}</p>}
    </section></div>
    <details className="technical-evidence"><summary>Chi tiết kỹ thuật chứng từ</summary>
      <dl className="tax-technical">
        {([['Tax record ID', record.id], ['Job ID', record.jobId], ['MISA certificate ID', record.misaCertificateId],
          ['MISA payout ID', record.misaPayoutTransactionId], ['Số chứng từ', record.certificateNumber],
          ['Ký hiệu', record.certificateSymbol], ['Mã tra cứu', record.lookupCode],
          ['Transaction reference', record.transactionReference], ['Submission ID', record.submissionId],
          ['Tax authority reference', record.taxAuthorityReference]] as [string, string | null][]).filter(([, value]) => !!value)
          .map(([label, value]) => <div key={label}><dt>{label}</dt><dd><code>{value}</code></dd></div>)}
      </dl>
    </details>
    <button className="text-button finance-refresh" type="button" disabled={busy || loading} onClick={() => setAttempt(value => value + 1)}><RefreshCw size={18} aria-hidden="true" />Làm mới từ Marketplace</button>
  </article>;
}
