import request from './request';
import type {
  Result,
  Page,
  CreateJobRequest,
  UpdateJobRequest,
  JobResponse,
  JobQueryParams,
} from '../types';

export const jobApi = {
  create: (data: CreateJobRequest) =>
    request.post<Result<number>>('/jobs', data),

  update: (data: UpdateJobRequest) =>
    request.put<Result<null>>('/jobs', data),

  getById: (id: number) =>
    request.get<Result<JobResponse>>(`/jobs/${id}`),

  list: (params?: JobQueryParams) =>
    request.get<Result<Page<JobResponse>>>('/jobs', { params }),

  publish: (id: number) =>
    request.post<Result<null>>(`/jobs/${id}/publish`),

  close: (id: number) =>
    request.post<Result<null>>(`/jobs/${id}/close`),

  delete: (id: number) =>
    request.delete<Result<null>>(`/jobs/${id}`),

  hot: (limit = 10) =>
    request.get<Result<JobResponse[]>>('/jobs/hot', { params: { limit } }),
};
