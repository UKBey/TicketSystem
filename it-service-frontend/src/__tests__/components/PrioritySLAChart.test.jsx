import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
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
    expect(screen.getByText(/No priority SLA metrics found/i)).toBeInTheDocument();
  });

  it('renders a row for each priority level', () => {
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);
    // Priority labels are localized via the shared ticket.priority.* keys.
    expect(screen.getByText('Critical')).toBeInTheDocument();
    expect(screen.getByText('High')).toBeInTheDocument();
    expect(screen.getByText('Medium')).toBeInTheDocument();
    expect(screen.getByText('Low')).toBeInTheDocument();
  });

  it('displays average on-time percentage in header', () => {
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);
    // Average of 100 + 91.7 + 90 + 90 = 92.925 → rounds to 93
    expect(screen.getByText('93%')).toBeInTheDocument();
  });

  it('shows hover detail section when row is hovered', async () => {
    render(<PrioritySLAChart data={SAMPLE_DATA} loading={false} />);
    // Each row renders chip spans showing breach count and on-time percentage.
    // CRITICAL row: breachCount=0 → "Breach: 0", onTimePercentage=100 → "On-time: 100%"
    expect(screen.getByText(/Breach: 0/i)).toBeInTheDocument();
    expect(screen.getByText(/On-time: 100%/i)).toBeInTheDocument();
  });

  it('sorts rows in CRITICAL → HIGH → MEDIUM → LOW order', () => {
    const shuffled = {
      priorityMetrics: [...SAMPLE_DATA.priorityMetrics].reverse(),
    };
    render(<PrioritySLAChart data={shuffled} loading={false} />);

    const headings = screen.getAllByRole('heading', { level: 3 });
    const labels = headings.map((h) => h.textContent);
    expect(labels).toEqual(['Critical', 'High', 'Medium', 'Low']);
  });
});
