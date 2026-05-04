import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PrioritySLAChart from '../../components/dashboard/PrioritySLAChart';

const SAMPLE_DATA = {
  priorityMetrics: [
    { priority: 'CRITICAL', ticketCount: 3, slaTargetHours: 4, avgResolutionHours: 2.1, breachCount: 0, breachPercentage: 0, onTimePercentage: 100 },
    { priority: 'HIGH',     ticketCount: 12, slaTargetHours: 8, avgResolutionHours: 5.4, breachCount: 1, breachPercentage: 8.3, onTimePercentage: 91.7 },
    { priority: 'MEDIUM',   ticketCount: 30, slaTargetHours: 16, avgResolutionHours: 14.0, breachCount: 3, breachPercentage: 10, onTimePercentage: 90 },
    { priority: 'LOW',      ticketCount: 50, slaTargetHours: 48, avgResolutionHours: 40.0, breachCount: 5, breachPercentage: 10, onTimePercentage: 90 },
  ],
};

describe('PrioritySLAChart', () => {
  it('renders without crashing when loading=true', () => {
    // PrioritySLAChart has no dedicated skeleton; it renders empty rows while loading
    const { container } = render(<PrioritySLAChart data={null} loading={true} />);
    expect(container.firstChild).toBeTruthy();
  });

  it('shows empty state when priorityMetrics is empty', () => {
    render(<PrioritySLAChart data={{ priorityMetrics: [] }} loading={false} />);
    expect(screen.getByText(/Priority-SLA metrikleri bulunamadı/i)).toBeInTheDocument();
  });

  it('renders a row for each priority level', () => {
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
    expect(screen.getByText('LOW')).toBeInTheDocument();
  });

  it('displays average on-time percentage in header', () => {
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);
    // Average of 100 + 91.7 + 90 + 90 = 92.925 → rounds to 93
    expect(screen.getByText('93%')).toBeInTheDocument();
  });

  it('shows hover detail section when row is hovered', async () => {
    const user = userEvent.setup();
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);

    const criticalRow = screen.getByRole('button', { name: /CRITICAL priority SLA metric row/i });
    await user.hover(criticalRow);

    expect(screen.getByText(/Seçili satır/i)).toBeInTheDocument();
  });

  it('sorts rows in CRITICAL → HIGH → MEDIUM → LOW order', () => {
    const shuffled = {
      priorityMetrics: [...SAMPLE_DATA.priorityMetrics].reverse(),
    };
    render(<PrioritySLAChart data={shuffled} loading={false} />);

    const rows = screen.getAllByRole('button');
    const labels = rows.map((r) => r.querySelector('h3')?.textContent);
    expect(labels).toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']);
  });
});
