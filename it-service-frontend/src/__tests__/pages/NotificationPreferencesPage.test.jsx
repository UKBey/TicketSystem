import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../../services/notificationApi', () => ({
  getPreferences: vi.fn(),
  updatePreferences: vi.fn(),
}));

import NotificationPreferencesPage from '../../pages/NotificationPreferencesPage';
import { getPreferences, updatePreferences } from '../../services/notificationApi';

const SAMPLE_PREFS = {
  emailOnTicketCreated: true,
  emailOnTicketAssigned: false,
  emailOnStatusChanged: true,
  emailOnCommentAdded: true,
  emailOnSlaWarning: true,
  emailOnSlaBreached: true,
  emailOnTicketResolved: true,
  notifyOnTicketCreated: true,
  notifyOnTicketAssigned: true,
  notifyOnStatusChanged: false,
  notifyOnCommentAdded: true,
  notifyOnSlaWarning: true,
  notifyOnSlaBreached: true,
  notifyOnTicketResolved: true,
};

describe('NotificationPreferencesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading spinner on initial render', () => {
    getPreferences.mockReturnValue(new Promise(() => {}));
    const { container } = render(<NotificationPreferencesPage />);
    expect(container.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('renders all 7 event labels after load', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => {
      expect(screen.getByText('Ticket created')).toBeInTheDocument();
      expect(screen.getByText('Ticket assigned to me')).toBeInTheDocument();
      expect(screen.getByText('Ticket status changed')).toBeInTheDocument();
      expect(screen.getByText('Comment added')).toBeInTheDocument();
      expect(screen.getByText('SLA warning')).toBeInTheDocument();
      expect(screen.getByText('SLA breached')).toBeInTheDocument();
      expect(screen.getByText('Ticket resolved')).toBeInTheDocument();
    });
  });

  it('renders 14 toggles — 2 per event', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => {
      expect(screen.getAllByRole('switch')).toHaveLength(14);
    });
  });

  it('shows column headers for Email and In-App', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => {
      expect(screen.getByText('Email')).toBeInTheDocument();
      expect(screen.getByText('In-App')).toBeInTheDocument();
    });
  });

  it('reflects correct initial checked state for email toggle', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Ticket assigned to me'));

    // emailOnTicketAssigned=false → switch at index 2 (row 1, col email)
    const switches = screen.getAllByRole('switch');
    expect(switches[2]).toHaveAttribute('aria-checked', 'false');
  });

  it('reflects correct initial checked state for in-app toggle', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Ticket status changed'));

    // notifyOnStatusChanged=false → switch at index 5 (row 2, col in-app)
    const switches = screen.getAllByRole('switch');
    expect(switches[5]).toHaveAttribute('aria-checked', 'false');
  });

  it('shows error feedback when preferences fail to load', async () => {
    getPreferences.mockRejectedValue(new Error('Network error'));
    render(<NotificationPreferencesPage />);
    await waitFor(() => {
      expect(screen.getByText('Failed to load preferences.')).toBeInTheDocument();
    });
  });

  it('calls updatePreferences with current prefs on save', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    updatePreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Save'));
    await user.click(screen.getByText('Save'));
    await waitFor(() => {
      expect(updatePreferences).toHaveBeenCalledWith(SAMPLE_PREFS);
    });
  });

  it('shows success feedback after successful save', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    updatePreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Save'));
    await user.click(screen.getByText('Save'));
    await waitFor(() => {
      expect(screen.getByText('Preferences saved.')).toBeInTheDocument();
    });
  });

  it('shows error feedback when save fails', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    updatePreferences.mockRejectedValue(new Error('Server error'));
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Save'));
    await user.click(screen.getByText('Save'));
    await waitFor(() => {
      expect(screen.getByText('Failed to save. Please try again.')).toBeInTheDocument();
    });
  });

  it('toggling an email switch updates its aria-checked', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Ticket assigned to me'));

    // emailOnTicketAssigned starts false (index 2)
    const switches = screen.getAllByRole('switch');
    expect(switches[2]).toHaveAttribute('aria-checked', 'false');
    await user.click(switches[2]);
    expect(switches[2]).toHaveAttribute('aria-checked', 'true');
  });

  it('toggling an in-app switch updates its aria-checked', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('Ticket status changed'));

    // notifyOnStatusChanged starts false (index 5)
    const switches = screen.getAllByRole('switch');
    expect(switches[5]).toHaveAttribute('aria-checked', 'false');
    await user.click(switches[5]);
    expect(switches[5]).toHaveAttribute('aria-checked', 'true');
  });
});
