import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
  default: vi.fn(),
}));

import { request } from '@/utils/request';
import { login, me, changePassword, listUsers } from '@/services/auth';

const mockRequest = vi.mocked(request);

describe('auth service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequest.mockResolvedValue({});
  });

  it('login POSTs to /api/auth/login with email and password', async () => {
    await login('user@test.com', 'pass123');
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        data: { email: 'user@test.com', password: 'pass123' },
      }),
    );
  });

  it('me GETs /api/auth/me', async () => {
    await me();
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('changePassword POSTs current and new password', async () => {
    await changePassword('old', 'new');
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/auth/password',
      expect.objectContaining({
        method: 'POST',
        data: { currentPassword: 'old', newPassword: 'new' },
      }),
    );
  });

  it('listUsers GETs /api/admin/users', async () => {
    mockRequest.mockResolvedValue([]);
    await listUsers();
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/users',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
