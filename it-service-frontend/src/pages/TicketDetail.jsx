import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import ActionReasonModal from '../components/ActionReasonModal';
import AgentSelectionModal from '../components/AgentSelectionModal';
import { ArrowLeft, Sparkles, User, FileDown } from 'lucide-react';

import { useTicketDetail } from '../hooks/useTicketDetail';
import { STATUS_OPTIONS } from '../utils/ticketFormatters';
import TicketTimeline from '../components/ticket/TicketTimeline';
import { TicketDetailProvider } from '../components/ticket/TicketDetailContext';
import StatusActionsCard from '../components/ticket/StatusActionsCard';
import TicketDetailsCard from '../components/ticket/TicketDetailsCard';
import WorklogCard from '../components/ticket/WorklogCard';
import AiSummaryModal from '../components/ticket/AiSummaryModal';
import PdfExportModal from '../components/ticket/PdfExportModal';
import CsatModal from '../components/ticket/CsatModal';
import ResolveModal from '../components/ticket/ResolveModal';
import ExtraActionsModal from '../components/ticket/ExtraActionsModal';
import DeleteTicketModal from '../components/ticket/DeleteTicketModal';
import AuditTimeline from '../components/ticket/AuditTimeline';
import ChangeFieldModal from '../components/ticket/ChangeFieldModal';
import { REASON_CODES } from '../utils/reasonCodes';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';

export default function TicketDetail() {
  const { t }        = useTranslation();
  const { id }       = useParams();
  const navigate     = useNavigate();
  const { user, isAgent, isLeadAgent, isAdmin, isCustomer } = useAuth();
  const { theme }    = useTheme();
  const isDark       = theme === 'dark';
  const [aiSummaryModalOpen, setAiSummaryModalOpen] = useState(false);
  const [pdfModalOpen, setPdfModalOpen] = useState(false);
  const [priorityInitial, setPriorityInitial] = useState(null);

  const {
    ticket, loading, timeline, slaInfo, currentDate,
    message, setMessage, commentType, setCommentType, sending, cooldown,
    uploading, fileInputRef, chatEndRef,
    resolveModalOpen, setResolveModalOpen,
    csatModalOpen, setCsatModalOpen,
    extraActionsOpen, setExtraActionsOpen,
    deleteModalOpen, deleting, openDeleteModal, closeDeleteModal, confirmDeleteTicket,
    reasonModal, reasonModalConfig,
    assignModal, setAssignModal,
    handleFileUpload, handleDownloadAttachment,
    handleSendComment, handleWaiting, handleResume, handleReopen, handleClaim,
    handleResolveClick, handleSubmitResolve,
    handleSubmitCsat, handleAssignSuccess,
    handlePriorityChange, handleTopicChange,
    priorityModalOpen, setPriorityModalOpen,
    topicModalOpen, setTopicModalOpen, openTopicModal,
    topicsList, topicsLoading,
    openReasonModal, closeReasonModal, handleReasonConfirm,
  } = useTicketDetail(id, isAgent);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-40">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="flex flex-col items-center justify-center py-20" style={{ color: 'var(--text-tertiary)' }}>
        <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.ticketNotFound')}</h3>
      </div>
    );
  }

  const ticketCode      = `TCK-${String(ticket.id).padStart(3, '0')}`;
  const allowedStatuses = STATUS_OPTIONS[ticket.status] || [];
  // Operasyonel aksiyonlar (claim/worklog/yorum/AI özet) ajan + lead ajan içindir.
  // Bilet atama lead ajan veya admin; kalıcı silme yalnızca admin içindir.
  const canAssign       = isLeadAgent || isAdmin;
  const canDelete       = isAdmin;

  const ticketDetailContextValue = {
    ticket, user, isAgent, isCustomer, isDark,
    timeline, chatEndRef,
    message, setMessage, commentType, setCommentType, sending, cooldown,
    uploading, fileInputRef,
    handleSendComment, handleFileUpload, handleDownloadAttachment,
  };

  return (
    <TicketDetailProvider value={ticketDetailContextValue}>
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4 sm:gap-6">
      {/* Left column */}
      <div className="flex flex-col gap-5 min-w-0">
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 text-sm font-medium transition-colors hover:text-primary-500 cursor-pointer self-start"
          style={{ color: 'var(--text-secondary)' }}
        >
          <ArrowLeft className="h-4 w-4" />
          {t('ticketDetail.back')}
        </button>

        <div>
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3 sm:flex-wrap sm:justify-between">
            <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
              <h1 className="text-xl font-bold break-words" style={{ color: 'var(--text-primary)' }}>{ticketCode}</h1>
              <PriorityBadge priority={ticket.priority} />
              <StatusBadge status={ticket.status} />
            </div>
            <div className="flex items-center gap-2 self-start sm:self-auto">
              {isAgent && (
                <button
                  onClick={() => setAiSummaryModalOpen(true)}
                  className="inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer hover:opacity-80 min-h-[40px] sm:min-h-0"
                  style={{ background: 'linear-gradient(135deg, #7c3aed, #6d28d9)', color: '#fff' }}
                  title={t('ticketDetail.aiSummaryTitle')}
                >
                  <Sparkles className="h-4 w-4" />
                  {t('ticketDetail.aiSummary')}
                </button>
              )}
              <button
                onClick={() => setPdfModalOpen(true)}
                className="inline-flex items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer hover:opacity-80 min-h-[40px] sm:min-h-0"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                title={t('ticketDetail.pdfTitle')}
              >
                <FileDown className="h-4 w-4" />
                {t('ticketDetail.pdf')}
              </button>
            </div>
          </div>
          <div className="text-lg font-semibold mt-1 break-words" style={{ color: 'var(--text-primary)' }}>
            {ticket.title}
          </div>
          <div className="flex items-center gap-2 mt-2 text-sm flex-wrap" style={{ color: 'var(--text-secondary)' }}>
            <span className="inline-flex items-center gap-1.5">
              <User className="h-3.5 w-3.5" aria-label={t('ticketDetail.customerLabel')} />
              {ticket.customerName || ticket.customerId}
            </span>
            <span aria-hidden="true" style={{ color: 'var(--text-tertiary)' }}>•</span>
            <span>{t('ticketDetail.productLabel')}: {localizedName(ticket, 'productName') || ticket.productId}</span>
            {(localizedName(ticket, 'topicName') || ticket.topicId) && (
              <>
                <span aria-hidden="true" style={{ color: 'var(--text-tertiary)' }}>•</span>
                <span>{t('ticketDetail.topicLabel')}: {localizedName(ticket, 'topicName') || ticket.topicId}</span>
              </>
            )}
          </div>
        </div>

        <TicketTimeline />

        <AuditTimeline
          auditLogs={ticket.auditLogs ?? ticket.ticketAuditLogs ?? []}
        />
      </div>

      {/* Right column */}
      <div className="flex flex-col gap-4">
        <StatusActionsCard
          ticket={ticket}
          user={user}
          allowedStatuses={allowedStatuses}
          isAgent={isAgent}
          canAssign={canAssign}
          canDelete={canDelete}
          onWaiting={handleWaiting}
          onResume={handleResume}
          onReopen={handleReopen}
          onClaim={handleClaim}
          onResolveClick={handleResolveClick}
          onSetAssignModal={setAssignModal}
          onExtraActionsOpen={setExtraActionsOpen}
        />

        <TicketDetailsCard
          ticket={ticket}
          slaInfo={slaInfo}
          currentDate={currentDate}
          isCustomer={isCustomer}
          isAgent={isAgent}
          isDark={isDark}
          onPriorityChangeRequest={(p) => { setPriorityInitial(p); setPriorityModalOpen(true); }}
          onOpenTopicModal={openTopicModal}
        />

        <WorklogCard
          ticketId={id}
          ticketStatus={ticket.status}
          isAgent={isAgent}
        />

        {isCustomer && ticket.status === 'RESOLVED' && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
              {t('ticketDetail.resolvedQuestion')}
            </div>
            <div className="p-4">
              <p className="text-xs mb-4 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                {t('ticketDetail.resolvedDesc')}
              </p>
              <div className="flex gap-2">
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors cursor-pointer"
                  onClick={() => setCsatModalOpen(true)}
                >
                  {t('ticketDetail.yesResolved')}
                </button>
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                  onClick={handleReopen}
                >
                  {t('ticketDetail.noResolved')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <ExtraActionsModal
        isOpen={extraActionsOpen}
        onClose={() => setExtraActionsOpen(false)}
        ticket={ticket}
        user={user}
        allowedStatuses={allowedStatuses}
        canDelete={canDelete}
        openReasonModal={openReasonModal}
        onDeleteClick={openDeleteModal}
      />

      <DeleteTicketModal
        isOpen={deleteModalOpen}
        onClose={closeDeleteModal}
        onConfirm={async () => {
          const ok = await confirmDeleteTicket();
          if (ok) navigate(-1);
        }}
        deleting={deleting}
        ticketId={ticket?.id}
        ticketTitle={ticket?.title}
      />

      <CsatModal
        isOpen={csatModalOpen}
        onClose={() => setCsatModalOpen(false)}
        onSubmit={handleSubmitCsat}
      />

      <AiSummaryModal
        isOpen={aiSummaryModalOpen}
        onClose={() => setAiSummaryModalOpen(false)}
        ticketId={id}
        isAgent={isAgent}
      />

      <PdfExportModal
        isOpen={pdfModalOpen}
        onClose={() => setPdfModalOpen(false)}
        ticket={ticket}
        ticketCode={ticketCode}
        isAgent={isAgent}
        isCustomer={isCustomer}
      />

      <ActionReasonModal
        isOpen={reasonModal.isOpen}
        onClose={closeReasonModal}
        onConfirm={handleReasonConfirm}
        title={reasonModalConfig.title}
        description={reasonModalConfig.description}
        confirmLabel={reasonModalConfig.confirmLabel}
        confirmVariant={reasonModalConfig.confirmVariant}
        reasonCodes={reasonModalConfig.reasonCodes}
        reasonTranslationPrefix={reasonModalConfig.reasonTranslationPrefix}
      />

      <AgentSelectionModal
        isOpen={assignModal}
        onClose={() => setAssignModal(false)}
        onSuccess={handleAssignSuccess}
        ticketId={ticket?.id}
        productId={ticket?.productId}
      />

      <ResolveModal
        isOpen={resolveModalOpen}
        onClose={() => setResolveModalOpen(false)}
        onSave={handleSubmitResolve}
      />

      <ChangeFieldModal
        isOpen={priorityModalOpen}
        onClose={() => { setPriorityModalOpen(false); setPriorityInitial(null); }}
        onSave={handlePriorityChange}
        title={t('ticketDetail.changePriorityTitle')}
        description={t('ticketDetail.changePriorityDesc')}
        label={t('ticketDetail.changePriorityLabel')}
        currentValue={ticket.priority}
        initialValue={priorityInitial}
        options={[
          { value: 'LOW',      label: t('ticket.priority.low') },
          { value: 'MEDIUM',   label: t('ticket.priority.medium') },
          { value: 'HIGH',     label: t('ticket.priority.high') },
          { value: 'CRITICAL', label: t('ticket.priority.critical') },
        ]}
        reasonCodes={REASON_CODES.PRIORITY_CHANGE}
        reasonTranslationPrefix="reasonCode.PRIORITY_CHANGE"
      />

      <ChangeFieldModal
        isOpen={topicModalOpen}
        onClose={() => setTopicModalOpen(false)}
        onSave={handleTopicChange}
        title={t('ticketDetail.changeTopicTitle')}
        description={t('ticketDetail.changeTopicDesc')}
        label={t('ticketDetail.changeTopicLabel')}
        currentValue={ticket.topicId}
        loading={topicsLoading}
        options={sortByLocalizedName(topicsList).map((tp) => ({ value: tp.id, label: localizedName(tp) }))}
        reasonCodes={REASON_CODES.TOPIC_CHANGE}
        reasonTranslationPrefix="reasonCode.TOPIC_CHANGE"
      />
    </div>
    </TicketDetailProvider>
  );
}
