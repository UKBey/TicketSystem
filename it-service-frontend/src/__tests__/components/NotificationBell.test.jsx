import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../../hooks/useNotifications', () => ({
  useNotifications: vi.fn(),
}));

vi.mock('../../components/notifications/NotificationList', () => ({
  default: ({ onMarkAllRead }) => (
    <div data-testid="notification-list">
      <button onClick={onMarkAllRead}>Mark all read</button>
    </div>
  ),
}));

import NotificationBell from '../../components/notifications/NotificationBell';
import { useNotifications } from '../../hooks/useNotifications';

describe('NotificationBell', () => {
  const mockMarkAllAsRead = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    useNotifications.mockReturnValue({ unreadCount: 0, markAllAsRead: mockMarkAllAsRead });
  });

  it('renders the bell button', () => {
    render(<NotificationBell />);
    expect(screen.getByRole('button', { name: /notifications/i })).toBeInTheDocument();
  });

  it('does not show badge when unreadCount is 0', () => {
    render(<NotificationBell />);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('shows badge with count when unreadCount > 0', () => {
    useNotifications.mockReturnValue({ unreadCount: 5, markAllAsRead: mockMarkAllAsRead });
    render(<NotificationBell />);
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('shows "9+" when unreadCount exceeds 9', () => {
    useNotifications.mockReturnValue({ unreadCount: 15, markAllAsRead: mockMarkAllAsRead });
    render(<NotificationBell />);
    expect(screen.getByText('9+')).toBeInTheDocument();
  });

  it('opens notification list on bell click', async () => {
    const user = userEvent.setup();
    render(<NotificationBell />);
    expect(screen.queryByTestId('notification-list')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /notifications/i }));
    expect(screen.getByTestId('notification-list')).toBeInTheDocument();
  });

  it('closes notification list on second bell click', async () => {
    const user = userEvent.setup();
    render(<NotificationBell />);
    const bell = screen.getByRole('button', { name: /notifications/i });
    await user.click(bell);
    await user.click(bell);
    expect(screen.queryByTestId('notification-list')).not.toBeInTheDocument();
  });

  it('calls markAllAsRead and closes list when mark-all-read is triggered', async () => {
    const user = userEvent.setup();
    render(<NotificationBell />);
    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(screen.getByText('Mark all read'));
    await waitFor(() => {
      expect(mockMarkAllAsRead).toHaveBeenCalledTimes(1);
      expect(screen.queryByTestId('notification-list')).not.toBeInTheDocument();
    });
  });
});
