import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProductMetricsChart from '../../components/dashboard/ProductMetricsChart';

const SAMPLE_DATA = {
  productMetrics: [
    { productName: 'Windows Server', totalTickets: 54, openTickets: 48, avgResolutionHours: 12.5, csatAverage: 4.7, slaBreachPercentage: 3.7 },
    { productName: 'SQL Database',   totalTickets: 38, openTickets: 20, avgResolutionHours: 8.0,  csatAverage: 4.5, slaBreachPercentage: 5.2 },
    { productName: 'Exchange',        totalTickets: 28, openTickets: 15, avgResolutionHours: 6.0,  csatAverage: 4.3, slaBreachPercentage: 11.0 },
  ],
};

describe('ProductMetricsChart', () => {
  it('renders loading skeleton when loading=true', () => {
    const { container } = render(<ProductMetricsChart data={null} loading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeTruthy();
  });

  it('shows empty state when productMetrics is empty', () => {
    render(<ProductMetricsChart data={{ productMetrics: [] }} loading={false} />);
    expect(screen.getByText(/Ürün verisi bulunamadı/i)).toBeInTheDocument();
  });

  it('renders a bar row for each product', () => {
    render(<ProductMetricsChart data={SAMPLE_DATA} loading={false} />);
    // product name appears in both bar row and legend
    expect(screen.getAllByText('Windows Server').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('SQL Database').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Exchange').length).toBeGreaterThanOrEqual(1);
  });

  it('shows total ticket count in header', () => {
    render(<ProductMetricsChart data={SAMPLE_DATA} loading={false} />);
    // Total: 54 + 38 + 28 = 120
    expect(screen.getByText(/120/)).toBeInTheDocument();
  });

  it('shows correct product count badge', () => {
    render(<ProductMetricsChart data={SAMPLE_DATA} loading={false} />);
    expect(screen.getByText('3 ürün')).toBeInTheDocument();
  });

  it('groups products beyond TOP_N into "Diğer" row', () => {
    const manyProducts = {
      productMetrics: Array.from({ length: 8 }, (_, i) => ({
        productName: `Product ${i + 1}`,
        totalTickets: 10 - i,
        openTickets: 5,
        avgResolutionHours: 4.0,
        csatAverage: 4.0,
        slaBreachPercentage: 2.0,
      })),
    };
    render(<ProductMetricsChart data={manyProducts} loading={false} />);
    // "Diğer" appears in both the bar row and the legend
    expect(screen.getAllByText(/Diğer/i).length).toBeGreaterThanOrEqual(1);
  });
});
