import request from './request';
import type { Result, AnalyticsQueryRequest, RecruitmentOverviewDTO } from '../types';

export const analyticsApi = {
  getOverview: (params?: AnalyticsQueryRequest) =>
    request.get<Result<RecruitmentOverviewDTO>>('/analytics/overview', { params }),

  getSseStatus: () =>
    request.get<Result<number>>('/analytics/sse-status'),
};

/**
 * SSE stream URL — used directly by EventSource (not via axios)
 */
export const getAnalyticsSseUrl = () => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1';
  return `${baseUrl}/analytics/stream`;
};
