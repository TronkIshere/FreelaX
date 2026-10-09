import type { PaymentFlowTimeline } from './types';

type Step = PaymentFlowTimeline['steps'][number];
const payoutStages = [
  ['USD_ORDER', 'Bắt đầu thanh toán'],
  ['USD_RECEIVED', 'Đã nhận tiền'],
  ['CLIENT_USDC', 'Tiền vào ví'],
  ['ESCROW', 'Giữ tiền cho công việc'],
  ['WORK_ACCEPTED', 'Duyệt kết quả'],
  ['USDC_RELEASE', 'Chuyển tiền cho người làm'],
  ['WITHDRAWAL', 'Đổi sang tiền Việt'],
  ['VND_PAYOUT', 'Người làm nhận tiền'],
] as const;
const refundStages = [
  ['USD_ORDER', 'Bắt đầu thanh toán'],
  ['USD_RECEIVED', 'Đã nhận tiền'],
  ['CLIENT_USDC', 'Tiền vào ví'],
  ['ESCROW', 'Giữ tiền cho công việc'],
  ['USDC_REFUND', 'Hoàn tiền về ví khách'],
  ['WITHDRAWAL', 'Gửi tiền hoàn'],
  ['USD_REFUND', 'Khách nhận lại tiền'],
] as const;
const stageExplanation: Record<string, [string, string]> = {
  USD_ORDER: ['Khách đã mở yêu cầu thanh toán.', 'Khách sẽ bắt đầu thanh toán cho công việc.'],
  USD_RECEIVED: ['Tiền USD của khách đã được xác nhận.', 'Đang chờ xác nhận tiền khách gửi.'],
  CLIENT_USDC: ['Tiền đã được đổi và đưa vào ví của khách.', 'Đang chờ tiền vào ví của khách.'],
  ESCROW: ['Tiền đã được giữ cho công việc; người làm có thể bắt đầu.', 'Đang chờ giữ tiền cho công việc.'],
  WORK_ACCEPTED: ['Kết quả công việc đã được duyệt.', 'Đang chờ duyệt kết quả công việc.'],
  USDC_RELEASE: ['Tiền đã được chuyển sang ví người làm.', 'Đang chờ chuyển tiền sang ví người làm.'],
  WITHDRAWAL: ['Người nhận đã xác nhận đổi tiền.', 'Đang chờ người nhận xác nhận đổi tiền.'],
  VND_PAYOUT: ['Tiền VND sau phí và thuế đã được chi cho người làm.', 'Đang chờ chuyển VND cho người làm.'],
  USDC_REFUND: ['Tiền đã được hoàn về ví khách.', 'Đang chờ hoàn tiền về ví khách.'],
  USD_REFUND: ['Khoản hoàn USD đã được xác nhận.', 'Đang chờ khoản hoàn USD được xác nhận.'],
};

function label(status: string): string {
  switch (status) {
    case 'CONFIRMED': return 'Hoàn tất';
    case 'PROCESSING': return 'Đang xử lý';
    case 'UNKNOWN': return 'Đang kiểm tra';
    case 'FAILED': return 'Cần hỗ trợ';
    default: return 'Đang chờ';
  }
}

export function PaymentJourney({ flow }: { flow: PaymentFlowTimeline }) {
  const byKind = new Map<string, Step>(flow.steps.map(step => [step.kind, step]));
  const refund = ['USDC_REFUND', 'USD_REFUND'].some(kind => {
    const status = byKind.get(kind)?.status;
    return status != null && !['NOT_STARTED', 'FAILED'].includes(status);
  });
  const stages = refund ? refundStages : payoutStages;
  const current = stages.findIndex(([kind]) => byKind.get(kind)?.status !== 'CONFIRMED');
  return <ol className="payment-journey" aria-label="Tiến trình thanh toán">
    {stages.map(([kind, title], index) => {
      const step = byKind.get(kind);
      const status = step?.status || 'NOT_STARTED';
      const tone = status === 'CONFIRMED' ? 'done' : status === 'FAILED' ? 'error'
        : index === current ? 'current' : 'waiting';
      return <li className={'payment-journey-stage payment-journey-' + tone} key={kind}>
        <span className="payment-journey-number">{index + 1}</span>
        <strong>{title}</strong>
        <span className="payment-journey-status">{['PROCESSING', 'UNKNOWN'].includes(status)
          && <span className="loading-spinner" aria-hidden="true" />}{label(status)}</span>
        <p className="payment-journey-note">{stageExplanation[kind][status === 'CONFIRMED' ? 0 : 1]}</p>
      </li>;
    })}
  </ol>;
}

export function paymentJourneySummary(flow: PaymentFlowTimeline | undefined): string {
  if (!flow) return 'Đang cập nhật';
  const byKind = new Map(flow.steps.map(step => [step.kind, step.status]));
  if (byKind.get('VND_PAYOUT') === 'CONFIRMED') return 'Đã thanh toán';
  if (byKind.get('USD_REFUND') === 'CONFIRMED') return 'Đã hoàn tiền';
  const refund = ['USDC_REFUND', 'USD_REFUND'].some(kind =>
    byKind.has(kind) && !['NOT_STARTED', 'FAILED'].includes(byKind.get(kind) || ''));
  const stages = refund ? refundStages : payoutStages;
  const current = stages.find(([kind]) => byKind.get(kind) !== 'CONFIRMED');
  return current ? `${label(byKind.get(current[0]) || 'NOT_STARTED')}: ${current[1]}` : 'Hoàn tất';
}
