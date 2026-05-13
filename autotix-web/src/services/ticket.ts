// ticket REST client - matches DeskController
import { request } from '@/utils/request';

export interface TicketDTO {
  id: string;
  channelId: string;
  platform: string;
  channelType: 'EMAIL' | 'CHAT';
  externalNativeId: string;
  subject: string;
  customerIdentifier: string;
  customerName?: string;
  status: 'OPEN' | 'PENDING' | 'ASSIGNED' | 'CLOSED';
  assigneeId?: string;
  tags: string[];
  messages?: MessageDTO[];
  createdAt: string;
  updatedAt: string;
}

export interface MessageDTO {
  direction: 'INBOUND' | 'OUTBOUND';
  author: string;
  content: string;
  occurredAt: string;
}

export interface ListParams {
  status?: string;
  channelId?: string;
  assignee?: string;
  q?: string;
  offset?: number;
  limit?: number;
}

// TODO: implement
export async function listTickets(params: ListParams): Promise<TicketDTO[]> {
  return request('/api/desk/tickets', { method: 'GET', params });
}

// TODO: implement
export async function getTicket(ticketId: string): Promise<TicketDTO> {
  return request(`/api/desk/tickets/${ticketId}`, { method: 'GET' });
}

// TODO: implement
export async function replyTicket(ticketId: string, content: string, closeAfter = false) {
  return request(`/api/desk/tickets/${ticketId}/reply`, {
    method: 'POST',
    data: { content, closeAfter },
  });
}

// TODO: implement
export async function assignTicket(ticketId: string, agentId: string) {
  return request(`/api/desk/tickets/${ticketId}/assign`, {
    method: 'POST',
    params: { agentId },
  });
}

// TODO: implement
export async function closeTicket(ticketId: string) {
  return request(`/api/desk/tickets/${ticketId}/close`, { method: 'POST' });
}
