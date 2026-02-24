import request from './request';
import type { Result, WebhookCreateRequest, WebhookResponse } from '../types';

export const webhookApi = {
  create: (data: WebhookCreateRequest) =>
    request.post<Result<WebhookResponse>>('/webhooks', data),

  list: () =>
    request.get<Result<WebhookResponse[]>>('/webhooks'),

  delete: (id: number) =>
    request.delete<Result<null>>(`/webhooks/${id}`),

  test: (id: number) =>
    request.post<Result<boolean>>(`/webhooks/${id}/test`),
};
