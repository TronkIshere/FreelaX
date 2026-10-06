import { JobThumbnail } from './job-thumbnails/JobThumbnail';
import { jobFamily, type JobFamily, type JobThumbnailSource } from './job-thumbnails/jobFamily';

// Presentation only: category identity and workflow state are separate channels.
const jobTones: Record<string, string> = {
  OPEN: 'state-open', AWAITING_PAYMENT: 'state-awaiting-payment',
  IN_PROGRESS: 'state-in-progress', SUBMITTED_FOR_REVIEW: 'state-review',
  REVISION_REQUESTED: 'state-revision', COMPLETED: 'state-completed', CANCELLED: 'state-cancelled',
};
const applicationTones: Record<string, string> = {
  PENDING: 'application-pending', ACCEPTED: 'application-accepted',
  REJECTED: 'application-rejected', CANCELLED: 'application-cancelled',
};
export const jobStateTone = (status: string) => jobTones[status] ?? 'state-unknown';
export const applicationStateTone = (status: string) => applicationTones[status] ?? 'state-unknown';

const families: Record<JobFamily, { label: string; tone: string }> = {
  web: { label: 'WEB / FRONTEND', tone: 'category-web' },
  backend: { label: 'BACKEND / API', tone: 'category-backend' },
  seo: { label: 'SEO / NỘI DUNG', tone: 'category-seo' },
  mobile: { label: 'MOBILE APP', tone: 'category-mobile' },
  uiux: { label: 'UI / UX', tone: 'category-uiux' },
  ecommerce: { label: 'E-COMMERCE', tone: 'category-ecommerce' },
  data: { label: 'DATA / ANALYTICS', tone: 'category-data' },
  branding: { label: 'BRANDING / GRAPHIC', tone: 'category-branding' },
  development: { label: 'KHÁC', tone: 'category-other' },
};

export function JobCategoryPlate({ job }: { job: JobThumbnailSource }) {
  // The same resolver feeds JobThumbnail; inferred families never change domain data.
  const family = jobFamily(job);
  const { label, tone } = families[family];
  return <span className={'job-category-plate ' + tone} data-category={job.category ?? ''} data-family={family}>{label}</span>;
}

export function JobIdentityCluster({ job }: { job: JobThumbnailSource }) {
  return <span className={'job-identity-cluster ' + families[jobFamily(job)].tone}>
    <JobThumbnail job={job} />
  </span>;
}
