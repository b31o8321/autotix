import { request } from '@/utils/request';

export interface RuleCondition {
  field: string;
  op: string;
  value: string;
}

export interface RuleAction {
  type: string;
  params: Record<string, string>;
}

export interface AutomationRuleDTO {
  id: string;
  name: string;
  priority: number;
  enabled: boolean;
  conditions: RuleCondition[];
  actions: RuleAction[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateRulePayload {
  name: string;
  priority: number;
  enabled: boolean;
  conditions: RuleCondition[];
  actions: RuleAction[];
}

export async function listRules(): Promise<AutomationRuleDTO[]> {
  return request('/api/admin/automation/rules', { method: 'GET' });
}

export async function createRule(payload: CreateRulePayload): Promise<AutomationRuleDTO> {
  return request('/api/admin/automation/rules', { method: 'POST', data: payload });
}

export async function updateRule(id: string, payload: Partial<CreateRulePayload>): Promise<AutomationRuleDTO> {
  return request(`/api/admin/automation/rules/${id}`, { method: 'PUT', data: payload });
}

export async function deleteRule(id: string): Promise<void> {
  return request(`/api/admin/automation/rules/${id}`, { method: 'DELETE' });
}

export async function setRuleEnabled(id: string, enabled: boolean): Promise<AutomationRuleDTO> {
  return request(`/api/admin/automation/rules/${id}/enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}
