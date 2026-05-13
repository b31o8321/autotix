// auth REST client - matches AuthController + UserAdminController
import { request } from '@/utils/request';

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
  refreshExpiresAt: number;
  user: UserInfo;
}

export interface UserInfo {
  id: string;
  email: string;
  displayName: string;
  role: 'ADMIN' | 'AGENT' | 'VIEWER';
}

export interface UserDTO extends UserInfo {
  enabled: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

// TODO: implement
export async function login(email: string, password: string): Promise<LoginResponse> {
  return request('/api/auth/login', { method: 'POST', data: { email, password } });
}

// TODO: implement
export async function refresh(refreshToken: string): Promise<LoginResponse> {
  return request('/api/auth/refresh', { method: 'POST', data: { refreshToken } });
}

// TODO: implement
export async function me(): Promise<UserInfo> {
  return request('/api/auth/me', { method: 'GET' });
}

// TODO: implement
export async function changePassword(currentPassword: string, newPassword: string) {
  return request('/api/auth/password', {
    method: 'POST',
    data: { currentPassword, newPassword },
  });
}

// admin user management
// TODO: implement
export async function listUsers(): Promise<UserDTO[]> {
  return request('/api/admin/users', { method: 'GET' });
}

// TODO: implement
export async function createUser(payload: {
  email: string; displayName: string; password: string; role: string;
}): Promise<UserDTO> {
  return request('/api/admin/users', { method: 'POST', data: payload });
}

// TODO: implement
export async function changeUserRole(userId: string, role: string) {
  return request(`/api/admin/users/${userId}/role`, { method: 'PUT', params: { role } });
}

// TODO: implement
export async function disableUser(userId: string) {
  return request(`/api/admin/users/${userId}`, { method: 'DELETE' });
}
