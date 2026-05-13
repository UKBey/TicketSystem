import { useTranslation } from 'react-i18next';
import {
  Send, Paperclip, File, FileText, FileArchive, Image, Download,
} from 'lucide-react';
import { formatShortDate } from '../../utils/ticketFormatters';

function getFileIcon(fileType) {
  if (!fileType) return <File className="h-5 w-5" />;
  if (fileType.startsWith('image/')) return <Image className="h-5 w-5" />;
  if (fileType.includes('pdf')) return <FileText className="h-5 w-5 text-danger-500" />;
  if (fileType.includes('zip') || fileType.includes('rar') || fileType.includes('tar')) return <FileArchive className="h-5 w-5" />;
  if (fileType.includes('word') || fileType.includes('document')) return <FileText className="h-5 w-5 text-primary-500" />;
  if (fileType.includes('sheet') || fileType.includes('excel')) return <FileText className="h-5 w-5 text-accent-500" />;
  return <File className="h-5 w-5" />;
}

export default function TicketTimeline({
  timeline, ticket, user,
  isAgent, isCustomer, isDark,
  chatEndRef,
  message, setMessage,
  commentType, setCommentType,
  sending, cooldown,
  uploading, fileInputRef,
  handleSendComment, handleFileUpload, handleDownloadAttachment,
}) {
  const { t } = useTranslation();

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendComment();
    }
  };

  return (
    <div
      className="rounded-xl border overflow-hidden"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
    >
      <div className="flex flex-col gap-4 p-5 min-h-[300px] max-h-[500px] overflow-y-auto">
        {timeline.length === 0 && (
          <div className="flex items-center justify-center py-8" style={{ color: 'var(--text-tertiary)' }}>
            <p className="text-sm">{t('ticketDetail.noComments')}</p>
          </div>
        )}
        {timeline.map((item) => {
          const itemAuthorId   = item._type === 'attachment' ? item.uploaderId : item.authorId;
          const itemAuthorName = item._type === 'attachment' ? null : item.authorName;
          const isOwnItem  = itemAuthorId === user?.id;
          const isInternal = item.type === 'INTERNAL';
          const isRight    = isOwnItem && !isInternal;
          const displayName = itemAuthorName || (itemAuthorId === ticket.customerId ? ticket.customerName : 'Agent');

          let bubbleBg, bubbleText;
          if (isInternal) { bubbleBg = 'border'; bubbleText = ''; }
          else if (isRight) { bubbleBg = 'bg-primary-500'; bubbleText = 'text-white'; }
          else { bubbleBg = 'border'; bubbleText = ''; }

          return (
            <div
              key={`${item._type}-${item.id}`}
              className={`max-w-[70%] rounded-xl px-4 py-3 text-sm animate-fade-in ${bubbleBg} ${bubbleText} ${
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
              <div className="flex items-center gap-2 mb-1">
                <span
                  className={`text-xs font-semibold ${isRight && !isInternal ? 'text-white/80' : ''}`}
                  style={!isRight || isInternal ? { color: 'var(--text-secondary)' } : {}}
                >
                  {displayName}
                </span>
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
                <div style={!isRight || isInternal ? { color: 'var(--text-primary)' } : {}}>{item.message}</div>
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
        <div className="border-t px-5 py-4" style={{ borderColor: 'var(--border-color)' }}>
          {isAgent && (
            <div className="flex gap-2 mb-3">
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
            </div>
          )}
          <div className="flex items-end gap-2">
            <textarea
              placeholder={t('ticketDetail.messagePlaceholder')}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={sending || cooldown > 0}
              rows={2}
              className="flex-1 resize-y rounded-lg border px-3 py-2.5 text-sm outline-none transition-all focus:ring-2"
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
              disabled={sending || !message.trim() || cooldown > 0}
            >
              <Send className="h-4 w-4" />
              {cooldown > 0 ? `${cooldown}s` : t('ticketDetail.send')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
