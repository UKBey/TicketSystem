import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Clock, Plus, Trash2, ChevronDown } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { formatMinutes, formatShortDate } from '../../utils/ticketFormatters';
import Button from '../Button';

export default function WorklogCard({ ticketId, ticketStatus, isAgent }) {
  const { t } = useTranslation();
  const toast = useToast();

  const [worklogs, setWorklogs]               = useState([]);
  const [isOpen, setIsOpen]                   = useState(true);
  const [formOpen, setFormOpen]               = useState(false);
  const [minutes, setMinutes]                 = useState('');
  const [description, setDescription]         = useState('');
  const [adding, setAdding]                   = useState(false);

  const fetchWorklogs = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${ticketId}/worklogs`);
      setWorklogs(res.data);
    } catch {
      // Silent: non-agents (and pre-sync states) get an expected 403 here — see note below.
    }
  }, [ticketId]);

  // Worklog'lar yalnizca ajanlara aciktir (backend GET /worklogs -> AGENT/LEAD/MANAGER).
  // Bilesen !isAgent durumunda zaten null render eder; customer'da fetch'i hic atesleme,
  // aksi halde her ticket detayinda 403 (Forbidden) gurultusu olusur.
  useEffect(() => { if (isAgent) fetchWorklogs(); }, [isAgent, fetchWorklogs]);

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
      toast.error(err.response?.data?.message || t('ticketDetail.addWorklogFailed'));
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
      toast.error(err.response?.data?.message || t('ticketDetail.deleteWorklogFailed'));
    }
  };

  if (!isAgent) return null;

  const total = worklogs.reduce((sum, w) => sum + w.minutes, 0);

  return (
    <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between px-5 py-3 border-b hover:opacity-80 transition-opacity cursor-pointer"
        style={{ borderColor: 'var(--border-color)' }}
      >
        <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
          <Clock className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
          {t('ticketDetail.worklogs', { count: worklogs.length })}
        </span>
        <div className="flex items-center gap-2">
          {worklogs.length > 0 && (
            <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
              {t('ticketDetail.worklogTotal', { value: formatMinutes(total) })}
            </span>
          )}
          <ChevronDown
            className="h-4 w-4 transition-transform"
            style={{ color: 'var(--text-tertiary)', transform: isOpen ? 'rotate(0deg)' : 'rotate(-90deg)' }}
          />
        </div>
      </button>

      {isOpen && (
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
                <div className="flex items-center justify-between gap-2 flex-wrap">
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
                  <div className="text-xs mt-1 leading-relaxed break-words" style={{ color: 'var(--text-primary)' }}>{w.description}</div>
                )}
                <div className="text-[11px] mt-1 flex flex-wrap gap-x-1 gap-y-0.5 break-words" style={{ color: 'var(--text-tertiary)' }}>
                  <span className="break-all">{w.agentName || w.agentId}</span>
                  <span>·</span>
                  <span>{formatShortDate(w.createdAt)}</span>
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
                  type="number" min="1" placeholder={t('ticketDetail.worklogDurationPlaceholder')}
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
                <Button
                  size="sm"
                  className="flex-1"
                  onClick={handleAdd}
                  disabled={adding || !minutes || parseInt(minutes, 10) <= 0}
                >
                  {adding ? t('ticketDetail.worklogSaving') : t('ticketDetail.worklogSave')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => { setFormOpen(false); setMinutes(''); setDescription(''); }}
                >
                  {t('form.cancel')}
                </Button>
              </div>
            </div>
          )
        )}
      </div>
      )}
    </div>
  );
}
