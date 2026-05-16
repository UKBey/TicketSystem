import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../context/ThemeContext';
import { Sparkles, ChevronDown, ChevronUp } from 'lucide-react';
import { generateAiSummary, getLatestAiSummary } from '../../services/api';
import { formatShortDate } from '../../utils/ticketFormatters';
import i18n from '../../i18n';

export default function AiSummaryCard({ ticketId, hasRole }) {
  const { t } = useTranslation();
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const [summary, setSummary]     = useState(null);
  const [loading, setLoading]     = useState(false);
  const [expanded, setExpanded]   = useState(true);
  const [error, setError]         = useState(null);

  const fetchLatest = useCallback(async () => {
    try {
      const res = await getLatestAiSummary(ticketId);
      setSummary(res.data);
    } catch {
      setSummary(null);
    }
  }, [ticketId]);

  useEffect(() => {
    if (hasRole('AGENT') || hasRole('AGENT_ADMIN')) {
      fetchLatest();
    }
  }, [fetchLatest, hasRole]);

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);
    try {
      const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
      const res = await generateAiSummary(ticketId, lang);
      setSummary(res.data);
      setExpanded(true);
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
        setError(t(key, { seconds }));
      } else {
        setError(data?.detail || t('ticketDetail.aiSummaryError'));
      }
    } finally {
      setLoading(false);
    }
  };

  if (!hasRole('AGENT') && !hasRole('AGENT_ADMIN')) return null;

  return (
    <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="flex items-center justify-between px-5 py-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
        <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
          <Sparkles className="h-4 w-4 text-violet-500" />
          {t('ticketDetail.aiSummaryTitle')}
        </span>
        {summary && (
          <button
            className="flex h-6 w-6 items-center justify-center rounded transition-colors cursor-pointer"
            style={{ color: 'var(--text-tertiary)' }}
            onClick={() => setExpanded((v) => !v)}
            title={expanded ? t('ticketDetail.aiSummaryCollapse') : t('ticketDetail.aiSummaryExpand')}
          >
            {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </button>
        )}
      </div>

      <div className="p-4 space-y-3">
        {summary && expanded && (
          <div
            className="rounded-lg p-3 text-xs leading-relaxed whitespace-pre-wrap"
            style={{ backgroundColor: isDark ? 'rgba(139,92,246,0.08)' : '#f5f3ff', color: 'var(--text-primary)', borderLeft: '3px solid #8b5cf6' }}
          >
            {summary.summary}
          </div>
        )}
        {summary && (
          <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
            <span className="break-all">{summary.model}</span>
            <span>{formatShortDate(summary.createdAt)}</span>
          </div>
        )}
        {error && (
          <div className="rounded-lg px-3 py-2 text-xs" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.1)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}>
            {error}
          </div>
        )}
        {!summary && !loading && !error && (
          <div className="text-center py-2 text-xs" style={{ color: 'var(--text-tertiary)' }}>
            {t('ticketDetail.aiSummaryEmpty')}
          </div>
        )}
        <button
          className="w-full rounded-lg px-3 py-2 text-xs font-semibold transition-colors cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
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
              <div className="h-3 w-3 rounded-full border-2 animate-spin" style={{ borderColor: 'rgba(255,255,255,0.3)', borderTopColor: '#fff' }} />
              {t('ticketDetail.aiSummaryGenerating')}
            </>
          ) : (
            <>
              <Sparkles className="h-3 w-3" />
              {summary ? t('ticketDetail.aiSummaryRegenerate') : t('ticketDetail.aiSummaryGenerate')}
            </>
          )}
        </button>
      </div>
    </div>
  );
}
