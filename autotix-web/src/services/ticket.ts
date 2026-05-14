// ticket REST client - matches DeskController
import { request } from '@/utils/request';

export type TicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type TicketType = 'QUESTION' | 'INCIDENT' | 'PROBLEM' | 'TASK';

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
  // Slice 9: priority + type
  priority?: TicketPriority;
  type?: TicketType;
  // Slice 10: SLA fields
  firstResponseAt?: string;
  firstHumanResponseAt?: string;
  firstResponseDueAt?: string;
  resolutionDueAt?: string;
  slaBreached?: boolean;
  firstResponseRemainingMs?: number;
  resolutionRemainingMs?: number;
}

/** Slice 11: file attachment metadata */
export interface AttachmentDTO {
  id: number;
  key: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  uploadedAt: string;
  downloadUrl: string;
}

export interface MessageDTO {
  direction: 'INBOUND' | 'OUTBOUND';
  author: string;
  content: string;
  occurredAt: string;
  /** Slice 9: PUBLIC (default) or INTERNAL */
  visibility?: 'PUBLIC' | 'INTERNAL';
  /** Slice 11: file attachments linked to this message */
  attachments?: AttachmentDTO[];
}

export interface TicketActivity {
  id: number;
  ticketId: string;
  actor: string;
  action: string;
  details?: string;
  occurredAt: string;
}

export interface ListParams {
  status?: string;
  channelId?: string;
  assignee?: string;
  q?: string;
  priority?: string;
  offset?: number;
  limit?: number;
}

export async function listTickets(params: ListParams): Promise<TicketDTO[]> {
  return request('/api/desk/tickets', { method: 'GET', params });
}

export async function getTicket(ticketId: string): Promise<TicketDTO> {
  return request(`/api/desk/tickets/${ticketId}`, { method: 'GET' });
}

export async function replyTicket(
  ticketId: string,
  content: string,
  closeAfter = false,
  internal = false,
  attachmentIds?: number[],
) {
  return request(`/api/desk/tickets/${ticketId}/reply`, {
    method: 'POST',
    data: { content, closeAfter, internal, attachmentIds },
  });
}

/** Slice 11: upload a file attachment before sending reply */
export async function uploadAttachment(ticketId: string, file: File): Promise<AttachmentDTO> {
  const formData = new FormData();
  formData.append('file', file);
  return request(`/api/desk/tickets/${ticketId}/attachments`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
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

export async function changeTicketPriority(ticketId: string, value: TicketPriority) {
  return request(`/api/desk/tickets/${ticketId}/priority`, {
    method: 'PUT',
    params: { value },
  });
}

export async function changeTicketType(ticketId: string, value: TicketType) {
  return request(`/api/desk/tickets/${ticketId}/type`, {
    method: 'PUT',
    params: { value },
  });
}

export async function listTicketActivity(
  ticketId: string,
  offset = 0,
  limit = 100,
): Promise<TicketActivity[]> {
  return request(`/api/desk/tickets/${ticketId}/activity`, {
    method: 'GET',
    params: { offset, limit },
  });
}
