import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}));

vi.mock('@/services/ticket', () => ({
  listTickets: vi.fn(),
}));

vi.mock('@/services/channel', () => ({
  listChannels: vi.fn(),
}));

import DeskPage from '@/pages/Desk';
import { listTickets } from '@/services/ticket';
import { listChannels } from '@/services/channel';

const mockListTickets = vi.mocked(listTickets);
const mockListChannels = vi.mocked(listChannels);

const sampleTickets = [
  {
    id: 'ticket-1',
    channelId: 'ch-1',
    platform: 'ZENDESK',
    channelType: 'EMAIL' as const,
    externalNativeId: 'ext-1',
    subject: 'First ticket subject',
    customerIdentifier: 'customer@test.com',
    customerName: 'Customer One',
    status: 'OPEN' as const,
    tags: [],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z',
  },
  {
    id: 'ticket-2',
    channelId: 'ch-1',
    platform: 'ZENDESK',
    channelType: 'CHAT' as const,
    externalNativeId: 'ext-2',
    subject: 'Second ticket subject',
    customerIdentifier: 'another@test.com',
    status: 'PENDING' as const,
    tags: [],
    createdAt: '2024-01-02T00:00:00Z',
    updatedAt: '2024-01-02T01:00:00Z',
  },
];

describe('DeskPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListTickets.mockResolvedValue(sampleTickets);
    mockListChannels.mockResolvedValue([]);
  });

  it('renders both ticket subjects in the table', async () => {
    render(<DeskPage />);
    await waitFor(() => {
      expect(screen.getByText('First ticket subject')).toBeInTheDocument();
      expect(screen.getByText('Second ticket subject')).toBeInTheDocument();
    });
  });

  it('shows status tags for each ticket', async () => {
    render(<DeskPage />);
    await waitFor(() => {
      expect(screen.getByText('OPEN')).toBeInTheDocument();
      expect(screen.getByText('PENDING')).toBeInTheDocument();
    });
  });

  it('calls listTickets on mount', async () => {
    render(<DeskPage />);
    await waitFor(() => {
      expect(mockListTickets).toHaveBeenCalledTimes(1);
    });
  });
});
