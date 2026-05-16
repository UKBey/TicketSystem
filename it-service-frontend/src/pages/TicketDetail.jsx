import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import ActionReasonModal from '../components/ActionReasonModal';
import AgentSelectionModal from '../components/AgentSelectionModal';
import { ArrowLeft, Sparkles } from 'lucide-react';

import { useTicketDetail } from '../hooks/useTicketDetail';
import { STATUS_OPTIONS } from '../utils/ticketFormatters';
import TicketTimeline from '../components/ticket/TicketTimeline';
import StatusActionsCard from '../components/ticket/StatusActionsCard';
import TicketDetailsCard from '../components/ticket/TicketDetailsCard';
import WorklogCard from '../components/ticket/WorklogCard';
import AiSummaryModal from '../components/ticket/AiSummaryModal';
import CsatModal from '../components/ticket/CsatModal';
import ResolveModal from '../components/ticket/ResolveModal';
import ExtraActionsModal from '../components/ticket/ExtraActionsModal';
import AuditTimeline from '../components/ticket/AuditTimeline';
import ChangeFieldModal from '../components/ticket/ChangeFieldModal';
import { REASON_CODES } from '../utils/reasonCodes';

export default function TicketDetail() {
  const { t }        = useTranslation();
  const { id }       = useParams();
  const navigate     = useNavigate();
  const { user, hasRole } = useAuth();
  const { theme }    = useTheme();
  const isDark       = theme === 'dark';
  const [aiSummaryModalOpen, setAiSummaryModalOpen] = useState(false);

  const {
    ticket, loading, timeline, slaInfo, currentDate,
    message, setMessage, commentType, setCommentType, sending, cooldown,
    uploading, fileInputRef, chatEndRef,
    resolveModalOpen, setResolveModalOpen,
    csatModalOpen, setCsatModalOpen,
    extraActionsOpen, setExtraActionsOpen,
    reasonModal, reasonModalConfig,
    assignModal, setAssignModal,
    handleFileUpload, handleDownloadAttachment,
    handleSendComment, handleStatusChange, handleClaim,
    handleResolveClick, handleSubmitResolve,
    handleSubmitCsat, handleAssignSuccess,
    handlePriorityChange, handleTopicChange,
    priorityModalOpen, setPriorityModalOpen,
    topicModalOpen, setTopicModalOpen, openTopicModal,
    topicsList, topicsLoading,
    openReasonModal, closeReasonModal, handleReasonConfirm,
  } = useTicketDetail(id, hasRole);

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
  const isAgent         = hasRole('AGENT') || hasRole('AGENT_ADMIN');
  const isAgentAdmin    = hasRole('AGENT_ADMIN');
  const isCustomer      = hasRole('CUSTOMER');

  return (
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
            {(isAgent || isAgentAdmin) && (
              <button
                onClick={() => setAiSummaryModalOpen(true)}
                className="inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer hover:opacity-80 self-start sm:self-auto min-h-[40px] sm:min-h-0"
                style={{ background: 'linear-gradient(135deg, #7c3aed, #6d28d9)', color: '#fff' }}
                title={t('ticketDetail.aiSummaryTitle')}
              >
                <Sparkles className="h-4 w-4" />
                {t('ticketDetail.aiSummary')}
              </button>
            )}
          </div>
          <div className="text-lg font-semibold mt-1 break-words" style={{ color: 'var(--text-primary)' }}>
            {ticket.title}
          </div>
          <div className="flex items-center gap-2 mt-2 text-sm flex-wrap" style={{ color: 'var(--text-secondary)' }}>
            <span>👤 {ticket.customerName || ticket.customerId}</span>
            <span style={{ color: 'var(--text-tertiary)' }}>•</span>
            <span>Product: {ticket.productName || ticket.productId}</span>
            {(ticket.topicName || ticket.topicId) && (
              <>
                <span style={{ color: 'var(--text-tertiary)' }}>•</span>
                <span>{t('ticketDetail.topicLabel')}: {ticket.topicName || ticket.topicId}</span>
              </>
            )}
          </div>
        </div>

        <TicketTimeline
          timeline={timeline}
          ticket={ticket}
          user={user}
          isAgent={isAgent}
          isCustomer={isCustomer}
          isDark={isDark}
          chatEndRef={chatEndRef}
          message={message}
          setMessage={setMessage}
          commentType={commentType}
          setCommentType={setCommentType}
          sending={sending}
          cooldown={cooldown}
          uploading={uploading}
          fileInputRef={fileInputRef}
          handleSendComment={handleSendComment}
          handleFileUpload={handleFileUpload}
          handleDownloadAttachment={handleDownloadAttachment}
        />

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
          isAgentAdmin={isAgentAdmin}
          onStatusChange={handleStatusChange}
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
          onOpenPriorityModal={() => setPriorityModalOpen(true)}
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
                  onClick={() => handleStatusChange('IN_PROGRESS')}
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
        isAgentAdmin={isAgentAdmin}
        openReasonModal={openReasonModal}
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
        hasRole={hasRole}
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
        onClose={() => setPriorityModalOpen(false)}
        onSave={handlePriorityChange}
        title={t('ticketDetail.changePriorityTitle')}
        description={t('ticketDetail.changePriorityDesc')}
        label={t('ticketDetail.changePriorityLabel')}
        currentValue={ticket.priority}
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
        options={topicsList.map((tp) => ({ value: tp.id, label: tp.name }))}
        reasonCodes={REASON_CODES.TOPIC_CHANGE}
        reasonTranslationPrefix="reasonCode.TOPIC_CHANGE"
      />
    </div>
  );
}
