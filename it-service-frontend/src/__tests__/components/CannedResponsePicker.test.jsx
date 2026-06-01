import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CannedResponsePicker from '../../components/ticket/CannedResponsePicker';

// jsdom does not implement scrollIntoView (used to keep the active option visible).
beforeEach(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
});

const TEMPLATES = [
  { id: 1, title: 'VPN steps', shortcut: 'vpn', scope: 'SHARED', productId: null, visibility: 'EXTERNAL', contentTr: 'VPN: {{musteri.ad}}', contentEn: 'VPN: {{musteri.ad}}', favorite: false },
  { id: 2, title: 'Greeting', shortcut: 'hi', scope: 'PERSONAL', productId: null, visibility: 'BOTH', contentTr: 'Merhaba', contentEn: 'Hello', favorite: true },
  { id: 3, title: 'Escalation note', shortcut: 'esc', scope: 'SHARED', productId: null, visibility: 'INTERNAL', contentTr: 'Eskalasyon', contentEn: 'Escalation', favorite: false },
];

function renderPicker(props = {}) {
  const onInsert = vi.fn();
  const onToggleFavorite = vi.fn();
  const onClose = vi.fn();
  render(
    <CannedResponsePicker
      open
      onClose={onClose}
      templates={TEMPLATES}
      ctx={{ 'musteri.ad': 'Ahmet' }}
      previewLang="en"
      onPreviewLangChange={vi.fn()}
      commentType="EXTERNAL"
      productId={null}
      recentIds={[]}
      onInsert={onInsert}
      onToggleFavorite={onToggleFavorite}
      onManage={vi.fn()}
      {...props}
    />,
  );
  return { onInsert, onToggleFavorite, onClose };
}

describe('CannedResponsePicker', () => {
  it('renders all visible templates as options', () => {
    renderPicker();
    expect(screen.getByText('VPN steps')).toBeInTheDocument();
    expect(screen.getByText('Greeting')).toBeInTheDocument();
    expect(screen.getByText('Escalation note')).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(3);
  });

  it('shows a filled preview (placeholders resolved), not the raw token', () => {
    renderPicker();
    expect(screen.getByText('VPN: Ahmet')).toBeInTheDocument();
    expect(screen.queryByText('VPN: {{musteri.ad}}')).not.toBeInTheDocument();
  });

  it('filters by search query over title/shortcut/content', async () => {
    const user = userEvent.setup();
    renderPicker();
    await user.type(screen.getByPlaceholderText(/Search templates/i), 'vpn');
    expect(screen.getByText('VPN steps')).toBeInTheDocument();
    expect(screen.queryByText('Greeting')).not.toBeInTheDocument();
  });

  it('calls onInsert with the chosen template on click', async () => {
    const user = userEvent.setup();
    const { onInsert } = renderPicker();
    await user.click(screen.getByText('Greeting'));
    expect(onInsert).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
  });

  it('toggles favorite without selecting the template', async () => {
    const user = userEvent.setup();
    const { onInsert, onToggleFavorite } = renderPicker();
    const vpnOption = screen.getByText('VPN steps').closest('[role="option"]');
    const starBtn = within(vpnOption).getByRole('button', { name: /favorite/i });
    await user.click(starBtn);
    expect(onToggleFavorite).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(onInsert).not.toHaveBeenCalled();
  });

  it('filters to personal scope via the tab', async () => {
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByRole('button', { name: 'Personal' }));
    expect(screen.getByText('Greeting')).toBeInTheDocument();
    expect(screen.queryByText('VPN steps')).not.toBeInTheDocument();
  });
});
