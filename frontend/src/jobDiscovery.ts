import type { JobCategory } from './types';

export const jobCategories: Record<JobCategory, string> = {
  WEB_FRONTEND: 'Web / Frontend', BACKEND_API: 'Backend / API', SEO_CONTENT: 'SEO / Nội dung',
  MOBILE_APP: 'Ứng dụng Mobile', UI_UX_DESIGN: 'UI/UX Design', ECOMMERCE: 'Thương mại điện tử',
  DATA_ANALYTICS: 'Dữ liệu / Phân tích', BRANDING_GRAPHIC: 'Branding / Thiết kế đồ họa', OTHER: 'Khác',
};

// A blank control means an empty list; blank entries inside a supplied list
// are invalid, not discarded. Job skills are independent of profile skills.
export function parseJobSkills(text: string): string[] {
  if (!text.trim()) return [];
  const skills = text.split(',').map(value => value.trim());
  if (skills.length > 10) throw new Error('Tối đa 10 kỹ năng công việc.');
  const seen = new Set<string>();
  for (const skill of skills) {
    if (skill.length < 2 || skill.length > 40) throw new Error('Mỗi kỹ năng cần 2–40 ký tự, không được để trống.');
    const key = skill.toLowerCase();
    if (seen.has(key)) throw new Error('Không được nhập kỹ năng trùng nhau.');
    seen.add(key);
  }
  return skills;
}
