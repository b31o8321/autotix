// TODO: Smoke test for Desk page.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import DeskPage from '@/pages/Desk';

vi.mock('@/services/ticket', () => ({
  listTickets: vi.fn().mockResolvedValue([]),
}));

describe('DeskPage', () => {
  it('renders Desk title', () => {
    render(<DeskPage />);
    expect(screen.getByText(/Desk/i)).toBeInTheDocument();
  });
});
