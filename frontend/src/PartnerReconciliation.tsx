import { useEffect, useState } from 'react';
import { api } from './api';
import { SectionHeading } from './components';

type View = Awaited<ReturnType<typeof api.partnerReconciliation>>;

export function PartnerReconciliation() {
  const [view, setView] = useState<View | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let active = true;
    api.partnerReconciliation().then(result => { if (active) { setView(result); setError(''); } })
      .catch(() => { if (active) setError('Không tải được báo cáo đối soát.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tick]);
  return <section className="page-section" aria-label="Đối soát ký quỹ đối tác">
    <SectionHeading title="Đối soát ký quỹ đối tác" aside="MVP mock · Không phải số dư ngân hàng thật" />
    {loading && <p>Đang đối soát…</p>}
    {error && <p role="alert">{error}</p>}
    {view && <>
      <p role="status"><strong>{view.matched ? 'Khớp' : 'Cảnh báo lệch số dư'}</strong></p>
      <dl className="reference-list">
        <div><dt>Số dư ký quỹ đối tác mock</dt><dd>{String(view.partnerBalanceUsd)} USD</dd></div>
        <div><dt>Nghĩa vụ trên ledger Marketplace</dt><dd>{String(view.ledgerLiabilityUsd)} USD</dd></div>
        <div><dt>Chênh lệch</dt><dd>{String(view.differenceUsd)} USD</dd></div>
        <div><dt>Lệnh đang chờ xác nhận</dt><dd>{view.pendingFunding}</dd></div>
        <div><dt>Thời điểm sao kê</dt><dd>{view.statementAt}</dd></div>
      </dl>
      {view.differences.length > 0 && <table className="partner-reconciliation-table"><caption>Khoản cần điều tra</caption><thead><tr><th>Milestone</th><th>Lý do</th><th>Chênh lệch USD</th></tr></thead><tbody>
        {view.differences.map(row => <tr key={row.milestoneId}><td><code>{row.milestoneId}</code></td><td>{row.reason}</td><td>{String(row.amountUsd)}</td></tr>)}
      </tbody></table>}
    </>}
    <button className="text-button" type="button" onClick={() => { setLoading(true); setTick(value => value + 1); }}>Làm mới đối soát</button>
  </section>;
}
