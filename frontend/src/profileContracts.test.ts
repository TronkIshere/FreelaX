import { afterEach, describe, expect, it, vi } from 'vitest';
import { MarketplaceApi } from './api';
import { profileUrl, validateProfile, validatePortfolio, validateReview, validateSkills } from './profileContracts';
import type { PortfolioInput, ProfilePatch, ReviewInput } from './types';

const profile: ProfilePatch = { version: 7, displayName: 'Valid name', avatarUrl: null, headline: null, bio: null, countryCode: 'VN', languages: [{ code: 'vi', proficiency: 'Bản ngữ' }] };
const portfolio: PortfolioInput = { title: 'Work sample', description: 'Actual work', projectUrl: 'https://example.test/proof', thumbnailUrl: null, skills: ['React'], sortOrder: 0, completedAt: '2026-01-01' };
const review: ReviewInput = { overall: 5, dimensions: { communication: 4, requirementsOrQuality: 3, timeliness: 2 }, comment: 'Review' };
afterEach(() => vi.unstubAllGlobals());
describe('Step 8/9 bounded contracts', () => {
  it('accepts supported profile, portfolio, skills and review values', () => {
    expect(validateProfile(profile)).toBeNull(); expect(validatePortfolio(portfolio, '2026-10-06')).toBeNull(); expect(validateSkills(['React', 'Java'])).toBeNull(); expect(validateReview(review)).toBeNull();
  });
  it.each(['http://example.test', 'javascript:alert(1)', 'https:example.test', 'https:///example.test', 'https://example.test\\proof', 'https://user:secret@example.test', 'https://example.test/#fragment', 'https://', 'https://example.test/' + 'x'.repeat(2048)])('rejects unsafe profile and portfolio URL %s', value => {
    expect(profileUrl(value)).toBeNull(); expect(validateProfile({ ...profile, avatarUrl: value })).not.toBeNull(); expect(validatePortfolio({ ...portfolio, projectUrl: value })).not.toBeNull();
  });
  it.each([['React', 'react'], ['a'], ['x'.repeat(41)], Array.from({ length: 21 }, (_, n) => 'Skill ' + n)].map(skills => ({ skills })))('rejects invalid skills %j', ({ skills }) => expect(validateSkills(skills)).not.toBeNull());
  it.each([{ displayName: 'a' }, { version: -1 }, { headline: 'x'.repeat(121) }, { bio: 'x'.repeat(2001) }, { countryCode: 'ZZ' }, { hourlyRateUsd: 0 }, { hourlyRateUsd: 1000000 }, { hourlyRateUsd: 1.123 }, { languages: [{ code: 'vi', proficiency: 'a' }] }, { languages: [{ code: 'vi', proficiency: 'Native' }, { code: 'VI', proficiency: 'Native' }] }])('rejects invalid profile field %j', value => expect(validateProfile({ ...profile, ...value })).not.toBeNull());
  it.each([{ title: 'a' }, { description: 'a' }, { sortOrder: -1 }, { sortOrder: 1.5 }, { sortOrder: 1001 }, { completedAt: '2026-02-30' }, { completedAt: '2027-01-01' }])('rejects invalid portfolio field %j', value => expect(validatePortfolio({ ...portfolio, ...value }, '2026-10-06')).not.toBeNull());
  it.each([0, 6, 1.5, NaN])('rejects non-integer or out-of-range rating %s', value => { expect(validateReview({ ...review, overall: value })).not.toBeNull(); expect(validateReview({ ...review, dimensions: { ...review.dimensions, timeliness: value } })).not.toBeNull(); });
  it('enforces comment boundary', () => { expect(validateReview({ ...review, comment: 'x'.repeat(2000) })).toBeNull(); expect(validateReview({ ...review, comment: 'x'.repeat(2001) })).not.toBeNull(); });
  it('uses exact Marketplace routes, allowlisted payloads and separate raw review lists', async () => {
    const response = (data: unknown) => new Response(JSON.stringify({ code: 200, data }));
    const fetcher = vi.fn().mockResolvedValueOnce(response({ accessToken: 'token' })).mockResolvedValueOnce(response({ id: 'u', email: 'u@example.test', displayName: 'User', userType: 'FREELANCER' })).mockImplementation(async () => response([]));
    vi.stubGlobal('fetch', fetcher); const api = new MarketplaceApi(); await api.restore();
    await api.patchProfile({ ...profile, email: 'must-not-send', authorities: ['ROLE_ADMIN'] } as ProfilePatch);
    await api.replaceSkills(8, ['React']); await api.savePortfolio({ ...portfolio, version: 99 } as PortfolioInput); await api.savePortfolio(portfolio, { id: 'i/1', version: 3 }); await api.deletePortfolio('i/1');
    expect(await api.publicReviews('u/1', 2)).toEqual([]); expect(await api.reportedReviews(50)).toEqual([]);
    await api.submitReview('c/1', review); await api.reportReview('c/1', 'r/1', 'Reason'); await api.adminReview('r/1'); await api.moderateReview('r/1', 'INVALIDATE', 'Reason');
    const calls = fetcher.mock.calls.slice(2);
    expect(calls.map(c => c[0])).toEqual(['/api/v1/profiles/me', '/api/v1/profiles/me/skills', '/api/v1/profiles/me/portfolio', '/api/v1/profiles/me/portfolio/i%2F1', '/api/v1/profiles/me/portfolio/i%2F1', '/api/v1/profiles/u%2F1/reviews?page=2&size=20', '/api/v1/admin/reviews/reported?size=50', '/api/v1/contracts/c%2F1/reviews', '/api/v1/contracts/c%2F1/reviews/r%2F1/reports', '/api/v1/admin/reviews/r%2F1', '/api/v1/admin/reviews/r%2F1/moderation']);
    expect(JSON.parse(calls[0][1].body)).toEqual(profile); expect(JSON.parse(calls[1][1].body)).toEqual({ version: 8, skills: ['React'] });
    expect(JSON.parse(calls[2][1].body)).toEqual(portfolio); expect(JSON.parse(calls[3][1].body)).toEqual({ ...portfolio, version: 3 });
    expect(calls[4][1].method).toBe('DELETE'); expect(JSON.parse(calls[7][1].body)).toEqual(review);
  });
});
