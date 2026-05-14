// SLA policy REST client - matches SlaPolicyAdminController
import { request } from '@/utils/request';

export interface SlaPolicyDTO {
  id?: string;
  name: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  firstResponseMinutes: number;
  resolutionMinutes: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export async function listSlaPolicies(): Promise<SlaPolicyDTO[]> {
  return request('/api/admin/sla', { method: 'GET' });
}

export async function updateSlaPolicy(
  priority: string,
  payload: Partial<SlaPolicyDTO>,
): Promise<SlaPolicyDTO> {
  return request(`/api/admin/sla/${priority}`, {
    method: 'PUT',
    data: payload,
  });
}
