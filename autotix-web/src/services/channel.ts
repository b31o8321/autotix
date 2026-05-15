// channel admin REST client - matches ChannelAdminController
import { request } from '@/utils/request';

export interface ChannelDTO {
  id: string;
  platform: string;
  channelType: 'EMAIL' | 'CHAT';
  displayName: string;
  webhookToken: string;
  enabled: boolean;
  autoReplyEnabled: boolean;
  connectedAt: string;
}

export async function listChannels(platform?: string): Promise<ChannelDTO[]> {
  return request('/api/admin/channels', {
    method: 'GET',
    params: platform ? { platform } : undefined,
  });
}

// TODO: implement
export async function startOAuth(platform: string, channelType: string, displayName: string) {
  return request('/api/admin/channels/oauth/start', {
    method: 'POST',
    data: { platform, channelType, displayName },
  });
}

// TODO: implement
export async function connectWithApiKey(payload: {
  platform: string;
  channelType: string;
  displayName: string;
  credentials: Record<string, string>;
}) {
  return request('/api/admin/channels/connect-api-key', { method: 'POST', data: payload });
}

// TODO: implement
export async function disconnectChannel(channelId: string, hardDelete = false) {
  return request(`/api/admin/channels/${channelId}`, {
    method: 'DELETE',
    params: { hardDelete },
  });
}

// TODO: implement
export async function setAutoReply(channelId: string, enabled: boolean) {
  return request(`/api/admin/channels/${channelId}/auto-reply`, {
    method: 'PUT',
    params: { enabled },
  });
}

// TODO: implement
export async function rotateWebhook(channelId: string) {
  return request(`/api/admin/channels/${channelId}/rotate-webhook`, { method: 'POST' });
}

export async function renameChannel(channelId: string, displayName: string) {
  return request(`/api/admin/channels/${channelId}/name`, {
    method: 'PUT',
    params: { displayName },
  });
}
