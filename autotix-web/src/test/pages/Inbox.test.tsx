import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}));

vi.mock('@/services/inbox', () => ({
  subscribeInbox: vi.fn(),
}));

vi.mock('@/utils/auth', () => ({
  getAccessToken: vi.fn(() => 'test-token'),
  getCurrentUser: vi.fn(() => null),
}));

import InboxPage from '@/pages/Inbox';
import { subscribeInbox } from '@/services/inbox';
import type { InboxHandler } from '@/services/inbox';

const mockSubscribeInbox = vi.mocked(subscribeInbox);

describe('InboxPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSubscribeInbox.mockReturnValue(() => {});
  });

  it('calls subscribeInbox with the stored token on mount', () => {
    render(<InboxPage />);
    expect(mockSubscribeInbox).toHaveBeenCalledWith('test-token', expect.any(Function));
  });

  it('displays events delivered via the handler', async () => {
    let capturedHandler: InboxHandler | null = null;
    mockSubscribeInbox.mockImplementation((_token: string, handler: InboxHandler) => {
      capturedHandler = handler;
      return () => {};
    });

    render(<InboxPage />);

    act(() => {
      capturedHandler!({
        kind: 'AI_REPLIED',
        ticketId: 'ticket-42',
        channelId: 'ch-1',
        summary: 'AI auto-replied to customer',
        occurredAt: new Date().toISOString(),
      });
    });

    expect(screen.getByText('AI auto-replied to customer')).toBeInTheDocument();
    expect(screen.getByText('AI_REPLIED')).toBeInTheDocument();
  });

  it('calls unsubscribe on unmount', () => {
    const unsub = vi.fn();
    mockSubscribeInbox.mockReturnValue(unsub);

    const { unmount } = render(<InboxPage />);
    unmount();
    expect(unsub).toHaveBeenCalled();
  });
});
