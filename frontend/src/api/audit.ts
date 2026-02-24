import request from './request';
import type { Result, Page, AuditLogQueryRequest, AuditLogResponse } from '../types';

export const auditApi = {
  list: (params?: AuditLogQueryRequest) =>
    request.get<Result<Page<AuditLogResponse>>>('/audit-logs', { params }),
};
