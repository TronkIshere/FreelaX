import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError } from './api';
import { ActionGroup, PageHeading, SectionHeading, StatePanel } from './components';
import { DisputeError } from './ContractDispute';
import { ReviewRecord, ReviewReport } from './ContractReviews';
import { Portfolio } from './Portfolio';
import { ProfileRecord } from './Profile';
import type { Profile, Review, User } from './types';
import { RoughBurst, RoughUnderline } from './ui/kinetic';

export function PublicProfile({ user }: { user: User }) {
  const { userId = '' } = useParams();
  return <PublicProfileDocument key={userId + ':' + user.id} userId={userId} user={user} />;
}
function PublicProfileDocument({ user, userId }: { user: User; userId: string }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [reviews, setReviews] = useState<Review[] | null>(null); const [page, setPage] = useState(0);
  const [error, setError] = useState<unknown>(null); const generation = useRef(0);
  async function load() {
    const current = ++generation.current;
    try { const [p, r] = await Promise.all([api.profile(userId), api.publicReviews(userId, page)]); if (current === generation.current) { setProfile(p); setReviews(r); setError(null); } }
    catch (e) { if (current === generation.current) { setError(e); if (e instanceof ApiError && [403, 404].includes(e.status)) { setProfile(null); setReviews(null); } } }
  }
  useEffect(() => { setProfile(null); setReviews(null); setPage(0); }, [userId]);
  useEffect(() => { setReviews(null); void load(); const refresh = () => void load(); window.addEventListener('focus', refresh); window.addEventListener('freelax:rating-update', refresh); return () => { generation.current++; window.removeEventListener('focus', refresh); window.removeEventListener('freelax:rating-update', refresh); }; }, [userId, page]);
  return <div className="public-profile-page"><PageHeading eyebrow="Hồ sơ / Marketplace" title={<>Hồ sơ <span className="profile-title-accent">đối tác.<RoughUnderline seedKey="partner-title" className="profile-title-underline" /><RoughBurst seedKey="partner-title-rays" size={34} accent="ink" className="profile-title-rays" /></span></>} description="Thông tin công khai dành cho người dùng đã đăng nhập." />
    <DisputeError error={error} />{Boolean(error) && <button className="button button-secondary" onClick={() => void load()}>Thử đọc lại hồ sơ</button>}
    {!profile ? <StatePanel kind={error ? 'error' : 'loading'} title="Hồ sơ đối tác" body="Đang đọc thông tin được phép xem từ Marketplace." /> : <section className="profile-document"><ProfileRecord profile={profile} />{profile.userType === 'FREELANCER' && <Portfolio userId={userId} key={userId} />}
      <section className="profile-published-reviews" aria-label="Đánh giá đã công bố"><SectionHeading title="Đánh giá đã công bố" />{reviews?.filter(r => r.submitted && r.publishedAt).map(r => <div key={r.id}><ReviewRecord review={r} user={user} /><ReviewReport review={r} user={user} onRefresh={load} /></div>)}
      {!reviews?.length && <p>Trang này chưa có đánh giá công khai.</p>}
      <ActionGroup label="Trang đánh giá"><button className="button button-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Trang trước</button><span>Trang {page + 1}</span><button className="button button-secondary" disabled={!reviews || reviews.length < 20} onClick={() => setPage(p => p + 1)}>Trang tiếp</button></ActionGroup>
      </section>
    </section>}
  </div>;
}
