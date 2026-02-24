import request from './request';
import type {
  Result,
  Page,
  CandidateResponse,
  CandidateUpdateRequest,
  CandidateQueryParams,
  SmartSearchRequest,
  SmartSearchResponse,
} from '../types';

export const candidateApi = {
  getByResumeId: (resumeId: number) =>
    request.get<Result<CandidateResponse>>(`/candidates/resume/${resumeId}`),

  getById: (id: number) =>
    request.get<Result<CandidateResponse>>(`/candidates/${id}`),

  update: (id: number, data: CandidateUpdateRequest) =>
    request.put<Result<CandidateResponse>>(`/candidates/${id}`, data),

  delete: (id: number) =>
    request.delete<Result<null>>(`/candidates/${id}`),

  list: (params?: CandidateQueryParams) =>
    request.get<Result<Page<CandidateResponse>>>('/candidates', { params }),

  smartSearch: (data: SmartSearchRequest) =>
    request.post<Result<SmartSearchResponse>>('/candidates/smart-search', data),
};
