import { describe, it, expect, beforeEach } from 'vitest';
import { getAccessToken, setTokens, clearTokens, getCurrentUser, hasRole } from '@/utils/auth';
import type { LoginResponse } from '@/services/auth';

const mockLoginResponse: LoginResponse = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  accessExpiresAt: Date.now() + 3600000,
  refreshExpiresAt: Date.now() + 86400000,
  user: {
    id: 'user-1',
    email: 'admin@test.com',
    displayName: 'Admin User',
    role: 'ADMIN',
  },
};

beforeEach(() => {
  localStorage.clear();
});

describe('auth utils', () => {
  it('setTokens stores accessToken; getAccessToken retrieves it', () => {
    setTokens(mockLoginResponse);
    expect(getAccessToken()).toBe('test-access-token');
  });

  it('clearTokens removes all stored token keys', () => {
    setTokens(mockLoginResponse);
    clearTokens();
    expect(getAccessToken()).toBeNull();
    expect(getCurrentUser()).toBeNull();
  });

  it('getCurrentUser returns parsed user after setTokens', () => {
    setTokens(mockLoginResponse);
    const user = getCurrentUser();
    expect(user?.email).toBe('admin@test.com');
    expect(user?.role).toBe('ADMIN');
  });

  it('hasRole returns true when user has matching role', () => {
    setTokens(mockLoginResponse);
    expect(hasRole('ADMIN')).toBe(true);
    expect(hasRole('AGENT')).toBe(false);
  });

  it('hasRole returns false when no user is stored', () => {
    expect(hasRole('ADMIN')).toBe(false);
  });
});
