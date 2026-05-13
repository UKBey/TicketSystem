import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Clock, Plus, Trash2 } from 'lucide-react';
import api from '../../services/api';
import { formatMinutes, formatShortDate } from '../../utils/ticketFormatters';

export default function WorklogCard({ ticketId, ticketStatus, isAgent }) {
  const { t } = useTranslation();

  const [worklogs, setWorklogs]               = useState([]);
  const [formOpen, setFormOpen]               = useState(false);
  const [minutes, setMinutes]                 = useState('');
  const [description, setDescription]         = useState('');
  const [adding, setAdding]                   = useState(false);

  const fetchWorklogs = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${ticketId}/worklogs`);
      setWorklogs(res.data);
    } catch (err) {
      console.debug('Could not load worklogs:', err);
    }
  }, [ticketId]);

  useEffect(() => { fetchWorklogs(); }, [fetchWorklogs]);

  const handleAdd = async () => {
    const mins = parseInt(minutes, 10);
    if (!mins || mins <= 0) return;
    setAdding(true);
    try {
      const res = await api.post(`/tickets/${ticketId}/worklogs`, {
        minutes: mins,
        description: description.trim() || null,
      });
      setWorklogs((prev) => [...prev, res.data]);
      setMinutes('');
      setDescription('');
      setFormOpen(false);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not add worklog.');
    } finally {
      setAdding(false);
    }
  };

  const handleDelete = async (worklogId) => {
    if (!confirm(t('ticketDetail.confirmDeleteWorklog'))) return;
    try {
      await api.delete(`/tickets/${ticketId}/worklogs/${worklogId}`);
      setWorklogs((prev) => prev.filter((w) => w.id !== worklogId));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not delete worklog.');
    }
  };

  if (!isAgent) return null;

  const total = worklogs.reduce((sum, w) => sum + w.minutes, 0);

  return (
    <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="flex items-center justify-between px-5 py-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
        <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
          <Clock className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
          {t('ticketDetail.worklogs', { count: worklogs.length })}
        </span>
        {worklogs.length > 0 && (
          <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
            {t('ticketDetail.worklogTotal', { value: formatMinutes(total) })}
          </span>
        )}
      </div>

      <div className="p-4">
        {worklogs.length === 0 && !formOpen && (
          <div className="text-center py-3 text-xs" style={{ color: 'var(--text-tertiary)' }}>
            {t('ticketDetail.noWorklogs')}
          </div>
        )}

        {worklogs.length > 0 && (
          <div className="space-y-2">
            {worklogs.map((w) => (
              <div
                key={w.id}
                className="rounded-lg border p-3 transition-colors"
                style={{ borderColor: 'var(--border-color-light)' }}
                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-primary-500">{formatMinutes(w.minutes)}</span>
                  <button
                    className="rounded p-1 transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
                    style={{ color: 'var(--text-tertiary)' }}
                    onClick={() => handleDelete(w.id)}
                  >
                    <Trash2 className="h-3 w-3" />
                  </button>
                </div>
                {w.description && (
                  <div className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-primary)' }}>{w.description}</div>
                )}
                <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
                  {w.agentId} · {formatShortDate(w.createdAt)}
                </div>
              </div>
            ))}
          </div>
        )}

        {ticketStatus !== 'CLOSED' && (
          !formOpen ? (
            <button
              className={`w-full rounded-lg border px-3 py-2 text-xs font-medium transition-colors cursor-pointer flex items-center justify-center gap-1.5 ${worklogs.length > 0 ? 'mt-3' : ''}`}
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              onClick={() => setFormOpen(true)}
            >
              <Plus className="h-3 w-3" />
              {t('ticketDetail.addWorklog')}
            </button>
          ) : (
            <div className={`rounded-lg border p-3 space-y-2 ${worklogs.length > 0 ? 'mt-3' : ''}`} style={{ borderColor: 'var(--border-color)', backgroundColor: 'var(--bg-surface-secondary)' }}>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.worklogDuration')}</label>
                <input
                  type="number" min="1" placeholder="e.g. 30"
                  value={minutes} onChange={(e) => setMinutes(e.target.value)}
                  className="w-full rounded-lg border px-3 py-1.5 text-sm outline-none transition-all focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.worklogDescription')}</label>
                <textarea
                  rows="2" placeholder={t('ticketDetail.worklogPlaceholder')}
                  value={description} onChange={(e) => setDescription(e.target.value)}
                  className="w-full rounded-lg border px-3 py-1.5 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[48px]"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
              </div>
              <div className="flex gap-2">
                <button
                  className="flex-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                  onClick={handleAdd}
                  disabled={adding || !minutes || parseInt(minutes, 10) <= 0}
                >
                  {adding ? t('ticketDetail.worklogSaving') : t('ticketDetail.worklogSave')}
                </button>
                <button
                  className="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                  onClick={() => { setFormOpen(false); setMinutes(''); setDescription(''); }}
                >
                  {t('form.cancel')}
                </button>
              </div>
            </div>
          )
        )}
      </div>
    </div>
  );
}
