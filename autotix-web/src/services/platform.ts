// platform admin REST client — matches PlatformAdminController
import { request } from '@/utils/request';

export interface AuthFieldDTO {
  key: string;
  label: string;
  type: 'string' | 'password' | 'number' | 'boolean' | 'select';
  options?: string[];
  required: boolean;
  placeholder?: string;
  help?: string;
  defaultValue?: string;
}

export interface PlatformDescriptorDTO {
  platform: string;
  displayName: string;
  category: string;
  defaultChannelType: string;
  allowedChannelTypes: string[];
  authMethod: 'API_KEY' | 'APP_CREDENTIALS' | 'EMAIL_BASIC' | 'OAUTH2' | 'NONE';
  authFields: AuthFieldDTO[];
  functional: boolean;
  docsUrl?: string;
}

export async function getPlatforms(): Promise<PlatformDescriptorDTO[]> {
  return request('/api/admin/platforms', { method: 'GET' });
}
