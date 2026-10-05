import type { PortfolioInput, ProfilePatch, ReviewInput } from './types';
import { httpsUrl } from './workflowContracts';

// ISO 3166-1 alpha-2, matching the backend Locale.getISOCountries() allowlist.
export const countryCodes = 'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'.split(' ');
export function profileUrl(value: string | null | undefined): URL | null {
  if (!value || value.length > 2048 || !/^https:\/\/[a-zA-Z0-9.:[\]-]+(?:[/?#]|$)/i.test(value) || /[\s\\<>"{}|^`]/.test(value) || /%(?![\da-f]{2})/i.test(value)) return null;
  const url = httpsUrl(value); return url?.hostname && !url.hash && !value.includes('#') ? url : null;
}
export const skillList = (value: string) => value.split(',').map(text => text.trim()).filter(Boolean);
export function validateSkills(skills: string[]): string | null {
  if (skills.length > 20 || skills.some(s => s.trim().length < 2 || s.trim().length > 40) || new Set(skills.map(s => s.trim().toLowerCase())).size !== skills.length) return 'Tối đa 20 kỹ năng, mỗi kỹ năng 2–40 ký tự, không trùng tên.';
  return null;
}
export function validateProfile(input: ProfilePatch): string | null {
  if (!Number.isInteger(input.version) || input.version < 0 || input.displayName.trim().length < 2 || input.displayName.trim().length > 80) return 'Tên hiển thị cần 2–80 ký tự và phiên bản hồ sơ hợp lệ.';
  for (const [field, max] of [['headline', 120], ['bio', 2000], ['availability', 40], ['companyName', 120]] as const) if ((input[field]?.trim().length || 0) > max) return 'Nội dung hồ sơ vượt giới hạn cho phép.';
  for (const field of ['avatarUrl', 'companyWebsite'] as const) if (input[field] && !profileUrl(input[field])) return 'URL cần HTTPS có hostname, không có tài khoản hoặc fragment; tối đa 2.048 ký tự.';
  if (input.countryCode && !countryCodes.includes(input.countryCode)) return 'Chọn mã quốc gia ISO hợp lệ.';
  if (input.languages.length > 20 || new Set(input.languages.map(l => l.code.toLowerCase())).size !== input.languages.length || input.languages.some(l => !/^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$/.test(l.code) || l.code.length > 35 || l.proficiency.trim().length < 2 || l.proficiency.trim().length > 40)) return 'Tối đa 20 ngôn ngữ không trùng; mã ngôn ngữ hợp lệ, trình độ là văn bản 2–40 ký tự.';
  if (input.hourlyRateUsd != null && (!Number.isFinite(input.hourlyRateUsd) || input.hourlyRateUsd <= 0 || input.hourlyRateUsd > 999999.99 || !/^\d+(\.\d{1,2})?$/.test(String(input.hourlyRateUsd)))) return 'Đơn giá cần số dương, tối đa 999999.99 USD và 2 chữ số thập phân.';
  return null;
}
export function validatePortfolio(input: PortfolioInput, today = localDate()): string | null {
  if (input.title.trim().length < 2 || input.title.trim().length > 160 || input.description.trim().length < 2 || input.description.trim().length > 2000) return 'Tiêu đề cần 2–160 ký tự; mô tả cần 2–2.000 ký tự.';
  for (const field of ['projectUrl', 'thumbnailUrl'] as const) if (input[field] && !profileUrl(input[field])) return 'URL portfolio cần HTTPS an toàn, không có fragment.';
  if (!Number.isInteger(input.sortOrder) || input.sortOrder < 0 || input.sortOrder > 1000) return 'Thứ tự cần số nguyên từ 0 đến 1000.';
  if (input.completedAt && (!/^\d{4}-\d{2}-\d{2}$/.test(input.completedAt) || Number.isNaN(Date.parse(input.completedAt)) || new Date(input.completedAt).toISOString().slice(0, 10) !== input.completedAt || input.completedAt > today)) return 'Ngày hoàn thành cần ngày ISO hợp lệ, không ở tương lai.';
  return validateSkills(input.skills);
}
function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }
export function validateReview(input: ReviewInput): string | null {
  if ([input.overall, input.dimensions.communication, input.dimensions.requirementsOrQuality, input.dimensions.timeliness].some(n => !Number.isInteger(n) || n < 1 || n > 5)) return 'Chọn đủ bốn điểm nguyên từ 1 đến 5.';
  return (input.comment?.length || 0) > 2000 ? 'Nhận xét tối đa 2.000 ký tự.' : null;
}
export function ratingUpdated(userIds: string[]) { window.dispatchEvent(new CustomEvent('freelax:profile-update', { detail: { userIds } })); window.dispatchEvent(new Event('freelax:rating-update')); }
