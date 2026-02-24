import request from './request';
import type {
  Result,
  Page,
  CreateApplicationRequest,
  UpdateApplicationStatusRequest,
  ApplicationResponse,
  ApplicationQueryParams,
} from '../types';

export const applicationApi = {
  create: (data: CreateApplicationRequest) =>
    request.post<Result<number>>('/applications', data),

  updateStatus: (id: number, data: UpdateApplicationStatusRequest) =>
    request.put<Result<null>>(`/applications/${id}/status`, data),

  getById: (id: number) =>
    request.get<Result<ApplicationResponse>>(`/applications/${id}`),

  listByJob: (jobId: number, params?: { pageNum?: number; pageSize?: number }) =>
    request.get<Result<Page<ApplicationResponse>>>(`/applications/job/${jobId}`, { params }),

  listByCandidate: (candidateId: number, params?: { pageNum?: number; pageSize?: number }) =>
    request.get<Result<Page<ApplicationResponse>>>(`/applications/candidate/${candidateId}`, { params }),

  list: (params?: ApplicationQueryParams) =>
    request.get<Result<Page<ApplicationResponse>>>('/applications', { params }),
};
