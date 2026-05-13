import type { LoginResponse, UserInfo } from '@/services/auth';

const KEY_ACCESS = 'autotix.access_token';
const KEY_REFRESH = 'autotix.refresh_token';
const KEY_USER = 'autotix.user';

export function getAccessToken(): string | null {
  return localStorage.getItem(KEY_ACCESS);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(KEY_REFRESH);
}

export function setTokens(response: LoginResponse): void {
  localStorage.setItem(KEY_ACCESS, response.accessToken);
  localStorage.setItem(KEY_REFRESH, response.refreshToken);
  localStorage.setItem(KEY_USER, JSON.stringify(response.user));
}

export function clearTokens(): void {
  localStorage.removeItem(KEY_ACCESS);
  localStorage.removeItem(KEY_REFRESH);
  localStorage.removeItem(KEY_USER);
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(KEY_USER);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function hasRole(role: string): boolean {
  const user = getCurrentUser();
  if (!user) return false;
  return user.role === role;
}
