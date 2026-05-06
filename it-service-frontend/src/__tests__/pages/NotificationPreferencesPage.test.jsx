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
  emailOnSlaWarning: false,
  emailOnSlaBreached: true,
  emailOnTicketResolved: true,
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

  it('renders all 7 preference toggles after load', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    render(<NotificationPreferencesPage />);
    await waitFor(() => {
      expect(screen.getByText('When a ticket is created')).toBeInTheDocument();
      expect(screen.getByText('When a ticket is assigned to me')).toBeInTheDocument();
      expect(screen.getByText('When ticket status changes')).toBeInTheDocument();
      expect(screen.getByText('When a comment is added')).toBeInTheDocument();
      expect(screen.getByText('On SLA warning')).toBeInTheDocument();
      expect(screen.getByText('On SLA breach')).toBeInTheDocument();
      expect(screen.getByText('When a ticket is resolved')).toBeInTheDocument();
    });
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

  it('toggles a preference when its switch is clicked', async () => {
    getPreferences.mockResolvedValue({ data: SAMPLE_PREFS });
    const user = userEvent.setup();
    render(<NotificationPreferencesPage />);
    await waitFor(() => screen.getByText('When a ticket is assigned to me'));

    // emailOnTicketAssigned is false in SAMPLE_PREFS — it is the second toggle (index 1)
    const switches = screen.getAllByRole('switch');
    const assignedToggle = switches[1];
    expect(assignedToggle).toHaveAttribute('aria-checked', 'false');

    await user.click(assignedToggle);
    expect(assignedToggle).toHaveAttribute('aria-checked', 'true');
  });
});
