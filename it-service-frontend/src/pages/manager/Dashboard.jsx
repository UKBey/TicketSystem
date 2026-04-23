import { LayoutDashboard } from 'lucide-react';

export default function Dashboard() {
  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Dashboard</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Overview of system metrics and performance.</p>
      </div>

      <div className="flex flex-col items-center justify-center rounded-xl border py-24" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <div className="flex h-20 w-20 items-center justify-center rounded-2xl mb-5" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
          <LayoutDashboard className="h-10 w-10 opacity-30" style={{ color: 'var(--text-tertiary)' }} />
        </div>
        <h2 className="text-xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>Coming Soon</h2>
        <p className="text-sm max-w-xs text-center" style={{ color: 'var(--text-secondary)' }}>
          The dashboard with analytics and reporting will be available here soon.
        </p>
      </div>
    </>
  );
}
