import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Button from '../../components/Button';

describe('Button', () => {
  it('renders children and defaults to type=button', () => {
    render(<Button>Save</Button>);
    const btn = screen.getByRole('button', { name: 'Save' });
    expect(btn).toBeInTheDocument();
    expect(btn).toHaveAttribute('type', 'button');
  });

  it('fires onClick', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick}>Go</Button>);
    await user.click(screen.getByRole('button', { name: 'Go' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not fire onClick when disabled', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick} disabled>Go</Button>);
    await user.click(screen.getByRole('button', { name: 'Go' }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('injects a ripple span on click when enabled', async () => {
    const user = userEvent.setup();
    render(<Button>Ripple</Button>);
    const btn = screen.getByRole('button', { name: 'Ripple' });
    await user.click(btn);
    expect(btn.querySelector('.btn-ripple')).not.toBeNull();
  });

  it('does not inject a ripple when ripple is disabled', async () => {
    const user = userEvent.setup();
    render(<Button ripple={false}>No ripple</Button>);
    const btn = screen.getByRole('button', { name: 'No ripple' });
    await user.click(btn);
    expect(btn.querySelector('.btn-ripple')).toBeNull();
  });

  it('applies the secondary variant theme styles', () => {
    render(<Button variant="secondary">Cancel</Button>);
    const btn = screen.getByRole('button', { name: 'Cancel' });
    expect(btn.style.backgroundColor).toBe('transparent');
    expect(btn.style.borderColor).toBe('var(--border-color)');
    expect(btn.className).toContain('border');
  });

  it('merges custom className', () => {
    render(<Button className="custom-x">X</Button>);
    expect(screen.getByRole('button', { name: 'X' }).className).toContain('custom-x');
  });
});
