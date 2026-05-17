import { request } from '@/utils/request';

export type NotificationEventKind = 'SLA_BREACHED';
export type NotificationChannel = 'EMAIL' | 'SLACK_WEBHOOK';

export interface NotificationRouteDTO {
  id: number;
  name: string;
  eventKind: NotificationEventKind;
  channel: NotificationChannel;
  configJson: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoutePayload {
  name: string;
  eventKind: NotificationEventKind;
  channel: NotificationChannel;
  configJson: string;
  enabled: boolean;
}

export async function listRoutes(): Promise<NotificationRouteDTO[]> {
  return request('/api/admin/notifications/routes', { method: 'GET' });
}

export async function createRoute(payload: CreateRoutePayload): Promise<NotificationRouteDTO> {
  return request('/api/admin/notifications/routes', { method: 'POST', data: payload });
}

export async function updateRoute(id: number, payload: Partial<CreateRoutePayload>): Promise<NotificationRouteDTO> {
  return request(`/api/admin/notifications/routes/${id}`, { method: 'PUT', data: payload });
}

export async function deleteRoute(id: number): Promise<void> {
  return request(`/api/admin/notifications/routes/${id}`, { method: 'DELETE' });
}

export async function testRoute(id: number): Promise<{ success: boolean; message: string; channel: string }> {
  return request(`/api/admin/notifications/routes/test/${id}`, { method: 'GET' });
}
