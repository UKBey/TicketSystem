import React from 'react';
import i18n from '../i18n';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
    this.handleReset = this.handleReset.bind(this);
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error('ErrorBoundary caught error:', error, info);
  }

  handleReset() {
    this.setState({ hasError: false, error: null });
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          className="rounded-2xl border p-6 flex flex-col gap-3"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        >
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            {i18n.t('errorBoundary.title')}
          </h3>
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {i18n.t('errorBoundary.message')}
          </p>
          <button
            type="button"
            onClick={this.handleReset}
            className="self-start rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors"
            style={{ backgroundColor: 'var(--bg-sidebar)', color: 'var(--text-inverse)' }}
          >
            {i18n.t('errorBoundary.retry')}
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
