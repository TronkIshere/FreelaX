import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import { PaymentJourney } from './PaymentJourney';
import type { PaymentFlowTimeline } from './types';

it('shows completed payment stages in green and later stages as waiting', () => {
  const flow = { steps: [
    { kind: 'USD_ORDER', status: 'CONFIRMED' },
    { kind: 'USD_RECEIVED', status: 'CONFIRMED' },
    { kind: 'CLIENT_USDC', status: 'PENDING' },
  ] } as PaymentFlowTimeline;
  const html = renderToStaticMarkup(<PaymentJourney flow={flow} />);
  expect(html.match(/payment-journey-done/g)).toHaveLength(2);
  expect(html.match(/payment-journey-current/g)).toHaveLength(1);
  expect(html).toContain('Đã nhận tiền');
  expect(html).toContain('Đang chờ');
  expect(html).toContain('Tiền USD của khách đã được xác nhận.');
  expect(html).toContain('Đang chờ tiền vào ví của khách.');
  expect(html).not.toContain('USD_ORDER');
  expect(html).not.toContain('localnet');
});

it('shows a rotating progress indicator only while a money step is being processed', () => {
  const flow = { steps: [
    { kind: 'USD_ORDER', status: 'CONFIRMED' },
    { kind: 'USD_RECEIVED', status: 'PROCESSING' },
  ] } as PaymentFlowTimeline;
  const html = renderToStaticMarkup(<PaymentJourney flow={flow} />);
  expect(html).toContain('loading-spinner');
  expect(html).toContain('Đang xử lý');
});
