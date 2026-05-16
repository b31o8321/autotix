import { request } from '@/utils/request';

export interface MacroDTO {
  id: number;
  name: string;
  bodyMarkdown: string;
  category?: string;
  availableTo: 'ADMIN_ONLY' | 'AGENT' | 'AI';
  usageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMacroRequest {
  name: string;
  bodyMarkdown: string;
  category?: string;
  availableTo: string;
}

export interface UpdateMacroRequest {
  name: string;
  bodyMarkdown: string;
  category?: string;
  availableTo: string;
}

// ── Desk (agent) endpoints ────────────────────────────────────────────────────

export async function listMacros(): Promise<MacroDTO[]> {
  return request('/api/desk/macros', { method: 'GET' });
}

export async function recordMacroUsage(id: number): Promise<void> {
  return request(`/api/desk/macros/${id}/use`, { method: 'POST' });
}

// ── Admin endpoints ───────────────────────────────────────────────────────────

export async function listAdminMacros(): Promise<MacroDTO[]> {
  return request('/api/admin/macros', { method: 'GET' });
}

export async function createMacro(data: CreateMacroRequest): Promise<MacroDTO> {
  return request('/api/admin/macros', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function updateMacro(id: number, data: UpdateMacroRequest): Promise<MacroDTO> {
  return request(`/api/admin/macros/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function deleteMacro(id: number): Promise<void> {
  return request(`/api/admin/macros/${id}`, { method: 'DELETE' });
}
