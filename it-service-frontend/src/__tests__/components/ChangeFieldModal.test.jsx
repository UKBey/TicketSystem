import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import ChangeFieldModal from '../../components/ticket/ChangeFieldModal';

const baseProps = {
  isOpen: true,
  onClose: vi.fn(),
  title: 'Change topic',
  label: 'Topic',
  reasonCodes: ['MISCATEGORIZED', 'OTHER'],
  reasonTranslationPrefix: 'reasonCode.TOPIC_CHANGE',
};

describe('ChangeFieldModal', () => {
  // Regression: a topicless ticket has currentValue=null. Without a placeholder
  // option the browser shows the first real option while React state stays '',
  // so with a single option no onChange ever fires and Save can never enable.
  it('lets the agent assign the only topic when the ticket has no current topic', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);

    render(
      <ChangeFieldModal
        {...baseProps}
        onSave={onSave}
        currentValue={null}
        options={[{ value: 5, label: 'Billing' }]}
      />,
    );

    // Placeholder option must exist so value='' maps to a real, visible option.
    expect(screen.getByRole('option', { name: 'Select an option...' })).toBeInTheDocument();

    const [fieldSelect, reasonSelect] = screen.getAllByRole('combobox');
    const saveBtn = screen.getByRole('button', { name: 'Save' });

    expect(saveBtn).toBeDisabled();

    await user.selectOptions(fieldSelect, '5');
    await user.selectOptions(reasonSelect, 'MISCATEGORIZED');

    expect(saveBtn).toBeEnabled();

    await user.click(saveBtn);
    expect(onSave).toHaveBeenCalledWith({ value: '5', reasonCode: 'MISCATEGORIZED', note: null });
  });

  // Guard: when a current value exists (e.g. priority change) the placeholder is
  // NOT injected, preserving the original single-list behaviour.
  it('does not inject a placeholder option when a current value is present', () => {
    render(
      <ChangeFieldModal
        {...baseProps}
        onSave={vi.fn()}
        title="Change priority"
        label="Priority"
        currentValue="HIGH"
        options={[
          { value: 'LOW', label: 'Low' },
          { value: 'HIGH', label: 'High' },
        ]}
      />,
    );

    expect(screen.queryByRole('option', { name: 'Select an option...' })).not.toBeInTheDocument();
    // Unchanged selection → Save stays disabled until a different option is picked.
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
