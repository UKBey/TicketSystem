import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../context/ThemeContext';
import { useToast } from '../../context/ToastContext';
import { Sparkles, X } from 'lucide-react';
import { generateAiSummary, getLatestAiSummary } from '../../services/api';
import { formatShortDate } from '../../utils/ticketFormatters';
import i18n from '../../i18n';

const modalStyles = `
  @keyframes modalFadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }

  @keyframes modalSlideUp {
    from {
      transform: translateY(20px);
      opacity: 0;
    }
    to {
      transform: translateY(0);
      opacity: 1;
    }
  }

  .ai-modal-overlay {
    animation: modalFadeIn 0.3s ease-out;
  }

  .ai-modal-content {
    animation: modalSlideUp 0.4s ease-out;
  }
`;

export default function AiSummaryModal({ isOpen, onClose, ticketId, isAgent }) {
  const { t } = useTranslation();
  const { theme } = useTheme();
  const toast = useToast();
  const isDark = theme === 'dark';

  const [summary, setSummary]     = useState(null);
  const [loading, setLoading]     = useState(false);

  const fetchLatest = useCallback(async () => {
    try {
      const res = await getLatestAiSummary(ticketId);
      setSummary(res.data);
    } catch {
      setSummary(null);
    }
  }, [ticketId]);

  useEffect(() => {
    if (isOpen && isAgent) {
      fetchLatest();
    }
  }, [isOpen, fetchLatest, isAgent]);

  const handleGenerate = async () => {
    setLoading(true);
    try {
      const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
      const res = await generateAiSummary(ticketId, lang);
      setSummary(res.data);
    } catch (err) {
      const status = err.response?.status;
      const data   = err.response?.data;
      if (status === 429) {
        const seconds = Math.ceil(data?.retryAfterSeconds ?? 10);
        // Bizim per-IP rate limit interceptor'umuz body'de error: "RATE_LIMIT_EXCEEDED" doner.
        // Groq token kotasi ise ProblemDetail (detail) gonderir — ayri mesaj.
        const key = data?.error === 'RATE_LIMIT_EXCEEDED'
          ? 'ticketDetail.aiSummaryThrottle'
          : 'ticketDetail.aiSummaryRateLimit';
        toast.error(t(key, { seconds }));
      } else {
        toast.error(data?.detail || t('ticketDetail.aiSummaryError'));
      }
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <style>{modalStyles}</style>
      <div className="ai-modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div
          className="ai-modal-content w-full max-w-md sm:max-w-lg lg:max-w-2xl rounded-xl border max-h-[90vh] flex flex-col overflow-hidden"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-lg)' }}
        >
          <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <span className="text-lg font-semibold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Sparkles className="h-5 w-5 text-violet-500" />
              {t('ticketDetail.aiSummaryTitle')}
            </span>
            <button
              className="flex h-8 w-8 items-center justify-center rounded transition-colors cursor-pointer hover:opacity-70"
              style={{ color: 'var(--text-tertiary)' }}
              onClick={onClose}
              title={t('form.close')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="overflow-y-auto flex-1 p-4 sm:p-6 space-y-4">
            {summary && (
              <div
                className="rounded-lg p-4 text-sm leading-relaxed whitespace-pre-wrap break-words"
                style={{ backgroundColor: isDark ? 'rgba(139,92,246,0.08)' : '#f5f3ff', color: 'var(--text-primary)', borderLeft: '4px solid #8b5cf6' }}
              >
                {summary.summary}
              </div>
            )}
            {summary && (
              <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                <span className="break-all">{summary.model}</span>
                <span>{formatShortDate(summary.createdAt)}</span>
              </div>
            )}
            {!summary && !loading && (
              <div className="text-center py-8 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                {t('ticketDetail.aiSummaryEmpty')}
              </div>
            )}
          </div>

          <div className="border-t px-4 sm:px-6 py-4 flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <button
              className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              style={
                loading
                  ? { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)', border: '1px solid var(--border-color)' }
                  : { background: 'linear-gradient(135deg, #7c3aed, #6d28d9)', color: '#fff' }
              }
              onClick={handleGenerate}
              disabled={loading}
            >
              {loading ? (
                <>
                  <div className="h-4 w-4 rounded-full border-2 animate-spin" style={{ borderColor: 'rgba(255,255,255,0.3)', borderTopColor: '#fff' }} />
                  {t('ticketDetail.aiSummaryGenerating')}
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4" />
                  {summary ? t('ticketDetail.aiSummaryRegenerate') : t('ticketDetail.aiSummaryGenerate')}
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
