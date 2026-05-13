import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// umi must be mocked before page import (hoisted)
vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}));

vi.mock('@/services/auth', () => ({
  login: vi.fn(),
  me: vi.fn(),
}));

vi.mock('@/utils/auth', () => ({
  setTokens: vi.fn(),
  getCurrentUser: vi.fn(() => null),
  getAccessToken: vi.fn(() => null),
}));

import LoginPage from '@/pages/Login';
import { history } from 'umi';
import { login, me } from '@/services/auth';

const mockHistoryPush = vi.mocked(history.push);
const mockLogin = vi.mocked(login);
const mockMe = vi.mocked(me);

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLogin.mockResolvedValue({
      accessToken: 'tok',
      refreshToken: 'ref',
      accessExpiresAt: 0,
      refreshExpiresAt: 0,
      user: { id: '1', email: 'a@b.com', displayName: 'A', role: 'AGENT' },
    });
    mockMe.mockResolvedValue({ id: '1', email: 'a@b.com', displayName: 'A', role: 'AGENT' });
  });

  it('renders email and password fields', () => {
    render(<LoginPage />);
    expect(screen.getByPlaceholderText('agent@example.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Password')).toBeInTheDocument();
  });

  it('calls login() with entered credentials on submit', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText('agent@example.com'), 'test@example.com');
    await user.type(screen.getByPlaceholderText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('test@example.com', 'secret123');
    });
  });

  it('redirects to /desk after successful login', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText('agent@example.com'), 'test@example.com');
    await user.type(screen.getByPlaceholderText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => {
      expect(mockHistoryPush).toHaveBeenCalledWith('/desk');
    });
  });
});
