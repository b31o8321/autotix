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
  status: 'NEW' | 'OPEN' | 'WAITING_ON_CUSTOMER' | 'WAITING_ON_INTERNAL' | 'SOLVED' | 'CLOSED' | 'SPAM';
  assigneeId?: string;
  tags: string[];
  messages?: MessageDTO[];
  createdAt: string;
  updatedAt: string;
  // Slice 8: new fields
  solvedAt?: string;
  closedAt?: string;
  parentTicketId?: string;
  reopenCount?: number;
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

export async function listTickets(params: ListParams): Promise<TicketDTO[]> {
  return request('/api/desk/tickets', { method: 'GET', params });
}

export async function getTicket(ticketId: string): Promise<TicketDTO> {
  return request(`/api/desk/tickets/${ticketId}`, { method: 'GET' });
}

export async function replyTicket(ticketId: string, content: string, closeAfter = false) {
  return request(`/api/desk/tickets/${ticketId}/reply`, {
    method: 'POST',
    data: { content, closeAfter },
  });
}

export async function assignTicket(ticketId: string, agentId: string) {
  return request(`/api/desk/tickets/${ticketId}/assign`, {
    method: 'POST',
    params: { agentId },
  });
}

/** Primary agent "close" action — transitions to SOLVED (customer can reopen). */
export async function solveTicket(ticketId: string) {
  return request(`/api/desk/tickets/${ticketId}/solve`, { method: 'POST' });
}

/** Permanent close (admin) — transitions to CLOSED (terminal). */
export async function closeTicket(ticketId: string) {
  return request(`/api/desk/tickets/${ticketId}/close`, { method: 'POST' });
}
