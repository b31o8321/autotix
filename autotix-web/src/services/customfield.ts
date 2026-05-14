import { request } from '@/utils/request';

export interface CustomFieldDTO {
  id: string;
  name: string;
  key: string;
  type: 'TEXT' | 'NUMBER' | 'DATE';
  appliesTo: 'TICKET' | 'CUSTOMER';
  required: boolean;
  displayOrder: number;
}

export async function getCustomFieldSchema(
  appliesTo: 'TICKET' | 'CUSTOMER',
): Promise<CustomFieldDTO[]> {
  return request('/api/desk/custom-fields', { method: 'GET', params: { appliesTo } });
}
