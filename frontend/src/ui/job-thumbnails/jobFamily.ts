import type { JobCategory } from '../../types';
// Decorative classification only. No business category or generated job data.
export type JobFamily = 'web' | 'backend' | 'seo' | 'mobile' | 'uiux' | 'ecommerce' | 'data' | 'branding' | 'development';
export interface JobThumbnailSource {
  title?: string | null;
  category?: string | null;
  type?: string | null;
  skills?: readonly string[] | null;
}

const categoryFamilies: Record<JobCategory, JobFamily> = {
  WEB_FRONTEND: 'web', BACKEND_API: 'backend', SEO_CONTENT: 'seo', MOBILE_APP: 'mobile',
  UI_UX_DESIGN: 'uiux', ECOMMERCE: 'ecommerce', DATA_ANALYTICS: 'data',
  BRANDING_GRAPHIC: 'branding', OTHER: 'development',
};

// Specific phrases precede broad terms: React Native is mobile, graphic design
// is branding, and JavaScript is never Java. Boundaries prevent 'database' from
// matching 'data', 'store' from matching 'restore', or 'UI' from matching 'build'.
const signals: readonly [JobFamily, RegExp][] = [
  ['mobile', /\b(react native|mobile|android|ios|flutter|kotlin|swift)\b/],
  ['branding', /\b(branding|logo|graphic design|brand identity|visual identity)\b/],
  ['ecommerce', /\b(e commerce|ecommerce|storefront|store|shop|shopping|cart|checkout|product)\b/],
  ['backend', /\b(backend|back end|rest|api|server|node js|nodejs|express|spring|java|database|mysql|postgresql)\b/],
  ['seo', /\b(seo|content|marketing|keyword|copywriting|search optimization)\b/],
  ['data', /\b(dashboard|analytics|data|report|bi|power bi|visualization)\b/],
  ['web', /\b(frontend|front end|web|landing page|html|css|javascript|react|next js|nextjs)\b/],
  ['uiux', /\b(ui|ux|figma|prototype|wireframe|design)\b/],
];

function normalize(value: string) {
  return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ').trim();
}
function match(values: readonly (string | null | undefined)[]): JobFamily | undefined {
  const text = values.filter((value): value is string => typeof value === 'string').map(normalize).join(' / ');
  return signals.find(([, pattern]) => pattern.test(text))?.[0];
}
export function jobFamily(source: JobThumbnailSource): JobFamily {
  if (source.category && Object.hasOwn(categoryFamilies, source.category)) {
    return categoryFamilies[source.category as JobCategory];
  }
  return match([source.category, source.type]) ?? match(source.skills ?? []) ?? match([source.title]) ?? 'development';
}
