import { request } from '@/utils/request';

export interface TagDTO {
  id: string;
  name: string;
  color: string;
  category?: string;
  createdAt: string;
}

export async function getTagSuggestions(): Promise<TagDTO[]> {
  return request('/api/desk/tags/suggestions', { method: 'GET' });
}
