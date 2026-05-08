import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';
import AgentSelectionModal from '../../components/AgentSelectionModal';
import { useAuth } from '../../context/AuthContext';

export default function Pool() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const isAgentAdmin = hasRole('AGENT_ADMIN');

  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);

  // Assign modal state
  const [assignModal, setAssignModal] = useState({ open: false, ticketId: null, productId: null });

  const fetchPool = async () => {
    try {
      const res = await api.get('/tickets/pool');
      setTickets(res.data);
    } catch (err) {
      console.error('Could not load pool tickets:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPool();
  }, []);

  const handleClaim = async (ticketId) => {
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      setTickets((prev) => prev.filter((t) => t.id !== ticketId));
      navigate(`/tickets/${ticketId}`);
    } catch (err) {
      // Handle ticket limit exceeded error (409 Conflict)
      if (err.response?.status === 409 && err.response?.data?.error === 'TICKET_LIMIT_EXCEEDED') {
        alert(`Limit exceeded: ${err.response.data.message}`);
      } else {
        alert(err.response?.data?.message || 'Could not claim ticket.');
      }
    }
  };

  const handleOpenAssign = (ticket) => {
    setAssignModal({ open: true, ticketId: ticket.id, productId: ticket.productId });
  };

  const handleAssignSuccess = (updatedTicket) => {
    // Başarılı atama sonrası bileti listeden kaldır (artık NEW değil)
    setTickets((prev) => prev.filter((t) => t.id !== updatedTicket.id));
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Pool</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Unassigned tickets waiting to be claimed.</p>
      </div>

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable
            tickets={tickets}
            showClaimButton
            onClaim={handleClaim}
            showSla
            showAssignButton={isAgentAdmin}
            onAssign={handleOpenAssign}
          />
        )}
      </div>

      <AgentSelectionModal
        isOpen={assignModal.open}
        onClose={() => setAssignModal({ open: false, ticketId: null, productId: null })}
        onSuccess={handleAssignSuccess}
        ticketId={assignModal.ticketId}
        productId={assignModal.productId}
      />
    </>
  );
}
