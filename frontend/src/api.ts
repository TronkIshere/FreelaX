import type { DiscoverJob, DiscoveryFilters, Job, JobApplication, JobPaymentStatus, JobSubmission, MyApplication, Notification as MarketplaceNotification, Page, RegisterInput, TaxRecord, User, UserType } from './types';

import type { ClientBankAccount, ClientBankInput, FundingResponse, SubmissionPayload, ContractSubmission, ReviewDecision, ContractSettlement, ContractCancellationRecord, CancellationRequest, CancellationDecision } from './types';

// Same-origin by default; an optional public origin can be supplied at build time.
const configuredApiOrigin = (import.meta.env.VITE_MARKETPLACE_API_ORIGIN || '').trim().replace(/\/+$/, '');
const API_ROOT = configuredApiOrigin + '/api/v1';

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly code: number | null = null) {
    super(message);
    this.name = 'ApiError';
  }
}

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

export function trustedUser(value: unknown): User {
  const data = record(value);
  const role = data?.userType;
  if (!data || typeof data.id !== 'string' || typeof data.email !== 'string' ||
      typeof data.displayName !== 'string' || (role !== 'CLIENT' && role !== 'FREELANCER')) {
    throw new ApiError('Không thể xác minh vai trò tài khoản từ máy chủ.', 0);
  }
  // Missing or malformed capability data never grants UI access or invalidates identity.
  const authorities = Array.isArray(data.authorities) && data.authorities.every(
    authority => typeof authority === 'string' && authority.length > 0 && authority.trim() === authority,
  ) ? [...new Set<string>(data.authorities)] : [];
  return { id: data.id, email: data.email, displayName: data.displayName, userType: role as UserType, authorities };
}

// UI discovery only; protected endpoints still enforce server authorization.
export function hasAuthority(user: Pick<User, 'authorities'> | null | undefined, authority: string): boolean {
  return user?.authorities?.includes(authority) === true;
}

export function serverPage<T>(value: unknown): Page<T> {
  const data = record(value);
  if (!data || !Number.isInteger(data.currentPage) || !Number.isInteger(data.pageSize) ||
      !Number.isInteger(data.totalPages) || !Number.isInteger(data.totalElements) || !Array.isArray(data.data)) {
    throw new ApiError('Phản hồi phân trang từ máy chủ không hợp lệ.', 0);
  }
  return data as unknown as Page<T>;
}

async function envelope<T>(response: Response): Promise<T> {
  let value: unknown;
  try {
    value = await response.json();
  } catch {
    throw new ApiError(response.status === 401 ? 'Phiên đăng nhập đã hết hạn.' : response.ok ? 'Máy chủ trả dữ liệu không hợp lệ.' : 'Dịch vụ trả phản hồi không hợp lệ (HTTP ' + response.status + '). Vui lòng tải lại trạng thái trước khi thử tiếp.', response.status);
  }
  const payload = record(value);
  if (!response.ok || !payload || payload.code !== 200) {
    const serverMessage = payload?.message || payload?.error;
    const message = typeof serverMessage === 'string' && serverMessage.trim()
      ? serverMessage : 'Yêu cầu không thành công. Vui lòng thử lại.';
    throw new ApiError(message, response.status, typeof payload?.status === 'number' ? payload.status : null);
  }
  return payload.data as T;
}

export class MarketplaceApi {
  private accessToken: string | null = null;
  onSessionExpired: (() => void) | null = null;

  private async raw<T>(path: string, init: RequestInit = {}, authorized = false): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body) headers.set('Content-Type', 'application/json');
    if (authorized && this.accessToken) headers.set('Authorization', 'Bearer ' + this.accessToken);
    let response: Response;
    try {
      response = await fetch(API_ROOT + path, { ...init, headers, credentials: 'include' });
    } catch {
      throw new ApiError('Không thể kết nối Marketplace. Kiểm tra máy chủ và thử lại.', 0);
    }
    return envelope<T>(response);
  }

  private async authorized<T>(path: string, init: RequestInit = {}): Promise<T> {
    if (!this.accessToken) throw new ApiError('Phiên đăng nhập đã hết hạn.', 401);
    try {
      return await this.raw<T>(path, init, true);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      try {
        await this.restore();
        return await this.raw<T>(path, init, true);
      } catch (retryError) {
        if (retryError instanceof ApiError && retryError.status === 401) {
          this.clear();
          this.onSessionExpired?.();
        }
        throw retryError;
      }
    }
  }

  clear(): void {
    this.accessToken = null;
  }

  async restore(): Promise<User> {
    const refreshed = await this.raw<{ accessToken: string }>('/auth/refresh-token', { method: 'POST' });
    if (!refreshed || typeof refreshed.accessToken !== 'string' || !refreshed.accessToken) {
      throw new ApiError('Không thể khôi phục phiên đăng nhập.', 0);
    }
    this.accessToken = refreshed.accessToken;
    return trustedUser(await this.raw<unknown>('/auth/me', {}, true));
  }

  async register(input: RegisterInput): Promise<User> {
    return trustedUser(await this.raw<unknown>('/auth/register', {
      method: 'POST', body: JSON.stringify(input),
    }));
  }

  async signIn(email: string, password: string): Promise<User> {
    const signedIn = await this.raw<{ status: string; accessToken: string }>('/auth/sign-in', {
      method: 'POST', body: JSON.stringify({ email, password }),
    });
    if (!signedIn || signedIn.status !== 'SUCCESS' || typeof signedIn.accessToken !== 'string' || !signedIn.accessToken) {
      throw new ApiError('Đăng nhập không thành công.', 401);
    }
    this.accessToken = signedIn.accessToken;
    try {
      return trustedUser(await this.raw<unknown>('/auth/me', {}, true));
    } catch (error) {
      this.clear();
      throw error;
    }
  }

  async me(): Promise<User> {
    return trustedUser(await this.authorized<unknown>('/auth/me'));
  }

  async signOut(): Promise<void> {
    if (!this.accessToken) return;
    try {
      await this.raw<void>('/auth/sign-out', {
        method: 'POST', body: JSON.stringify({ accessToken: this.accessToken }),
      }, true);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      await this.restore();
      await this.raw<void>('/auth/sign-out', {
        method: 'POST', body: JSON.stringify({ accessToken: this.accessToken }),
      }, true);
    }
    this.clear();
  }

  async clientJobs(page: number, size = 10): Promise<Page<Job>> {
    return serverPage<Job>(await this.authorized<unknown>('/marketplace/jobs?page=' + page + '&size=' + size));
  }

  async discoverJobs(page: number, filters: DiscoveryFilters, size = 10): Promise<Page<DiscoverJob>> {
    const query = new URLSearchParams({ page: String(page), size: String(size), sort: filters.sort, application: filters.application });
    if (filters.keyword.trim()) query.set('keyword', filters.keyword.trim());
    if (filters.minBudgetUsd) query.set('minBudgetUsd', filters.minBudgetUsd);
    if (filters.maxBudgetUsd) query.set('maxBudgetUsd', filters.maxBudgetUsd);
    return serverPage<DiscoverJob>(await this.authorized<unknown>('/marketplace/jobs/discover?' + query));
  }

  async myJobs(page: number, size = 10): Promise<Page<Job>> {
    return this.clientJobs(page, size);
  }

  async job(jobId: string): Promise<Job> {
    return this.authorized<Job>('/marketplace/jobs/' + encodeURIComponent(jobId));
  }

  async findDiscoverJob(jobId: string): Promise<DiscoverJob | null> {
    const filters: DiscoveryFilters = { keyword: '', minBudgetUsd: '', maxBudgetUsd: '', sort: 'NEWEST', application: 'ALL' };
    let page = 0;
    let totalPages = 1;
    while (page < totalPages) {
      const result = await this.discoverJobs(page, filters, 100);
      const found = result.data.find(job => job.id === jobId);
      if (found) return found;
      totalPages = result.totalPages;
      page++;
    }
    return null;
  }

  async apply(jobId: string): Promise<JobApplication> {
    return this.authorized<JobApplication>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/apply',
      { method: 'POST', body: '{}' });
  }

  async myApplications(page: number, status: string = 'ALL', size = 10): Promise<Page<MyApplication>> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (status !== 'ALL') query.set('status', status);
    return serverPage<MyApplication>(await this.authorized<unknown>('/marketplace/jobs/applications/me?' + query));
  }

  async applicants(jobId: string): Promise<JobApplication[]> {
    return this.authorized<JobApplication[]>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/applications');
  }

  async assign(jobId: string, freelancerId: string): Promise<Job> {
    return this.authorized<Job>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/assignments',
      { method: 'POST', body: JSON.stringify({ freelancerId }) });
  }
  async submissions(jobId: string): Promise<JobSubmission[]> {
    return this.authorized<JobSubmission[]>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/submissions');
  }

  async submitWork(jobId: string, summary: string, deliverableUrl: string | null): Promise<JobSubmission> {
    return this.authorized<JobSubmission>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/submit-work',
      { method: 'POST', body: JSON.stringify({ summary, deliverableUrl }) });
  }

  async requestRevision(jobId: string, feedback: string): Promise<JobSubmission> {
    return this.authorized<JobSubmission>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/request-revision',
      { method: 'POST', body: JSON.stringify({ feedback }) });
  }

  async approveWork(jobId: string): Promise<Job> {
    return this.authorized<Job>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/approve',
      { method: 'POST', body: '{}' });
  }
  async paymentStatus(jobId: string): Promise<JobPaymentStatus> {
    return this.authorized<JobPaymentStatus>('/marketplace/jobs/' + encodeURIComponent(jobId) + '/payment-status');
  }

  async clientBank(): Promise<ClientBankAccount> {
    return this.authorized('/payment-methods/bank-account');
  }
  async saveClientBank(input: ClientBankInput): Promise<ClientBankAccount> {
    return this.authorized('/payment-methods/bank-account', { method: 'PUT', body: JSON.stringify(input) });
  }
  async funding(contractId: string, milestoneId: string, transactionId?: string): Promise<FundingResponse | null> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/milestones/' + encodeURIComponent(milestoneId)
      + '/fund' + (transactionId ? '/' + encodeURIComponent(transactionId) : ''));
  }
  async fund(contractId: string, milestoneId: string, key: string, amount: string, currency: string): Promise<FundingResponse> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/milestones/' + encodeURIComponent(milestoneId) + '/fund',
      { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ paymentMethodId: 'BANK_ACCOUNT_ON_FILE', expectedAmount: { amount, currency } }) });
  }
  async contractSubmissions(contractId: string): Promise<ContractSubmission[]> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/submissions');
  }
  async submitContract(contractId: string, key: string, payload: SubmissionPayload): Promise<ContractSubmission> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/submissions',
      { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload) });
  }
  async decideSubmission(contractId: string, submissionId: string, decision: ReviewDecision): Promise<ContractSubmission> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/submissions/' + encodeURIComponent(submissionId) + '/decisions',
      { method: 'POST', body: JSON.stringify(decision) });
  }

  async settlement(contractId: string): Promise<ContractSettlement | null> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/settlement');
  }
  async cancellation(contractId: string): Promise<ContractCancellationRecord | null> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/cancellations');
  }
  async requestCancellation(contractId: string, payload: CancellationRequest): Promise<ContractCancellationRecord> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/cancellations',
      { method: 'POST', body: JSON.stringify(payload) });
  }
  async decideCancellation(contractId: string, cancellationId: string, decision: CancellationDecision): Promise<ContractCancellationRecord> {
    return this.authorized('/contracts/' + encodeURIComponent(contractId) + '/cancellations/' + encodeURIComponent(cancellationId) + '/decisions',
      { method: 'POST', body: JSON.stringify({ decision }) });
  }

  async taxRecords(page: number, size = 10): Promise<Page<TaxRecord>> {
    return serverPage<TaxRecord>(await this.authorized<unknown>('/marketplace/tax-records?page=' + page + '&size=' + size));
  }

  async taxRecord(taxRecordId: string): Promise<TaxRecord> {
    return this.authorized<TaxRecord>('/marketplace/tax-records/' + encodeURIComponent(taxRecordId));
  }

  async taxRecordForJob(jobId: string): Promise<TaxRecord> {
    return this.authorized<TaxRecord>('/marketplace/tax-records/jobs/' + encodeURIComponent(jobId));
  }

  async syncTaxRecord(taxRecordId: string): Promise<TaxRecord> {
    return this.authorized<TaxRecord>('/marketplace/tax-records/' + encodeURIComponent(taxRecordId) + '/sync',
      { method: 'POST' });
  }

  async retryTaxExport(taxRecordId: string): Promise<TaxRecord> {
    return this.authorized<TaxRecord>('/marketplace/tax-records/' + encodeURIComponent(taxRecordId) + '/retry-export',
      { method: 'POST' });
  }

  async notifications(page: number, size = 10): Promise<Page<MarketplaceNotification>> {
    return serverPage<MarketplaceNotification>(
      await this.authorized<unknown>('/notifications?page=' + page + '&size=' + size));
  }

  async markNotificationRead(id: string): Promise<MarketplaceNotification> {
    return this.authorized<MarketplaceNotification>('/notifications/' + encodeURIComponent(id) + '/read',
      { method: 'PATCH' });
  }

  private async authorizedBlob(path: string): Promise<Blob> {
    if (!this.accessToken) throw new ApiError('Phiên đăng nhập đã hết hạn.', 401);
    const request = async (): Promise<Response> => {
      const headers = new Headers({ Authorization: 'Bearer ' + this.accessToken });
      try {
        return await fetch(API_ROOT + path, { headers, credentials: 'include' });
      } catch {
        throw new ApiError('Không thể kết nối Marketplace. Kiểm tra máy chủ và thử lại.', 0);
      }
    };
    let response = await request();
    if (response.status === 401) {
      try {
        await this.restore();
        response = await request();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          this.clear();
          this.onSessionExpired?.();
        }
        throw error;
      }
    }
    if (!response.ok || response.headers.get('content-type')?.includes('application/json')) {
      try {
        await envelope<unknown>(response);
        throw new ApiError('Máy chủ không trả tệp chứng từ hợp lệ.', 0);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          this.clear();
          this.onSessionExpired?.();
        }
        throw error;
      }
    }
    return response.blob();
  }

  async downloadTaxFile(taxRecordId: string, format: 'pdf' | 'xml'): Promise<Blob> {
    return this.authorizedBlob('/marketplace/tax-records/' + encodeURIComponent(taxRecordId) + '/' + format);
  }

}

export const api = new MarketplaceApi();
