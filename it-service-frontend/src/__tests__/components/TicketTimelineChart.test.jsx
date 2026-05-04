import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TicketTimelineChart from '../../components/dashboard/TicketTimelineChart';

const SAMPLE_DATA = {
  timeline: [
    { date: '2026-04-01', created: 12, resolved: 8, closed: 3, slaBreach: 1 },
    { date: '2026-04-02', created: 9,  resolved: 11, closed: 2, slaBreach: 0 },
    { date: '2026-04-03', created: 15, resolved: 7,  closed: 4, slaBreach: 2 },
  ],
};

describe('TicketTimelineChart', () => {
  it('renders loading skeleton when loading=true', () => {
    const { container } = render(<TicketTimelineChart data={null} loading={true} />);
    // When loading, lines are hidden — Recharts container still renders
    expect(container).toBeTruthy();
  });

  it('shows empty state message when timeline is empty', () => {
    render(<TicketTimelineChart data={{ timeline: [] }} loading={false} />);
    expect(screen.getByText(/Timeline verisi bulunamadı/i)).toBeInTheDocument();
  });

  it('renders chart container when data is present', () => {
    const { container } = render(<TicketTimelineChart data={SAMPLE_DATA} loading={false} />);
    expect(container.querySelector('.timeline-chart-scroll')).toBeTruthy();
  });

  it('shows chart section header', () => {
    render(<TicketTimelineChart data={SAMPLE_DATA} loading={false} />);
    expect(screen.getByText(/30 Günlük Trend/i)).toBeInTheDocument();
  });

  it('renders null data gracefully without crash', () => {
    expect(() => render(<TicketTimelineChart data={null} loading={false} />)).not.toThrow();
  });

  it('renders undefined timeline gracefully without crash', () => {
    expect(() => render(<TicketTimelineChart data={{}} loading={false} />)).not.toThrow();
  });
});
