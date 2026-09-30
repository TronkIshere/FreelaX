export type UserType = 'CLIENT' | 'FREELANCER';

export interface User {
  id: string;
  email: string;
  displayName: string;
  userType: UserType;
}

export interface Page<T> {
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  data: T[];
}

export interface Job {
  id: string;
  title: string;
  description: string;
  budgetUsd: number;
  clientUserId: string;
  freelancerId: string | null;
  status: string;
  createdAt: string;
}

export interface DiscoverJob {
  id: string;
  title: string;
  description: string;
  budgetUsd: number;
  status: string;
  client: { id: string; displayName: string };
  hasApplied: boolean;
  applicationId: string | null;
  applicationStatus: string | null;
  createdAt: string;
}

export type DiscoverySort = 'NEWEST' | 'BUDGET_ASC' | 'BUDGET_DESC';
export type ApplicationFilter = 'ALL' | 'APPLIED' | 'NOT_APPLIED';

export interface DiscoveryFilters {
  keyword: string;
  minBudgetUsd: string;
  maxBudgetUsd: string;
  sort: DiscoverySort;
  application: ApplicationFilter;
}
