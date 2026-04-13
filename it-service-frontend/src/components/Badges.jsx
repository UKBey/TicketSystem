const STATUS_MAP = {
  NEW: { label: 'New', className: 'badge-new' },
  IN_PROGRESS: { label: 'In Progress', className: 'badge-in-progress' },
  WAITING_FOR_CUSTOMER: { label: 'Waiting for Customer', className: 'badge-waiting' },
  RESOLVED: { label: 'Resolved', className: 'badge-resolved' },
  CLOSED: { label: 'Closed', className: 'badge-closed' },
};

export function StatusBadge({ status }) {
  const info = STATUS_MAP[status] || { label: status, className: '' };
  return <span className={`badge ${info.className}`}>{info.label}</span>;
}

const PRIORITY_MAP = {
  LOW: { label: 'Low', className: 'badge-low' },
  MEDIUM: { label: 'Medium', className: 'badge-medium' },
  HIGH: { label: 'High', className: 'badge-high' },
  CRITICAL: { label: 'Critical', className: 'badge-critical' },
};

export function PriorityBadge({ priority }) {
  const info = PRIORITY_MAP[priority] || { label: priority, className: '' };
  return <span className={`badge ${info.className}`}>{info.label}</span>;
}
