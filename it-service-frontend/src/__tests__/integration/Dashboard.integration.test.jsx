import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../context/ToastContext';

// Dashboard, leaderboard satırlarından navigasyon için useNavigate kullanır → Router gerekir.
// Hata mesajları artık global toast'tan aktığı için ToastProvider da sarmalanır.
const renderDashboard = () =>
  render(
    <ToastProvider>
      <MemoryRouter><Dashboard /></MemoryRouter>
    </ToastProvider>
  );

// Mock metricService before importing Dashboard
vi.mock('../../services/metricService', () => ({
  default: {
    getDashboardSummary: vi.fn(),
    getStatusDistribution: vi.fn(),
    getAgentPerformance: vi.fn(),
    getTicketTimeline: vi.fn(),
    getPrioritySLAMetrics: vi.fn(),
    getProductMetrics: vi.fn(),
    getCSATMetrics: vi.fn(),
    getAlertsAndBacklog: vi.fn(),
    getWorklogCompletion: vi.fn(),
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
  csatMetrics: {
    totalResponses: 0,
    averageRating: 0,
    ratingDistribution: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 },
    trend: { thisMonth: 0, lastMonth: 0, trend: 'STABLE' },
    byPriority: {},
    topComments: [],
  },
  alerts: {
    breachedSLA: [],
    upcomingBreach: [],
    waitingTooLong: [],
    backlogMetrics: { unassignedCount: 0, newTicketsWaiting: 0, avgWaitingHours: 0 },
  },
  worklog: {
    periodDays: 30,
    agentWorklogs: [],
    completionRates: { totalResolved: 0, totalClosed: 0, totalCreated: 0, resolvedInPeriod: 0, completionRate: 0, avgResolutionHours: 0, slaComplianceRate: 100 },
  },
};

function setupHappyPath() {
  metricService.getDashboardSummary.mockResolvedValue(EMPTY_RESPONSES.summary);
  metricService.getStatusDistribution.mockResolvedValue(EMPTY_RESPONSES.statusDist);
  metricService.getAgentPerformance.mockResolvedValue(EMPTY_RESPONSES.agentPerf);
  metricService.getTicketTimeline.mockResolvedValue(EMPTY_RESPONSES.timeline);
  metricService.getPrioritySLAMetrics.mockResolvedValue(EMPTY_RESPONSES.prioritySla);
  metricService.getProductMetrics.mockResolvedValue(EMPTY_RESPONSES.products);
  metricService.getCSATMetrics.mockResolvedValue(EMPTY_RESPONSES.csatMetrics);
  metricService.getAlertsAndBacklog.mockResolvedValue(EMPTY_RESPONSES.alerts);
  metricService.getWorklogCompletion.mockResolvedValue(EMPTY_RESPONSES.worklog);
}

describe('Dashboard — Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page header', async () => {
    setupHappyPath();
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('Manager Dashboard')).toBeInTheDocument();
    });
  });

  it('shows skeleton loaders during initial load', () => {
    setupHappyPath();
    const { container } = renderDashboard();
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders all four KPI card titles after load', async () => {
    setupHappyPath();
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('Open Tickets')).toBeInTheDocument();
      expect(screen.getByText('SLA Breach')).toBeInTheDocument();
      expect(screen.getByText('Avg. Resolution Time')).toBeInTheDocument();
      // 'CSAT' may appear in multiple places (KPI card + AgentPerformanceTable header)
      expect(screen.getAllByText('CSAT').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('calls all nine metric service methods on mount', async () => {
    setupHappyPath();
    renderDashboard();
    await waitFor(() => {
      expect(metricService.getDashboardSummary).toHaveBeenCalledTimes(1);
      expect(metricService.getStatusDistribution).toHaveBeenCalledTimes(1);
      expect(metricService.getAgentPerformance).toHaveBeenCalledTimes(1);
      expect(metricService.getTicketTimeline).toHaveBeenCalledWith(30);
      expect(metricService.getPrioritySLAMetrics).toHaveBeenCalledTimes(1);
      expect(metricService.getProductMetrics).toHaveBeenCalledTimes(1);
      expect(metricService.getCSATMetrics).toHaveBeenCalledWith(3);
      expect(metricService.getWorklogCompletion).toHaveBeenCalledWith(30);
      expect(metricService.getAlertsAndBacklog).toHaveBeenCalledTimes(1);
    });
  });

  it('shows error toast when API call fails', async () => {
    metricService.getDashboardSummary.mockRejectedValue(new Error('Network error'));
    metricService.getStatusDistribution.mockRejectedValue(new Error('Network error'));
    metricService.getAgentPerformance.mockRejectedValue(new Error('Network error'));
    metricService.getTicketTimeline.mockRejectedValue(new Error('Network error'));
    metricService.getPrioritySLAMetrics.mockRejectedValue(new Error('Network error'));
    metricService.getProductMetrics.mockRejectedValue(new Error('Network error'));
    metricService.getCSATMetrics.mockRejectedValue(new Error('Network error'));
    metricService.getAlertsAndBacklog.mockRejectedValue(new Error('Network error'));
    metricService.getWorklogCompletion.mockRejectedValue(new Error('Network error'));

    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText(/could not be loaded/i)).toBeInTheDocument();
    });
  });

  it('refresh button triggers data reload', async () => {
    setupHappyPath();
    const user = userEvent.setup();
    renderDashboard();

    await waitFor(() => screen.getByText('Refresh data'));
    await user.click(screen.getByText('Refresh data'));

    await waitFor(() => {
      expect(metricService.getDashboardSummary).toHaveBeenCalledTimes(2);
    });
  });
});
