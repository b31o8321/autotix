import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}));

vi.mock('@/services/ticket', () => ({
  listTickets: vi.fn(),
  getTicket: vi.fn(),
  replyTicket: vi.fn(),
  solveTicket: vi.fn(),
  closeTicket: vi.fn(),
  escalateTicket: vi.fn(),
  resumeAi: vi.fn(),
  uploadAttachment: vi.fn(),
}));

vi.mock('@/services/inbox', () => ({
  subscribeInbox: vi.fn(),
}));

vi.mock('@/services/customer', () => ({
  getCustomer: vi.fn(),
}));

vi.mock('@/services/tag', () => ({
  getTagSuggestions: vi.fn(),
}));

vi.mock('@/services/customfield', () => ({
  getCustomFieldSchema: vi.fn(),
}));

vi.mock('@/utils/auth', () => ({
  getAccessToken: vi.fn(() => 'test-token'),
  getCurrentUser: vi.fn(() => ({ id: 'user-1', email: 'agent@test.com', role: 'AGENT' })),
  hasRole: vi.fn((role: string) => role !== 'ADMIN'),
}));

import InboxPage from '@/pages/Inbox';
import { listTickets, getTicket } from '@/services/ticket';
import { subscribeInbox } from '@/services/inbox';
import { getTagSuggestions } from '@/services/tag';
import { getCustomFieldSchema } from '@/services/customfield';

const mockListTickets = vi.mocked(listTickets);
const mockGetTicket = vi.mocked(getTicket);
const mockSubscribeInbox = vi.mocked(subscribeInbox);
const mockGetTagSuggestions = vi.mocked(getTagSuggestions);
const mockGetCustomFieldSchema = vi.mocked(getCustomFieldSchema);

const sampleTickets = [
  {
    id: 'ticket-1',
    channelId: 'ch-1',
    platform: 'EMAIL',
    channelType: 'EMAIL' as const,
    externalNativeId: 'ext-1',
    subject: 'Order issue #1234',
    customerIdentifier: 'alice@example.com',
    customerName: 'Alice',
    status: 'OPEN' as const,
    assigneeId: 'user-1',
    tags: ['refund'],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
  },
  {
    id: 'ticket-2',
    channelId: 'ch-1',
    platform: 'EMAIL',
    channelType: 'EMAIL' as const,
    externalNativeId: 'ext-2',
    subject: 'Refund request',
    customerIdentifier: 'bob@example.com',
    status: 'NEW' as const,
    tags: [],
    createdAt: '2024-01-02T00:00:00Z',
    updatedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
  },
];

const sampleTicketDetail = {
  ...sampleTickets[0],
  messages: [
    {
      direction: 'INBOUND' as const,
      author: 'alice@example.com',
      content: 'Hi, my order has not arrived yet.',
      occurredAt: '2024-01-01T01:00:00Z',
    },
  ],
};

describe('InboxPage (3-column)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListTickets.mockResolvedValue(sampleTickets);
    mockGetTicket.mockResolvedValue(sampleTicketDetail);
    mockSubscribeInbox.mockReturnValue(() => {});
    mockGetTagSuggestions.mockResolvedValue([]);
    mockGetCustomFieldSchema.mockResolvedValue([]);
  });

  it('calls subscribeInbox on mount', () => {
    render(<InboxPage />);
    expect(mockSubscribeInbox).toHaveBeenCalledWith('test-token', expect.any(Function));
  });

  it('renders ticket list after fetch', async () => {
    render(<InboxPage />);
    await waitFor(() => {
      expect(screen.getByText('Order issue #1234')).toBeInTheDocument();
    });
  });

  it('shows empty state when no ticket is selected', async () => {
    render(<InboxPage />);
    expect(screen.getByText('Select a ticket from the list')).toBeInTheDocument();
  });

  it('loads ticket detail when row is clicked', async () => {
    render(<InboxPage />);
    await waitFor(() => {
      expect(screen.getByText('Order issue #1234')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Order issue #1234'));

    await waitFor(() => {
      expect(mockGetTicket).toHaveBeenCalledWith('ticket-1');
    });
  });

  it('shows message content after ticket is loaded', async () => {
    render(<InboxPage />);
    await waitFor(() => {
      expect(screen.getByText('Order issue #1234')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Order issue #1234'));

    await waitFor(() => {
      expect(screen.getByText('Hi, my order has not arrived yet.')).toBeInTheDocument();
    });
  });

  it('calls unsubscribe on unmount', () => {
    const unsub = vi.fn();
    mockSubscribeInbox.mockReturnValue(unsub);
    const { unmount } = render(<InboxPage />);
    unmount();
    expect(unsub).toHaveBeenCalled();
  });

  it('shows smart view tabs', async () => {
    render(<InboxPage />);
    // "Mine" and "Unassigned" only appear in tabs
    expect(screen.getByText(/Mine/)).toBeInTheDocument();
    expect(screen.getByText(/Unassigned/)).toBeInTheDocument();
    // "Open" and "All" appear in both tabs and segmented filter; verify at least one
    expect(screen.getAllByText(/^Open$/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/^All$/).length).toBeGreaterThan(0);
  });
});
