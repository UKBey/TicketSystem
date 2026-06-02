import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Send, Paperclip, File, FileText, FileArchive, Image, Download, Zap, Star, AlertTriangle,
} from 'lucide-react';
import { formatShortDate } from '../../utils/ticketFormatters';
import { useTicketDetailContext } from './TicketDetailContext';
import { useCannedResponses } from '../../hooks/useCannedResponses';
import { useAnchoredPosition } from '../../hooks/useAnchoredPosition';
import {
  buildPlaceholderContext, fillPlaceholders, pickContent, findUnfilledPlaceholders,
} from '../../utils/cannedResponses';
import CannedResponsePicker from './CannedResponsePicker';
import SlashAutocomplete from './SlashAutocomplete';

function getFileIcon(fileType) {
  if (!fileType) return <File className="h-5 w-5" />;
  if (fileType.startsWith('image/')) return <Image className="h-5 w-5" />;
  if (fileType.includes('pdf')) return <FileText className="h-5 w-5 text-danger-500" />;
  if (fileType.includes('zip') || fileType.includes('rar') || fileType.includes('tar')) return <FileArchive className="h-5 w-5" />;
  if (fileType.includes('word') || fileType.includes('document')) return <FileText className="h-5 w-5 text-primary-500" />;
  if (fileType.includes('sheet') || fileType.includes('excel')) return <FileText className="h-5 w-5 text-accent-500" />;
  return <File className="h-5 w-5" />;
}

// Finds a clean `/token` immediately before the caret (at line start or after whitespace).
function getSlashContext(value, caret) {
  if (caret == null) return null;
  const upto = value.slice(0, caret);
  const m = upto.match(/(?:^|\s)\/([^\s/]*)$/);
  if (!m) return null;
  const query = m[1];
  return { query, start: caret - (query.length + 1), end: caret };
}

export default function TicketTimeline() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const {
    timeline, ticket, user,
    isAgent, isCustomer, isDark,
    chatEndRef,
    message, setMessage,
    commentType, setCommentType,
    sending, cooldown,
    uploading, fileInputRef,
    handleSendComment, handleFileUpload, handleDownloadAttachment,
  } = useTicketDetailContext();
  const COMMENT_MESSAGE_MAX_LENGTH = 500;

  // ---- Canned responses (Hazır Yanıtlar) — agents only ----------------------
  const { templates, loading: cannedLoading, error: cannedError, recentIds, toggleFavorite, markUsed } =
    useCannedResponses({ productId: ticket?.productId, userId: user?.id, enabled: isAgent });

  const [previewLang, setPreviewLang] = useState((i18n.language || 'en').startsWith('tr') ? 'tr' : 'en');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [caret, setCaret] = useState(0);
  const [slashDismissed, setSlashDismissed] = useState(false);
  const [slashIndex, setSlashIndex] = useState(0);

  const textareaRef = useRef(null);
  const zapBtnRef = useRef(null);

  const placeholderCtx = useMemo(
    () => buildPlaceholderContext({ ticket, user, language: previewLang }),
    [ticket, user, previewLang],
  );

  const slashCtx = useMemo(() => getSlashContext(message, caret), [message, caret]);
  const slashMatches = useMemo(() => {
    if (!slashCtx) return [];
    const q = slashCtx.query.toLowerCase();
    const score = (tpl) => {
      const sc = (tpl.shortcut || '').toLowerCase();
      const ti = (tpl.title || '').toLowerCase();
      if (!q) return 0;
      if (sc === q) return 0;
      if (sc.startsWith(q)) return 1;
      if (ti.startsWith(q)) return 2;
      if (sc.includes(q) || ti.includes(q)
        || (tpl.contentTr || '').toLowerCase().includes(q)
        || (tpl.contentEn || '').toLowerCase().includes(q)) return 3;
      return 99;
    };
    return templates
      .map((tpl) => ({ tpl, s: score(tpl) }))
      .filter((x) => x.s < 99)
      .sort((a, b) => a.s - b.s)
      .slice(0, 8)
      .map((x) => x.tpl);
  }, [slashCtx, templates]);

  const slashOpen = isAgent && !pickerOpen && !slashDismissed && !!slashCtx && slashMatches.length > 0;

  // Quick chips: favorites + recently-used, deduped, capped.
  const chipTemplates = useMemo(() => {
    const byId = new Map(templates.map((tpl) => [tpl.id, tpl]));
    const favs = templates.filter((tpl) => tpl.favorite);
    const recents = recentIds.map((id) => byId.get(id)).filter(Boolean);
    const seen = new Set();
    const out = [];
    for (const tpl of [...favs, ...recents]) {
      if (!seen.has(tpl.id)) { seen.add(tpl.id); out.push(tpl); }
    }
    return out.slice(0, 4);
  }, [templates, recentIds]);

  const unfilled = useMemo(() => findUnfilledPlaceholders(message), [message]);

  const pickerAnchorRef = useAnchoredPosition(zapBtnRef, pickerOpen, { placement: 'top', align: 'right' });
  const slashAnchorRef = useAnchoredPosition(textareaRef, slashOpen, { placement: 'top', align: 'left' });

  // ---- insertion ------------------------------------------------------------
  // One-time: if a template suits only one side, flip the composer to match. The agent
  // can still toggle afterward. BOTH-visibility templates leave the current choice alone.
  const matchCommentType = (tpl) => {
    if (tpl.visibility === 'EXTERNAL') setCommentType('EXTERNAL');
    else if (tpl.visibility === 'INTERNAL') setCommentType('INTERNAL');
  };

  const insertTemplateAtCaret = (tpl) => {
    const { content } = pickContent(tpl, previewLang);
    const filled = fillPlaceholders(content, placeholderCtx);
    const el = textareaRef.current;
    const start = el ? el.selectionStart : message.length;
    const end = el ? el.selectionEnd : message.length;
    const next = message.slice(0, start) + filled + message.slice(end);
    setMessage(next);
    markUsed(tpl.id);
    matchCommentType(tpl);
    const pos = start + filled.length;
    requestAnimationFrame(() => {
      if (el) { el.focus(); el.setSelectionRange(pos, pos); setCaret(pos); }
    });
  };

  const insertFromPicker = (tpl) => {
    insertTemplateAtCaret(tpl);
    setPickerOpen(false);
  };

  const selectSlash = (tpl) => {
    if (!slashCtx) return;
    const { content } = pickContent(tpl, previewLang);
    const filled = fillPlaceholders(content, placeholderCtx);
    const next = message.slice(0, slashCtx.start) + filled + message.slice(slashCtx.end);
    setMessage(next);
    markUsed(tpl.id);
    matchCommentType(tpl);
    setSlashDismissed(true);
    const pos = slashCtx.start + filled.length;
    requestAnimationFrame(() => {
      const el = textareaRef.current;
      if (el) { el.focus(); el.setSelectionRange(pos, pos); setCaret(pos); }
    });
  };

  // ---- composer events ------------------------------------------------------
  const handleChange = (e) => {
    setMessage(e.target.value);
    setCaret(e.target.selectionStart);
    setSlashDismissed(false);
  };

  const syncCaret = (e) => setCaret(e.target.selectionStart);

  const handleKeyDown = (e) => {
    if (slashOpen) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSlashIndex((i) => (slashMatches.length ? (i + 1) % slashMatches.length : 0));
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSlashIndex((i) => (slashMatches.length ? (i - 1 + slashMatches.length) % slashMatches.length : 0));
        return;
      }
      if ((e.key === 'Enter' || e.key === 'Tab') && slashMatches.length) {
        e.preventDefault();
        selectSlash(slashMatches[Math.min(slashIndex, slashMatches.length - 1)]);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setSlashDismissed(true);
        return;
      }
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendComment();
    }
  };

  const openManage = () => {
    setPickerOpen(false);
    navigate('/canned-responses');
  };

  return (
    <div
      className="rounded-xl border overflow-hidden"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
    >
      <div className="flex flex-col gap-4 p-4 sm:p-5 min-h-[300px] max-h-[500px] overflow-y-auto">
        {timeline.length === 0 && (
          <div className="flex items-center justify-center py-8" style={{ color: 'var(--text-tertiary)' }}>
            <p className="text-sm">{t('ticketDetail.noComments')}</p>
          </div>
        )}
        {timeline.map((item) => {
          const itemAuthorId   = item._type === 'attachment' ? item.uploaderId : item.authorId;
          const itemAuthorName = item._type === 'attachment' ? null : item.authorName;
          const itemAuthorRole = item._type === 'attachment' ? null : item.authorRole;
          const isInternal = item.type === 'INTERNAL';
          const isCustomerAuthor = itemAuthorRole
            ? itemAuthorRole === 'CUSTOMER'
            : itemAuthorId === ticket.customerId;
          // Hizalama yazarın kimliğine göre değil, bakan kullanıcının tarafına göre yapılır:
          // müşteri bakıyorsa kendi mesajları sağda, ajan bakıyorsa tüm ajan mesajları sağda.
          // Dahili notları yalnızca ajanlar görür; her zaman ajan tarafında (sağda) durur.
          const isRight = isInternal
            ? true
            : isCustomer ? isCustomerAuthor : !isCustomerAuthor;
          const displayName = itemAuthorName || (isCustomerAuthor ? ticket.customerName : 'Agent');
          // Rol rozeti yalnızca karşı taraftaki (soldaki) mesajlarda gösterilir.
          const showRoleBadge = !isRight && !isInternal;
          const roleBadgeLabel = isCustomerAuthor ? t('ticketDetail.roleCustomer') : t('ticketDetail.roleAgent');

          let bubbleBg, bubbleText;
          if (isInternal) { bubbleBg = 'border'; bubbleText = ''; }
          else if (isRight) { bubbleBg = 'bg-primary-500'; bubbleText = 'text-white'; }
          else { bubbleBg = 'border'; bubbleText = ''; }

          return (
            <div
              key={`${item._type}-${item.id}`}
              className={`max-w-[85%] sm:max-w-[70%] rounded-xl px-4 py-3 text-sm animate-fade-in break-words ${bubbleBg} ${bubbleText} ${
                isInternal ? 'self-end' : isRight ? 'self-end' : 'self-start'
              }`}
              style={
                isInternal
                  ? { backgroundColor: isDark ? 'rgba(245,158,11,0.08)' : '#fffbeb', borderColor: isDark ? 'rgba(245,158,11,0.2)' : '#fde68a' }
                  : !isRight
                    ? { backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }
                    : {}
              }
            >
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <span
                  className={`text-xs font-semibold ${isRight && !isInternal ? 'text-white/80' : ''}`}
                  style={!isRight || isInternal ? { color: 'var(--text-secondary)' } : {}}
                >
                  {displayName}
                </span>
                {showRoleBadge && (
                  <span
                    className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                    style={isCustomerAuthor
                      ? { backgroundColor: isDark ? 'rgba(148,163,184,0.2)' : '#e2e8f0', color: isDark ? '#cbd5e1' : '#475569' }
                      : { backgroundColor: isDark ? 'rgba(59,130,246,0.2)' : '#dbeafe', color: isDark ? '#93c5fd' : '#1d4ed8' }
                    }
                  >
                    {roleBadgeLabel}
                  </span>
                )}
                {isInternal && (
                  <span
                    className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                    style={{ backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' }}
                  >
                    {t('ticketDetail.internal')}
                  </span>
                )}
              </div>

              {item._type === 'attachment' ? (
                <div className="flex flex-col gap-2">
                  <div className="flex items-center gap-2 cursor-pointer" onClick={() => handleDownloadAttachment(item)} title="Click to download">
                    {getFileIcon(item.fileType)}
                    <span className="font-semibold text-sm">{item.fileName}</span>
                  </div>
                  <button
                    className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors cursor-pointer border"
                    style={{ borderColor: 'rgba(255,255,255,0.15)', backgroundColor: 'rgba(255,255,255,0.1)' }}
                    onClick={() => handleDownloadAttachment(item)}
                  >
                    <Download className="h-3 w-3" />
                    {t('ticketDetail.download')}
                  </button>
                </div>
              ) : (
                <div
                  style={
                    !isRight || isInternal
                      ? { color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }
                      : { whiteSpace: 'pre-wrap' }
                  }
                >
                  {item.message}
                </div>
              )}

              <div
                className={`text-[11px] mt-1 ${isRight && !isInternal ? 'text-white/60' : ''}`}
                style={!isRight || isInternal ? { color: 'var(--text-tertiary)' } : {}}
              >
                {formatShortDate(item.createdAt)}
              </div>
            </div>
          );
        })}
        <div ref={chatEndRef} />
      </div>

      {ticket.status !== 'CLOSED' && !(isCustomer && ticket.status === 'RESOLVED') && (
        <div className="border-t px-4 sm:px-5 py-4" style={{ borderColor: 'var(--border-color)' }}>
          {isAgent && (
            <div className="flex flex-wrap items-center gap-2 mb-3">
              <button
                className={`rounded-full px-3.5 py-1.5 text-xs font-semibold border transition-colors cursor-pointer ${
                  commentType === 'EXTERNAL' ? 'bg-primary-500 text-white border-primary-500' : ''
                }`}
                style={commentType !== 'EXTERNAL' ? { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' } : {}}
                onClick={() => setCommentType('EXTERNAL')}
              >
                {t('ticketDetail.replyToCustomer')}
              </button>
              <button
                className="rounded-full px-3.5 py-1.5 text-xs font-semibold border transition-colors cursor-pointer"
                style={
                  commentType === 'INTERNAL'
                    ? { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e', borderColor: isDark ? 'rgba(245,158,11,0.3)' : '#fcd34d' }
                    : { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }
                }
                onClick={() => setCommentType('INTERNAL')}
              >
                {t('ticketDetail.internalNote')}
              </button>

              {/* ⚡ Hazır yanıtlar */}
              <div className="relative ml-auto">
                <button
                  ref={zapBtnRef}
                  type="button"
                  onClick={() => setPickerOpen((o) => !o)}
                  className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold border transition-colors cursor-pointer"
                  style={pickerOpen
                    ? { backgroundColor: 'rgba(59,130,246,0.12)', color: '#3b82f6', borderColor: 'rgba(59,130,246,0.4)' }
                    : { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                  aria-haspopup="dialog"
                  aria-expanded={pickerOpen}
                >
                  <Zap className="h-3.5 w-3.5" />
                  {t('cannedResponses.button')}
                </button>
                <CannedResponsePicker
                  open={pickerOpen}
                  onClose={() => setPickerOpen(false)}
                  templates={templates}
                  loading={cannedLoading}
                  error={cannedError}
                  ctx={placeholderCtx}
                  previewLang={previewLang}
                  onPreviewLangChange={setPreviewLang}
                  commentType={commentType}
                  productId={ticket?.productId ?? null}
                  recentIds={recentIds}
                  onInsert={insertFromPicker}
                  onToggleFavorite={toggleFavorite}
                  onManage={openManage}
                  canManage
                  anchorRef={pickerAnchorRef}
                  triggerRef={zapBtnRef}
                />
              </div>
            </div>
          )}

          {/* Quick chips — favorites + recently used */}
          {isAgent && chipTemplates.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-2">
              {chipTemplates.map((tpl) => (
                <button
                  key={tpl.id}
                  type="button"
                  onClick={() => insertTemplateAtCaret(tpl)}
                  title={tpl.title}
                  className="inline-flex max-w-[12rem] items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors cursor-pointer hover:border-primary-500"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                >
                  {tpl.favorite
                    ? <Star className="h-3 w-3 flex-shrink-0" fill="#f59e0b" style={{ color: '#f59e0b' }} />
                    : <Zap className="h-3 w-3 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />}
                  <span className="truncate">{tpl.title}</span>
                </button>
              ))}
            </div>
          )}

          <div className="relative flex items-end gap-2">
            {isAgent && slashOpen && (
              <SlashAutocomplete
                matches={slashMatches}
                activeIndex={Math.min(slashIndex, Math.max(0, slashMatches.length - 1))}
                ctx={placeholderCtx}
                previewLang={previewLang}
                onSelect={selectSlash}
                onHover={setSlashIndex}
                anchorRef={slashAnchorRef}
              />
            )}
            <textarea
              ref={textareaRef}
              placeholder={isAgent ? t('cannedResponses.composerHint') : t('ticketDetail.messagePlaceholder')}
              value={message}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              onKeyUp={syncCaret}
              onClick={syncCaret}
              onSelect={syncCaret}
              disabled={sending || cooldown > 0}
              rows={2}
              maxLength={COMMENT_MESSAGE_MAX_LENGTH}
              className="flex-1 resize-none rounded-lg border px-3 py-2.5 text-sm outline-none transition-all focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            />
            <input
              ref={fileInputRef}
              type="file"
              style={{ display: 'none' }}
              onChange={(e) => handleFileUpload(e.target.files?.[0])}
            />
            <button
              className="flex h-10 w-10 items-center justify-center rounded-lg border transition-colors cursor-pointer"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
              title="Attach File"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              type="button"
            >
              {uploading
                ? <div className="h-4 w-4 rounded-full border-2 animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
                : <Paperclip className="h-4 w-4" />}
            </button>
            <button
              className="flex h-10 items-center gap-2 rounded-lg px-4 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              onClick={handleSendComment}
              disabled={sending || !message.trim() || message.length > COMMENT_MESSAGE_MAX_LENGTH || cooldown > 0}
            >
              <Send className="h-4 w-4" />
              {cooldown > 0 ? `${cooldown}s` : t('ticketDetail.send')}
            </button>
          </div>

          {/* Unfilled placeholder warning */}
          {isAgent && unfilled.length > 0 && (
            <div className="mt-1.5 flex items-center gap-1.5 text-xs" style={{ color: 'var(--color-warning-500, #f59e0b)' }}>
              <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
              <span>{t('cannedResponses.unfilledWarning', { count: unfilled.length, fields: unfilled.join(', ') })}</span>
            </div>
          )}

          <div
            className="mt-1.5 text-right text-xs tabular-nums"
            style={{
              color:
                message.length >= COMMENT_MESSAGE_MAX_LENGTH
                  ? 'var(--color-danger-500, #ef4444)'
                  : message.length >= COMMENT_MESSAGE_MAX_LENGTH * 0.9
                    ? 'var(--color-warning-500, #f59e0b)'
                    : 'var(--text-secondary)',
            }}
          >
            {message.length}/{COMMENT_MESSAGE_MAX_LENGTH}
          </div>
        </div>
      )}
    </div>
  );
}
