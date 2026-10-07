import type { HTMLAttributes, ReactNode } from 'react';
import { motion } from 'motion/react';
import { ArrowRight } from 'lucide-react';
import { KineticMotionContext, kineticVariants, useKineticFocus, useKineticMotion } from './motion';
import type { KineticSurface } from './palette';

type RegionProps = Pick<HTMLAttributes<HTMLElement>, 'id' | 'aria-label' | 'aria-labelledby' | 'aria-describedby'>;

export function KineticCard({ as = 'article', children, variant = 'cream', depth = 'regular', motion: allowMotion = true,
  className = '', ...region }: RegionProps & {
  as?: 'article' | 'section' | 'div'; children: ReactNode; variant?: KineticSurface;
  depth?: 'regular' | 'compact'; motion?: boolean; className?: string;
}) {
  const { enabled, transition } = useKineticMotion(allowMotion);
  const { focused, ...focus } = useKineticFocus();
  const Surface = as === 'section' ? motion.section : as === 'div' ? motion.div : motion.article;
  return <KineticMotionContext.Provider value={enabled}>
    <Surface {...region} {...focus} className={`ku-card ku-card--${depth} ku-surface--${variant} ${className}`}
      data-motion={enabled ? 'on' : 'off'} initial={false} variants={kineticVariants(depth === 'compact' ? 'compactCard' : 'card', enabled)}
      animate={enabled && focused ? 'active' : 'rest'} whileHover={enabled ? 'active' : undefined}
      transition={transition}>{children}</Surface>
  </KineticMotionContext.Provider>;
}

// Functional direction. The surrounding link/button must carry the real accessible action label.
// With active omitted, it inherits a motion parent's rest/active variants.
export function KineticActionArrow({ active, motion: allowMotion = true, className = '' }: {
  active?: boolean; motion?: boolean; className?: string;
}) {
  const { enabled, transition } = useKineticMotion(allowMotion);
  return <motion.span className={'ku-action-arrow ' + className} aria-hidden="true" data-motion={enabled ? 'on' : 'off'}
    initial={false} variants={kineticVariants('arrow', enabled)}
    animate={active === undefined ? undefined : enabled && active ? 'active' : 'rest'} transition={transition}>
    <ArrowRight size={28} strokeWidth={2} focusable="false" />
  </motion.span>;
}

export function KineticLedgerRow({ title, thumbnail, metadata, status, value, action, attention = false,
  motion: allowMotion = true, className = '', ...region }: RegionProps & {
  title: ReactNode; thumbnail?: ReactNode; metadata?: ReactNode; status?: ReactNode;
  value?: ReactNode; action?: ReactNode; attention?: boolean; motion?: boolean; className?: string;
}) {
  const { enabled, transition } = useKineticMotion(allowMotion);
  const { focused, ...focus } = useKineticFocus();
  return <KineticMotionContext.Provider value={enabled}>
    <motion.li {...region} {...focus} className={`ku-ledger-row ${thumbnail != null ? 'ku-ledger-row--illustrated' : ''} ${className}`}
      data-attention={attention ? 'true' : undefined} data-motion={enabled ? 'on' : 'off'} initial={false}
      variants={kineticVariants('ledger', enabled)} animate={enabled && focused ? 'active' : 'rest'}
      whileHover={enabled ? 'active' : undefined} transition={transition}>
      {thumbnail != null && <motion.span className="ku-ledger-thumbnail" aria-hidden="true"
        variants={kineticVariants('thumbnail', enabled)} transition={transition}>{thumbnail}</motion.span>}
      <div className="ku-ledger-copy">
        <div className="ku-ledger-title">{title}</div>
        {(metadata != null || status != null || value != null) && <div className="ku-ledger-facts">
          {status != null && <span className="ku-ledger-status">{status}</span>}
          {value != null && <span className="ku-ledger-value">{value}</span>}
          {metadata != null && <span className="ku-ledger-metadata">{metadata}</span>}
        </div>}
      </div>
      {action != null && <span className="ku-ledger-action">{action}</span>}
    </motion.li>
  </KineticMotionContext.Provider>;
}
