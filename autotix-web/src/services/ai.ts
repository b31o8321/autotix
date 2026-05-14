// AI config REST client + AI draft service - matches AIConfigController & DeskController
import { request } from '@/utils/request';

export interface AIConfigDTO {
  endpoint: string;
  apiKey: string;
  model: string;
  systemPrompt: string;
  timeoutSeconds: number;
  maxRetries: number;
}

export interface AITestResult {
  ok: boolean;
  latencyMs: number;
  sampleReply?: string;
  error?: string;
}

// TODO: implement
export async function getAIConfig(): Promise<AIConfigDTO> {
  return request('/api/admin/ai', { method: 'GET' });
}

// TODO: implement
export async function updateAIConfig(dto: AIConfigDTO) {
  return request('/api/admin/ai', { method: 'PUT', data: dto });
}

// TODO: implement
export async function testAIConfig(dto: AIConfigDTO): Promise<AITestResult> {
  return request('/api/admin/ai/test', { method: 'POST', data: dto });
}

// ─── AI Draft (Slice 15) ────────────────────────────────────────────────────

export interface DraftDTO {
  reply: string;
  action: string;
  suggestedTags: string[];
  latencyMs: number;
  modelName: string;
}

export type StyleHint = 'DEFAULT' | 'FRIENDLIER' | 'FORMAL' | 'SHORTER';

export async function generateAIDraft(ticketId: string, styleHint: StyleHint = 'DEFAULT'): Promise<DraftDTO> {
  return request(`/api/desk/tickets/${ticketId}/ai-draft`, {
    method: 'POST',
    data: { styleHint },
  });
}
