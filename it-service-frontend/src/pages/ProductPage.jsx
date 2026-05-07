import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import SlaTimerBadge from '../components/SlaTimerBadge';
import { ArrowLeft, Package, AlertTriangle, Ticket, Activity, CheckCircle, Settings } from 'lucide-react';

export default function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { getPrimaryRole } = useAuth();
  const isAdmin = getPrimaryRole() === 'AGENT_ADMIN';

  const [product, setProduct] = useState(null);
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tickSeconds, setTickSeconds] = useState(0);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [productRes, ticketsRes] = await Promise.all([
          api.get(`/products/${id}`),
          api.get(`/tickets/by-product/${id}`),
        ]);
        setProduct(productRes.data);
        setTickets(ticketsRes.data);
      } catch (err) {
        console.error('Could not load product data:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id]);

  useEffect(() => {
    const timer = setInterval(() => setTickSeconds((v) => v + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const activeStatuses = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER'];
  const stats = {
    total: tickets.length,
    active: tickets.filter((t) => activeStatuses.includes(t.status)).length,
    slaBreached: tickets.filter((t) => t.slaBreached).length,
    resolved: tickets.filter((t) => t.status === 'RESOLVED').length,
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-40">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin"
          style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3" style={{ color: 'var(--text-tertiary)' }}>
        <Package className="h-12 w-12 opacity-30" />
        <p className="text-sm">Product not found or access denied.</p>
        <button
          onClick={() => navigate('/products')}
          className="mt-2 text-sm font-medium text-primary-500 hover:underline cursor-pointer"
        >
          Back to Products
        </button>
      </div>
    );
  }

  return (
    <>
      {/* Header */}
      <div className="mb-6">
        <button
          onClick={() => navigate('/products')}
          className="inline-flex items-center gap-1.5 text-sm font-medium mb-4 transition-colors cursor-pointer"
          style={{ color: 'var(--text-secondary)' }}
          onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--text-primary)')}
          onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-secondary)')}
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Products
        </button>

        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div
              className="flex h-11 w-11 items-center justify-center rounded-xl"
              style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
            >
              <Package className="h-6 w-6" style={{ color: 'var(--text-secondary)' }} />
            </div>
            <div>
              <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                {product.name}
              </h1>
              <span
                className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold mt-1 ${
                  product.isActive
                    ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                    : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                }`}
              >
                {product.isActive ? 'Active' : 'Inactive'}
              </span>
            </div>
          </div>

          {isAdmin && (
            <button
              onClick={() => navigate('/products')}
              className="inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition-colors cursor-pointer"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
            >
              <Settings className="h-4 w-4" />
              Manage Products
            </button>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="Total Tickets" value={stats.total} icon={Ticket} color="#3b82f6" />
        <StatCard label="Active" value={stats.active} icon={Activity} color="#f59e0b" />
        <StatCard label="SLA Breached" value={stats.slaBreached} icon={AlertTriangle} color="#ef4444" />
        <StatCard label="Resolved" value={stats.resolved} icon={CheckCircle} color="#10b981" />
      </div>

      {/* Ticket List */}
      <div
        className="rounded-xl border overflow-hidden"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        <div
          className="px-6 py-4 border-b text-sm font-semibold"
          style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
        >
          Tickets ({tickets.length})
        </div>

        {tickets.length === 0 ? (
          <div
            className="flex flex-col items-center justify-center py-16"
            style={{ color: 'var(--text-tertiary)' }}
          >
            <Ticket className="h-10 w-10 mb-3 opacity-25" />
            <p className="text-sm">No tickets for this product yet.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  {['ID', 'Title', 'Status', 'Priority', 'SLA', 'Created'].map((h) => (
                    <th
                      key={h}
                      className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr
                    key={ticket.id}
                    onClick={() => navigate(`/tickets/${ticket.id}`)}
                    className="cursor-pointer transition-colors duration-150"
                    style={{ borderBottom: '1px solid var(--border-color-light)' }}
                    onMouseEnter={(e) =>
                      (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')
                    }
                    onMouseLeave={(e) =>
                      (e.currentTarget.style.backgroundColor = 'transparent')
                    }
                  >
                    <td className="px-4 py-3 text-sm font-semibold text-primary-500">
                      TCK-{String(ticket.id).padStart(3, '0')}
                    </td>
                    <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-primary)' }}>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{ticket.title}</span>
                        {ticket.slaBreached && (
                          <span
                            className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold"
                            style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}
                          >
                            <AlertTriangle className="h-3 w-3" />
                            SLA
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={ticket.status} />
                    </td>
                    <td className="px-4 py-3">
                      <PriorityBadge priority={ticket.priority} />
                    </td>
                    <td className="px-4 py-3">
                      <SlaTimerBadge ticket={ticket} tickSeconds={tickSeconds} />
                    </td>
                    <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                      {formatDate(ticket.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function StatCard({ label, value, icon: Icon, color }) {
  return (
    <div
      className="rounded-xl border p-5"
      style={{
        backgroundColor: 'var(--bg-surface)',
        borderColor: 'var(--border-color)',
        boxShadow: 'var(--shadow-sm)',
      }}
    >
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>
          {label}
        </span>
        <div
          className="flex h-8 w-8 items-center justify-center rounded-lg"
          style={{ backgroundColor: `${color}18` }}
        >
          <Icon className="h-4 w-4" style={{ color }} />
        </div>
      </div>
      <p className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
        {value}
      </p>
    </div>
  );
}
