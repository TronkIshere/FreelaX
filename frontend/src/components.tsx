export function PageHeading({ eyebrow, title, description, aside }: {
  eyebrow: string; title: string; description: string; aside?: string;
}) {
  return <header className="page-head">
    <div>
      <span className="eyebrow">{eyebrow}</span>
      <h1>{title}</h1>
      <p>{description}</p>
    </div>
    {aside && <div className="page-aside">{aside}</div>}
  </header>;
}

export function StatePanel({ kind, title, body, action }: {
  kind: 'empty' | 'error' | 'loading'; title: string; body: string; action?: { label: string; onClick: () => void };
}) {
  return <section className={'state-panel state-' + kind} role={kind === 'error' ? 'alert' : 'status'} aria-live="polite">
    <span className="eyebrow">{kind === 'error' ? 'Không thể tải' : kind === 'loading' ? 'Đang xử lý' : 'Chưa có dữ liệu'}</span>
    <h2>{title}</h2>
    <p>{body}</p>
    {kind === 'loading' && <div className="skeleton-stack" aria-hidden="true"><span /><span /><span /></div>}
    {action && <button className="button button-secondary" type="button" onClick={action.onClick}>{action.label}</button>}
  </section>;
}
