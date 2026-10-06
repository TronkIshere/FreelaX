import type { ReactNode } from 'react';

export function PageHeading({ eyebrow, title, description, descriptionClassName, aside }: {
  eyebrow: string; title: ReactNode; description: string; descriptionClassName?: string; aside?: string;
}) {
  return <header className={'page-head' + (aside ? ' page-head-with-aside' : '')}>
    <div className="page-head-copy">
      <span className="eyebrow">{eyebrow}</span>
      <h1>{title}</h1>
      <p className={descriptionClassName}>{description}</p>
    </div>
    {aside && <div className="page-aside">{aside}</div>}
  </header>;
}

// Presentation only: callers retain ownership of data, formatting and actions.
export function SectionHeading({ title, description, aside, id, level = 2 }: {
  title: string; description?: string; aside?: ReactNode; id?: string; level?: 2 | 3;
}) {
  const Heading = level === 3 ? 'h3' : 'h2';
  return <header className="section-heading">
    <div className="section-heading-copy">
      <Heading id={id}>{title}</Heading>
      {description && <p>{description}</p>}
    </div>
    {aside != null && <div className="section-heading-aside">{aside}</div>}
  </header>;
}

export function FactGrid({ facts, label }: {
  facts: ReadonlyArray<{ label: string; value: ReactNode }>; label?: string;
}) {
  return <dl className="fact-grid" aria-label={label}>
    {facts.map(fact => <div key={fact.label}>
      <dt>{fact.label}</dt><dd>{fact.value}</dd>
    </div>)}
  </dl>;
}

export function EvidenceDisclosure({ summary, children }: {
  summary: string; children: ReactNode;
}) {
  return <details className="technical-evidence">
    <summary>{summary}</summary>
    <div className="evidence-body">{children}</div>
  </details>;
}

export function ActionGroup({ children, label }: { children: ReactNode; label?: string }) {
  return <div className="action-group" role={label ? 'group' : undefined} aria-label={label}>
    {children}
  </div>;
}

export function StatePanel({ kind, title, body, action }: {
  kind: 'empty' | 'error' | 'loading'; title: string; body: string; action?: { label: string; onClick: () => void };
}) {
  return <section className={'state-panel state-' + kind} role={kind === 'error' ? 'alert' : 'status'} aria-live="polite">
    <span className="eyebrow">{kind === 'error' ? 'Không thể tải' : kind === 'loading' ? 'Đang xử lý' : 'Chưa có dữ liệu'}</span>
    <h2>{title}</h2>
    <p>{body}</p>
    {kind === 'loading' && <div className="skeleton-stack" aria-hidden="true"><span /><span /><span /></div>}
    {action && <ActionGroup>
      <button className="button button-secondary" type="button" onClick={action.onClick}>{action.label}</button>
    </ActionGroup>}
  </section>;
}
