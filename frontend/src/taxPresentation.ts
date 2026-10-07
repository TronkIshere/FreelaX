import type { TaxRecord } from './types';

// Only the TaxRecord status determines certificate presentation. Payment export
// success and certificate identity never imply authority acceptance.
export function taxPresentation(record: TaxRecord) {
  const tone = record.status === 'ACCEPTED' ? 'accepted'
    : ['EXPORT_FAILED', 'REJECTED'].includes(record.status) ? 'error'
    : record.status === 'CORRECTION_REQUIRED' ? 'attention'
    : ['CANCELLED', 'REPLACED'].includes(record.status) ? 'closed' : 'pending';
  return { tone, label: record.statusLabel || record.status };
}
