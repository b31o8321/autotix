import { request } from '@/utils/request';

export interface CustomerDTO {
  id: string;
  displayName?: string;
  primaryEmail?: string;
  identifierCount: number;
  createdAt: string;
}

export interface CustomerIdentifierDTO {
  type: string;
  value: string;
  channelId?: string;
  firstSeenAt: string;
}

export interface CustomerDetailDTO extends CustomerDTO {
  identifiers: CustomerIdentifierDTO[];
  attributes: Record<string, string>;
  recentTicketIds: string[];
}

export async function getCustomer(id: string): Promise<CustomerDetailDTO> {
  return request(`/api/admin/customers/${id}`, { method: 'GET' });
}

export async function listCustomers(q?: string): Promise<CustomerDTO[]> {
  return request('/api/admin/customers', { method: 'GET', params: q ? { q } : {} });
}
