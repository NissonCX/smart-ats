import request from './request';
import type {
  Result,
  ScheduleInterviewRequest,
  SubmitFeedbackRequest,
  InterviewResponse,
} from '../types';

export const interviewApi = {
  schedule: (data: ScheduleInterviewRequest) =>
    request.post<Result<number>>('/interviews', data),

  submitFeedback: (id: number, data: SubmitFeedbackRequest) =>
    request.put<Result<null>>(`/interviews/${id}/feedback`, data),

  cancel: (id: number) =>
    request.post<Result<null>>(`/interviews/${id}/cancel`),

  getById: (id: number) =>
    request.get<Result<InterviewResponse>>(`/interviews/${id}`),

  listByApplication: (applicationId: number) =>
    request.get<Result<InterviewResponse[]>>(`/interviews/application/${applicationId}`),
};
