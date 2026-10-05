import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ApiError, api } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import { Pagination } from './Jobs';
import { checkoutLabel, clientPaymentLabel, decimal, exportLabel, maskedBank, offRampLabel,
  paymentStages, paymentTerminal, rateSource, safeExplorerUrl, syncableTaxStatuses, usdc, vnd,
  financePath, contractFinanceLabel, contractFinanceNeedsRefresh, settlementMoneyLabel, settlementStageLabel, releaseOwned } from './financeStatus';
import { cancellationLabel, date, fundingLabel, money, refundLabel } from './status';
import type { ContractFinance, EvidenceStage } from './financeStatus';
import type { FundingResponse, Job, JobPaymentStatus, Page, TaxRecord, User } from './types';

const POLL_MS = 15000;
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'Không thể tải dữ liệu từ Marketplace.';
const missingTax = (cause: unknown) => cause instanceof ApiError && (cause.status === 404 || cause.code === 4010);
const stamp = (value: string | null | undefined) => value ? value.slice(0, 16).replace('T', ' · ') : '—';

function FinanceNav() {
  return <nav className="finance-nav" aria-label="Khu vực tài chính">
    <Link to="/finance">Theo công việc</Link>
    <Link to="/finance/tax-records">Chứng từ thuế</Link>
  </nav>;
}

type PaymentEntry = { data: JobPaymentStatus | null; error: string; contract?: ContractFinance };

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
  return <>
    <PageHeading eyebrow={freelancer ? 'Freelancer / Thu nhập' : 'Client / Thanh toán'}
      title={freelancer ? 'Thu nhập theo từng công việc.' : 'Thanh toán theo từng công việc.'}
      description="Funding, release, hoàn tiền và chứng từ theo công việc; mỗi chặng có bằng chứng riêng từ Marketplace."
      aside="Không hiển thị số dư ví hoặc lệnh chuyển tiền" />
    <FinanceNav />
    {loading && <StatePanel kind="loading" title="Đang tải hồ sơ tài chính" body="Đang đối chiếu công việc và trạng thái thanh toán." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải công việc" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      <div className="finance-list-intro"><strong>{financial.length} hồ sơ tài chính trên trang này</strong>
        <span>Danh sách công việc được phân trang bởi Marketplace.</span></div>
      {financial.length === 0 ? <StatePanel kind="empty" title="Chưa có hồ sơ tài chính trên trang này"
        body="Chuyển trang để xem các công việc khác. Hồ sơ hợp đồng đang xử lý tiền cũng xuất hiện tại đây." /> :
        <div className="finance-job-list">{financial.map(job => {
          const entry = payments[job.id];
          const status = entry?.data;
          return <article className="finance-job-row" key={job.id}>
            <div><span className="finance-category">{job.contract ? 'Hồ sơ hợp đồng' : 'Công việc hoàn thành'}</span>
              <h2><Link to={financePath(job.id)}>{job.title}</Link></h2>
              <p>Giá trị công việc: {money(job.budgetUsd)}</p></div>
            <div><span className="cell-label">{job.contract ? 'Release / hoàn tiền' : 'Thanh toán Client'}</span>
              <strong>{job.contract ? contractFinanceLabel(job, entry?.contract) : status ? checkoutLabel(status.checkoutOrderStatus) : entry?.error || 'Đang tải…'}</strong>
              {entry?.contract?.error && <span role="alert">{entry.contract.error}</span>}</div>
            <div><span className="cell-label">USDC / VND</span>
              <strong>{status?.amountUsdcReceived == null ? 'Chưa có số USDC' : usdc(status.amountUsdcReceived)}</strong>
              <span>{status?.estimatedAmountVnd == null ? 'Chưa có VND dự kiến' : vnd(status.estimatedAmountVnd) + ' dự kiến'}</span></div>
            <div><span className="cell-label">Chi trả</span>
              <strong>{entry?.contract?.settlement ? settlementStageLabel(entry.contract.settlement.offRampStatus) : status ? offRampLabel(status.offRampStatus) : 'Chưa có dữ liệu chi trả'}</strong>
              {(status?.simulation || entry?.contract?.settlement?.simulation || entry?.contract?.cancellation?.simulation) && <b className="simulation-mark">Mô phỏng</b>}
              <Link className="finance-row-link" to={financePath(job.id)}>Xem bằng chứng →</Link></div>
          </article>;
        })}</div>}
      <Pagination page={result} onPage={setPage} />
      <button className="text-button finance-refresh" type="button" onClick={() => setAttempt(value => value + 1)}>Làm mới từ Marketplace</button>
    </>}
  </>;
}

function StageRail({ stages }: { stages: EvidenceStage[] }) {
  return <ol className="finance-stages" aria-label="Năm chặng tài chính">{stages.map((stage, index) =>
    <li key={stage.key} className={'finance-stage stage-' + stage.tone}>
      <span className="stage-number">0{index + 1}</span>
      <div><h3>{stage.title}</h3><strong>{stage.status}</strong><p>{stage.note}</p>
        {stage.error && <p className="finance-stage-error" role="alert">{stage.error}</p>}</div>
    </li>)}</ol>;
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
  return <>
    {!ready && <StatePanel kind="loading" title="Đang đối chiếu hồ sơ hợp đồng" body="Đọc funding, release và đề nghị hủy từ Marketplace." />}
    {ready && <>
      <section className="finance-statement" aria-label="Tiền chính của hợp đồng">
        <SectionHeading title={contractFinanceLabel(job, record)} aside={settlement?.simulation || cancellation?.simulation || funding?.simulation ? 'Mô phỏng' : undefined} />
        <p>Funding, release và hoàn tiền là ba bản ghi riêng. Bản ghi release/hoàn tiền mô phỏng không xác nhận chuyển khoản ngân hàng thật.</p>
        <FactGrid facts={[
          { label: 'Giá trị hợp đồng', value: decimal(job.contract!.amount) + ' ' + job.contract!.currency },
          { label: 'Funding', value: funding ? fundingLabel(funding.fundingStatus) : fundingError ? 'Chưa đọc được funding' : 'Chưa có bản ghi funding' },
          { label: 'Release', value: settlement ? settlementMoneyLabel(settlement.moneyStatus) : 'Chưa có bản ghi release' },
          { label: 'Hoàn tiền', value: refundLabel(cancellation?.refundStatus ?? null) },
        ]} />
      </section>
      {record.error && <StatePanel kind="error" title="Chưa thể đối chiếu đầy đủ" body={record.error} action={{ label: 'Thử lại', onClick: refreshed }} />}
      {fundingError && <p role="alert">Không thể cập nhật funding: {fundingError}</p>}
      {cancellation && <section className="cancellation-document" aria-label="Đề nghị hủy và hoàn tiền">
        <SectionHeading title={cancellationLabel(cancellation.cancellationStatus)} />
        <p>{cancellation.cancellationStatus === 'REQUESTED' ? 'Đề nghị đang chờ quyết định; công việc tiếp tục, chưa có hủy cuối cùng.' :
          cancellation.cancellationStatus === 'REJECTED' ? 'Đề nghị bị từ chối; công việc tiếp tục, không có hoàn tiền từ đề nghị này.' :
          cancellation.refundStatus === 'SUCCEEDED' ? 'Đã xác nhận bản ghi hoàn tiền. Không xác nhận tiền đã về ngân hàng thật.' :
          cancellation.cancellationStatus === 'REFUND_PENDING' ? 'Hoàn tiền chưa được xác nhận; chưa phải hủy và hoàn tiền cuối cùng.' : 'Hợp đồng đã hủy; không suy ra hoàn tiền nếu chưa có bản ghi.'}</p>
        <FactGrid facts={[
          { label: 'Lý do', value: cancellation.reason || 'Chưa có lý do' },
          { label: 'Hoàn tiền', value: refundLabel(cancellation.refundStatus) },
          { label: 'Giá trị đề nghị', value: decimal(cancellation.amount) + ' ' + cancellation.currency },
          { label: 'Cập nhật', value: stamp(cancellation.updatedAt) },
        ]} />
      </section>}
      {settlement && <section className="settlement-document" aria-label="Xử lý sau release">
        <SectionHeading title="Bằng chứng sau release" description="Lỗi ở chặng sau không đảo ngược release đã xác nhận." />
        <FactGrid facts={[
          { label: 'On-chain', value: settlementStageLabel(settlement.onChainStatus) },
          { label: 'Chi trả VND', value: settlementStageLabel(settlement.offRampStatus) },
          { label: 'Lập / khôi phục chứng từ', value: settlementStageLabel(settlement.taxStatus) },
          { label: 'USDC theo bản ghi chi trả', value: payment?.amountUsdcReceived == null ? 'Chưa có dữ liệu' : usdc(payment.amountUsdcReceived) },
          { label: 'VND dự kiến', value: payment?.estimatedAmountVnd == null ? 'Chưa có ước tính' : vnd(payment.estimatedAmountVnd) },
          { label: 'Ngân hàng', value: (payment?.payoutBankCode || 'Chưa có ngân hàng') + ' · ' + maskedBank(payment?.payoutBankAccountNumber) },
        ]} />
        <p>Chặng thuế đã xác nhận chỉ chứng minh lập/khôi phục chứng từ; trạng thái cơ quan thuế lấy từ chứng từ thực tế bên dưới.</p>
      </section>}
      <section className="finance-tax-callout" aria-label="Chứng từ thực tế">
        <div><h2>{tax ? tax.statusLabel || tax.status : 'Chưa có chứng từ'}</h2>
          {taxError && <p role="alert">Chưa thể đọc chứng từ: {taxError}</p>}</div>
        <Link className="button button-secondary" to={tax ? '/finance/tax-records/' + encodeURIComponent(tax.id) : '/finance/tax-records'}>Xem chứng từ</Link>
      </section>
      <EvidenceDisclosure summary="Tham chiếu và lỗi kỹ thuật">
        <FactGrid facts={[
          { label: 'Release reference', value: settlement?.releaseReference || '—' },
          { label: 'Refund reference', value: cancellation?.refundReference || '—' },
          { label: 'On-chain reference', value: settlement?.onChainReference || '—' },
          { label: 'Off-ramp reference', value: settlement?.offRampReference || '—' },
          { label: 'Tax reference', value: settlement?.taxReference || '—' },
          { label: 'Lỗi release', value: settlement?.lastError || '—' },
          { label: 'Lỗi on-chain', value: settlement?.onChainError || '—' },
          { label: 'Lỗi VND', value: settlement?.offRampError || '—' },
          { label: 'Lỗi thuế', value: settlement?.taxError || '—' },
          { label: 'Lỗi hoàn tiền', value: cancellation?.lastError || '—' },
          { label: 'Lỗi bản ghi chi trả', value: paymentError || '—' },
        ]} />
      </EvidenceDisclosure>
      <ActionGroup><Link className="button button-secondary" to={'/work/' + encodeURIComponent(job.id)}>Xem hồ sơ công việc</Link>
        <button className="text-button" type="button" disabled={busy} onClick={refreshed}>{busy ? 'Đang đối chiếu…' : 'Làm mới từ Marketplace'}</button></ActionGroup>
    </>}
  </>;
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
      else setPaymentError(message(paymentResult.reason));
      if (taxResult.status === 'fulfilled') setTax(taxResult.value);
      else if (missingTax(taxResult.reason)) setTax(null);
      else setTaxError(message(taxResult.reason));
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
      else setPaymentError(message(nextPayment.reason));
      if (nextTax.status === 'fulfilled') { setTax(nextTax.value); setTaxError(''); }
      else if (missingTax(nextTax.reason)) setTax(null);
      else setTaxError(message(nextTax.reason));
      setPollTick(value => value + 1);
    }, POLL_MS);
    return () => { active = false; window.clearTimeout(timer); };
  }, [payment, paymentError, jobId, pollTick]);

  const retry = () => setAttempt(value => value + 1);
  if (loading && !job) return <StatePanel kind="loading" title="Đang tải bằng chứng thanh toán"
    body="Đang đối chiếu trạng thái tài chính của công việc từ Marketplace." />;
  if (!job) return <StatePanel kind="error" title="Không thể tải công việc" body={jobError}
    action={{ label: 'Thử lại', onClick: retry }} />;
  const participant = user.userType === 'CLIENT' ? job.clientUserId === user.id : job.freelancerId === user.id;
  if (!participant) return <StatePanel kind="error" title="Không có quyền xem"
    body="Bằng chứng tài chính chỉ dành cho người tham gia công việc này." />;
  return <>
    <PageHeading eyebrow={(user.userType === 'CLIENT' ? 'Client / Thanh toán' : 'Freelancer / Thu nhập') + ' / Công việc'}
      title={job.title} description="Bằng chứng tài chính theo từng chặng, cập nhật từ Marketplace."
      aside={'Giá trị công việc ' + money(job.budgetUsd)} />
    <FinanceNav />
    <div className="finance-back"><Link to="/finance">← Danh sách công việc</Link><Link to={'/work/' + job.id}>Hồ sơ công việc</Link></div>
    {job.contract && <ContractEvidence initialJob={job} />}
    {!payment && !job.contract && <StatePanel kind="error" title="Chưa thể đọc trạng thái thanh toán"
      body={paymentError || 'Marketplace chưa trả dữ liệu.'} action={{ label: 'Tải lại', onClick: retry }} />}
    {payment && <>
      <section className="finance-statement" aria-label="Tóm tắt tài chính">
        <div className="finance-statement-top"><span>BẢN GHI TÀI CHÍNH / MARKETPLACE</span>
          {payment.simulation === true && <strong className="simulation-mark">Mô phỏng</strong>}
          {payment.network?.toLowerCase() === 'devnet' && <strong className="network-mark">DEVNET</strong>}</div>
        <h2>{user.userType === 'CLIENT' ? checkoutLabel(payment.checkoutOrderStatus) : offRampLabel(payment.offRampStatus)}</h2>
        <p>{payment.simulation === true
          ? 'Đây là luồng mô phỏng. Trạng thái hoàn tất không xác nhận tiền đã chuyển vào ngân hàng thật.'
          : 'Các chặng bên dưới là trạng thái do Marketplace trả về; công việc hoàn thành không đồng nghĩa mọi chặng chi trả đã hoàn tất.'}</p>
        <div className="finance-outcomes">
          <div><span>Giá trị công việc</span><strong>{money(job.budgetUsd)}</strong></div>
          <div><span>USDC đã xác nhận</span><strong>{payment.amountUsdcReceived == null ? 'Chưa có dữ liệu' : usdc(payment.amountUsdcReceived)}</strong></div>
          <div><span>VND dự kiến</span><strong>{payment.estimatedAmountVnd == null ? 'Chưa có ước tính' : vnd(payment.estimatedAmountVnd)}</strong></div>
        </div>
      </section>
      <StageRail stages={paymentStages(payment, tax)} />
      <section className="finance-support" aria-label="Chi tiết chi trả">
        <h2>Dữ liệu hỗ trợ</h2>
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
      </section>
      {taxError && <p className="finance-inline-error" role="alert">Chưa thể đọc chứng từ: {taxError}</p>}
      <section className="finance-tax-callout" aria-label="Chứng từ thuế">
        <div><span className="finance-category">Chứng từ thuế</span>
          <h2>{tax ? tax.statusLabel || tax.status : 'Chưa có chứng từ'}</h2>
          <p>{tax ? 'Trạng thái chứng từ được trả về từ Marketplace/MISA.' :
            'Không có bản ghi chứng từ cho công việc này; điều đó không xác nhận miễn thuế.'}</p></div>
        {tax ? <Link className="button" to={'/finance/tax-records/' + tax.id}>Xem chứng từ</Link> :
          <Link className="button button-secondary" to="/finance/tax-records">Danh sách chứng từ</Link>}
      </section>
      <TechnicalEvidence payment={payment} tax={tax} />
      {paymentError && <p className="finance-inline-error" role="alert">Không thể cập nhật: {paymentError}</p>}
      <button className="text-button finance-refresh" type="button" onClick={retry}>Làm mới từ Marketplace</button>
    </>}
  </>;
}

export function FinanceHome({ user }: { user: User }) {
  const [params] = useSearchParams();
  const jobId = params.get('jobId');
  return jobId ? <JobEvidence key={jobId} jobId={jobId} user={user} /> : <FinanceList user={user} />;
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
  return <>
    <PageHeading eyebrow="Tài chính / Thuế" title="Chứng từ theo công việc."
      description="Bản ghi thuế được lấy từ Marketplace. Trạng thái của chứng từ khác với trạng thái thanh toán." />
    <FinanceNav />
    {loading && <StatePanel kind="loading" title="Đang tải chứng từ" body="Marketplace đang trả danh sách chứng từ của tài khoản." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải chứng từ" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      {result.data.length === 0 ? <StatePanel kind="empty" title="Chưa có chứng từ trên trang này"
        body="Chứng từ chỉ xuất hiện khi quá trình chi trả và xuất thuế tạo được bản ghi." /> :
        <div className="tax-list">{result.data.map(record => <article key={record.id} className="tax-list-row">
          <div><span className="finance-category">Hồ sơ thuế / công việc</span>
            <h2><Link to={'/finance/tax-records/' + record.id}>{record.jobTitle || 'Công việc ' + record.jobId.slice(0, 8)}</Link></h2>
            <span>Tạo {date(record.createdAt)}</span></div>
          <div><span className="cell-label">Trạng thái MISA</span><strong>{record.statusLabel || record.status}</strong></div>
          <div><span className="cell-label">Thu nhập chịu thuế</span><strong>{vnd(record.taxableIncomeVnd)}</strong></div>
          <Link className="finance-row-link" to={'/finance/tax-records/' + record.id}>Xem chi tiết →</Link>
        </article>)}</div>}
      <nav className="pagination" aria-label="Phân trang chứng từ"><span>{result.totalElements} chứng từ · Trang {result.totalPages ? page + 1 : 0}/{result.totalPages}</span>
        <div><button className="button button-secondary" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trang trước</button>
          <button className="button button-secondary" disabled={page >= result.totalPages - 1} onClick={() => setPage(page + 1)}>Trang sau</button></div></nav>
    </>}
  </>;
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
    }, cause => { if (active) { setError(message(cause)); setLoading(false); } });
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
  return <>
    <PageHeading eyebrow="Tài chính / Chứng từ" title={record.jobTitle || 'Chứng từ theo công việc'}
      description="Trạng thái và số liệu thuế do Marketplace trả về. Số thu nhập chịu thuế dùng tỷ giá USD/VND riêng."
      aside={record.statusLabel || record.status} />
    <FinanceNav />
    <div className="finance-back"><Link to="/finance/tax-records">← Danh sách chứng từ</Link>
      <Link to={financePath(record.jobId)}>Bằng chứng thanh toán</Link></div>
    <section className="tax-statement" aria-label="Tình trạng chứng từ">
      <span className="finance-category">Trạng thái MISA</span>
      <h2>{record.statusLabel || record.status}</h2>
      {payment?.simulation === true && <p className="simulation-mark">Mô phỏng · luồng chi trả; chứng từ phản ánh dữ liệu hệ thống.</p>}
      <div className="tax-amounts"><div><span>Thu nhập chịu thuế</span><strong>{vnd(record.taxableIncomeVnd)}</strong></div>
        <div><span>Thuế đã khấu trừ</span><strong>{vnd(record.taxWithheldVnd)}</strong></div></div>
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
    {paymentError && <p className="finance-inline-error" role="alert">Chưa xác minh được trạng thái chi trả; thao tác đồng bộ/lập lại tạm khóa: {paymentError}</p>}
    <section className="tax-actions" aria-label="Tác vụ chứng từ">
      <div><h2>Chứng từ và trạng thái</h2>
        <p>Đồng bộ khi chứng từ có mã MISA và chi trả đã hoàn tất; lập lại chỉ khi xuất chứng từ thất bại.</p></div>
      <div className="tax-action-buttons">
        {canSync && <button className="button button-secondary" type="button" disabled={busy} onClick={() => void act('sync')}>Đồng bộ trạng thái</button>}
        {canRetry && <button className="button button-secondary" type="button" disabled={busy} onClick={() => void act('retry')}>Lập lại chứng từ</button>}
        {record.misaCertificateId && <>
          <button className="button" type="button" disabled={busy} onClick={() => void download('pdf')}>Tải PDF</button>
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => void download('xml')}>Tải XML</button>
        </>}
        {!canSync && !canRetry && !record.misaCertificateId && <span>Chưa có tệp chứng từ để tải.</span>}
      </div>
      {notice && <p className="lifecycle-success" role="status">{notice}</p>}
      {actionError && <p className="finance-inline-error" role="alert">{actionError}</p>}
    </section>
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
    <button className="text-button finance-refresh" type="button" onClick={() => setAttempt(value => value + 1)}>Làm mới từ Marketplace</button>
  </>;
}
