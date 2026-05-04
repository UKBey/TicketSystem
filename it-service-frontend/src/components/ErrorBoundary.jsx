import React from 'react';

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
            Bir hata oluştu
          </h3>
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            Bu bölümü yüklerken bir hata ile karşılaşıldı. Sayfayı yenileyin veya tekrar deneyin.
          </p>
          <button
            type="button"
            onClick={this.handleReset}
            className="self-start rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors"
            style={{ backgroundColor: 'var(--bg-sidebar)', color: 'var(--text-inverse)' }}
          >
            Tekrar dene
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
