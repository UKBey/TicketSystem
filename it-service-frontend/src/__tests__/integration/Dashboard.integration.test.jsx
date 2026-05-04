import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// Mock metricService before importing Dashboard
vi.mock('../../services/metricService', () => ({
  default: {
    getDashboardSummary: vi.fn(),
    getStatusDistribution: vi.fn(),
    getAgentPerformance: vi.fn(),
    getTicketTimeline: vi.fn(),
    getPrioritySLAMetrics: vi.fn(),
    getProductMetrics: vi.fn(),
  },
}));

import Dashboard from '../../pages/manager/Dashboard';
import metricService from '../../services/metricService';

const EMPTY_RESPONSES = {
  summary: {
    totalOpenTickets: 0, newTicketsLast24Hours: 0, slaBreachedCount: 0,
    slaBreachedPercentage: 0, avgResponseTimeHours: 0, csatAverage: 0,
    csatTotalResponses: 0, priorityDistribution: { critical: 0, high: 0, medium: 0, low: 0 },
  },
  statusDist:  { newCount: 0, inProgressCount: 0, resolvedCount: 0, closedCount: 0, totalCount: 0 },
  agentPerf:   { agents: [], totalAgents: 0, totalActiveTickets: 0, averageCsat: 0 },
  timeline:    { timeline: [] },
  prioritySla: { priorityMetrics: [] },
  products:    { productMetrics: [] },
};

function setupHappyPath() {
  metricService.getDashboardSummary.mockResolvedValue(EMPTY_RESPONSES.summary);
  metricService.getStatusDistribution.mockResolvedValue(EMPTY_RESPONSES.statusDist);
  metricService.getAgentPerformance.mockResolvedValue(EMPTY_RESPONSES.agentPerf);
  metricService.getTicketTimeline.mockResolvedValue(EMPTY_RESPONSES.timeline);
  metricService.getPrioritySLAMetrics.mockResolvedValue(EMPTY_RESPONSES.prioritySla);
  metricService.getProductMetrics.mockResolvedValue(EMPTY_RESPONSES.products);
}

describe('Dashboard — Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page header', async () => {
    setupHappyPath();
    render(<Dashboard />);
    await waitFor(() => {
      expect(screen.getByText('Manager Dashboard')).toBeInTheDocument();
    });
  });

  it('shows skeleton loaders during initial load', () => {
    setupHappyPath();
    const { container } = render(<Dashboard />);
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders all four KPI card titles after load', async () => {
    setupHappyPath();
    render(<Dashboard />);
    await waitFor(() => {
      expect(screen.getByText('Açık Bilet')).toBeInTheDocument();
      expect(screen.getByText('SLA Breach')).toBeInTheDocument();
      expect(screen.getByText('Ort. Çözüm Süresi')).toBeInTheDocument();
      // 'CSAT' may appear in multiple places (KPI card + AgentPerformanceTable header)
      expect(screen.getAllByText('CSAT').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('calls all six metric service methods on mount', async () => {
    setupHappyPath();
    render(<Dashboard />);
    await waitFor(() => {
      expect(metricService.getDashboardSummary).toHaveBeenCalledTimes(1);
      expect(metricService.getStatusDistribution).toHaveBeenCalledTimes(1);
      expect(metricService.getAgentPerformance).toHaveBeenCalledTimes(1);
      expect(metricService.getTicketTimeline).toHaveBeenCalledWith(30);
      expect(metricService.getPrioritySLAMetrics).toHaveBeenCalledTimes(1);
      expect(metricService.getProductMetrics).toHaveBeenCalledTimes(1);
    });
  });

  it('shows error banner when API call fails', async () => {
    metricService.getDashboardSummary.mockRejectedValue(new Error('Network error'));
    metricService.getStatusDistribution.mockRejectedValue(new Error('Network error'));
    metricService.getAgentPerformance.mockRejectedValue(new Error('Network error'));
    metricService.getTicketTimeline.mockRejectedValue(new Error('Network error'));
    metricService.getPrioritySLAMetrics.mockRejectedValue(new Error('Network error'));
    metricService.getProductMetrics.mockRejectedValue(new Error('Network error'));

    render(<Dashboard />);
    await waitFor(() => {
      expect(screen.getByText(/yüklenemedi/i)).toBeInTheDocument();
    });
  });

  it('refresh button triggers data reload', async () => {
    setupHappyPath();
    const user = userEvent.setup();
    render(<Dashboard />);

    await waitFor(() => screen.getByText('Veriyi yenile'));
    await user.click(screen.getByText('Veriyi yenile'));

    await waitFor(() => {
      expect(metricService.getDashboardSummary).toHaveBeenCalledTimes(2);
    });
  });
});
