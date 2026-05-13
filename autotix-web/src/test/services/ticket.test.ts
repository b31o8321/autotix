import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
  default: vi.fn(),
}));

import { request } from '@/utils/request';
import { listTickets, getTicket, replyTicket, closeTicket } from '@/services/ticket';

const mockRequest = vi.mocked(request);

describe('ticket service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequest.mockResolvedValue([]);
  });

  it('listTickets calls /api/desk/tickets with filter params', async () => {
    await listTickets({ status: 'OPEN', limit: 10 });
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/desk/tickets',
      expect.objectContaining({
        method: 'GET',
        params: expect.objectContaining({ status: 'OPEN', limit: 10 }),
      }),
    );
  });

  it('getTicket calls correct URL', async () => {
    mockRequest.mockResolvedValue({ id: 'ticket-1' });
    await getTicket('ticket-1');
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/desk/tickets/ticket-1',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('replyTicket POSTs to correct URL with content and closeAfter', async () => {
    mockRequest.mockResolvedValue({});
    await replyTicket('ticket-1', 'Hello!', true);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/desk/tickets/ticket-1/reply',
      expect.objectContaining({
        method: 'POST',
        data: { content: 'Hello!', closeAfter: true },
      }),
    );
  });

  it('closeTicket POSTs to close endpoint', async () => {
    mockRequest.mockResolvedValue({});
    await closeTicket('ticket-1');
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/desk/tickets/ticket-1/close',
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
