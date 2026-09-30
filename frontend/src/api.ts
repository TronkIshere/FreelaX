import type { DiscoverJob, DiscoveryFilters, Job, Page, User, UserType } from './types';

const API_ROOT = '/api/v1';

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
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
  return { id: data.id, email: data.email, displayName: data.displayName, userType: role as UserType };
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
    throw new ApiError(response.status === 401 ? 'Phiên đăng nhập đã hết hạn.' : response.ok ? 'Máy chủ trả dữ liệu không hợp lệ.' : 'Không thể kết nối dịch vụ.', response.status);
  }
  const payload = record(value);
  if (!response.ok || !payload || payload.code !== 200) {
    const serverMessage = payload?.message || payload?.error;
    const message = typeof serverMessage === 'string' && serverMessage.trim()
      ? serverMessage : 'Yêu cầu không thành công. Vui lòng thử lại.';
    throw new ApiError(message, response.status);
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
}

export const api = new MarketplaceApi();
