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
  createdAt: string | null;
  updatedAt?: string | null;
  checkoutOrderId?: string | null;
  taxExportStatus?: string | null;
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
  createdAt: string | null;
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


export type JobStatus = 'OPEN' | 'IN_PROGRESS' | 'SUBMITTED_FOR_REVIEW' | 'REVISION_REQUESTED' | 'COMPLETED' | 'CANCELLED';
export type JobApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
export type JobSubmissionStatus = 'SUBMITTED' | 'REVISION_REQUESTED' | 'APPROVED';
export interface JobSubmission {
  id: string;
  jobId: string;
  freelancerId: string;
  version: number;
  summary: string;
  deliverableUrl: string | null;
  status: JobSubmissionStatus;
  reviewerFeedback: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}
export interface JobApplication {
  id: string; jobId: string; freelancerId: string; status: JobApplicationStatus; createdAt: string | null;
}
export interface MyApplication {
  id: string; status: JobApplicationStatus; createdAt: string | null; updatedAt: string | null;
  job: { id: string; title: string; description: string; budgetUsd: number; status: JobStatus;
    clientDisplayName: string | null; createdAt: string | null };
}
