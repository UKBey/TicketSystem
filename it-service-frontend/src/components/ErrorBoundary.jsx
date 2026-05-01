import React from 'react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('ErrorBoundary caught error:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="rounded-2xl border p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold">Bir hata oluştu</h3>
          <p className="mt-2 text-sm" style={{ color: 'var(--text-secondary)' }}>Bu bölümü yüklerken bir hata ile karşılaşıldı.</p>
        </div>
      );
    }

    return this.props.children;
  }
}
