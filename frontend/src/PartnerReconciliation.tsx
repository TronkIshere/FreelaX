import { useEffect, useState } from 'react';
import { api } from './api';
import { PageHeading, SectionHeading } from './components';

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
  const usd = (value: unknown) => Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + ' USD';
  return <section className="finance-detail-page recon-page" aria-label="Đối soát ký quỹ đối tác">
    <PageHeading eyebrow="Quản trị / Ký quỹ đối tác" title="Đối soát ký quỹ đối tác"
      description="So số dư ký quỹ đối tác mô phỏng với nghĩa vụ trên sổ Marketplace. Không phải số dư ngân hàng thật." />
    <div className="recon-toolbar">
      <span className="metadata" role="status">{loading ? 'Đang đối soát…' : view ? 'Sao kê lúc ' + new Date(view.statementAt).toLocaleString('vi-VN') : ''}</span>
      <button className="button button-secondary" type="button" disabled={loading} onClick={() => { setLoading(true); setTick(value => value + 1); }}>Làm mới đối soát</button>
    </div>
    {error && <p role="alert" className="form-error">{error}</p>}
    {view && <>
      <p><span className={'recon-badge ' + (view.matched ? 'recon-matched' : 'recon-alert')}>{view.matched ? 'Khớp' : 'Cảnh báo lệch số dư'}</span></p>
      <div className="recon-kpis recon-kpis-static">
        <div className="recon-kpi recon-kpi-all"><span>Số dư ký quỹ đối tác</span><strong>{usd(view.partnerBalanceUsd)}</strong></div>
        <div className="recon-kpi recon-kpi-all"><span>Nghĩa vụ trên sổ Marketplace</span><strong>{usd(view.ledgerLiabilityUsd)}</strong></div>
        <div className={'recon-kpi ' + (Number(view.differenceUsd) === 0 ? 'recon-kpi-done' : 'recon-kpi-attention')}><span>Chênh lệch</span><strong>{usd(view.differenceUsd)}</strong></div>
        <div className={'recon-kpi ' + (view.pendingFunding ? 'recon-kpi-running' : 'recon-kpi-done')}><span>Lệnh đang chờ xác nhận</span><strong>{view.pendingFunding}</strong></div>
      </div>
      {view.differences.length > 0 && <section className="recon-section">
        <SectionHeading title="Khoản cần điều tra" aside={`${view.differences.length} khoản`} />
        <table className="recon-table"><thead><tr><th>Milestone</th><th>Lý do</th><th>Chênh lệch</th></tr></thead><tbody>
          {view.differences.map(row => <tr key={row.milestoneId}><td><code>{row.milestoneId}</code></td><td>{row.reason}</td><td>{usd(row.amountUsd)}</td></tr>)}
        </tbody></table>
      </section>}
    </>}
  </section>;
}
