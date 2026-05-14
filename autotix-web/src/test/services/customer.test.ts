import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
  default: vi.fn(),
}));

import { request } from '@/utils/request';
import { getCustomer, listCustomers } from '@/services/customer';

const mockRequest = vi.mocked(request);

describe('customer service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequest.mockResolvedValue({});
  });

  it('getCustomer calls correct URL', async () => {
    const mockCustomer = {
      id: 'cust-1',
      displayName: 'Alice',
      primaryEmail: 'alice@example.com',
      identifierCount: 2,
      createdAt: '2024-01-01T00:00:00Z',
      identifiers: [],
      attributes: {},
      recentTicketIds: ['ticket-1'],
    };
    mockRequest.mockResolvedValue(mockCustomer);

    const result = await getCustomer('cust-1');

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/customers/cust-1',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(result.id).toBe('cust-1');
    expect(result.displayName).toBe('Alice');
  });

  it('listCustomers calls correct URL with optional query', async () => {
    mockRequest.mockResolvedValue([]);
    await listCustomers('alice');
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/customers',
      expect.objectContaining({
        method: 'GET',
        params: { q: 'alice' },
      }),
    );
  });

  it('listCustomers calls without params when no query given', async () => {
    mockRequest.mockResolvedValue([]);
    await listCustomers();
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/customers',
      expect.objectContaining({
        method: 'GET',
        params: {},
      }),
    );
  });
});
