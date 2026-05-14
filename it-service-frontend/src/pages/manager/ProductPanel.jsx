import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Trash2, X, Eye, Search, Tag, ChevronDown, ChevronUp } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import PaginationBar from '../../components/PaginationBar';
import ProductTopicsSection from '../../components/ProductTopicsSection';

const PAGE_SIZE = 10;

export default function ProductPanel() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { getPrimaryRole } = useAuth();
  const role = getPrimaryRole();
  const isAdmin = role === 'AGENT_ADMIN' || role === 'MANAGER';

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch]   = useState('');
  const [page, setPage]       = useState(0);
  const [size, setSize]       = useState(PAGE_SIZE);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentProduct, setCurrentProduct] = useState(null);
  const [formData, setFormData] = useState({ name: '', isActive: true, maxActiveTickets: '' });
  const [expandedTopicsProductId, setExpandedTopicsProductId] = useState(null);

  const fetchProducts = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get('/products');
      setProducts(res.data);
    } catch (err) {
      console.error('Could not load products:', err);
      setError(t('productPanel.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const filtered = products.filter(p =>
    !search || p.name.toLowerCase().includes(search.toLowerCase())
  );
  const totalPages = Math.ceil(filtered.length / size);
  const paginated  = filtered.slice(page * size, page * size + size);

  useEffect(() => { setPage(0); }, [search]);

  const openModal = (product = null) => {
    if (product) {
      setCurrentProduct(product);
      setFormData({
        name: product.name,
        isActive: product.isActive,
        maxActiveTickets: product.maxActiveTickets ?? ''
      });
    } else {
      setCurrentProduct(null);
      setFormData({ name: '', isActive: true, maxActiveTickets: '' });
    }
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setCurrentProduct(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      alert(t('productPanel.errorNameRequired'));
      return;
    }

    const payload = {
      ...formData,
      maxActiveTickets: formData.maxActiveTickets === '' ? null : Number(formData.maxActiveTickets)
    };

    try {
      if (currentProduct) {
        const res = await api.put(`/products/${currentProduct.id}`, payload);
        setProducts(products.map(p => p.id === currentProduct.id ? res.data : p));
      } else {
        const res = await api.post('/products', payload);
        setProducts([...products, res.data]);
      }
      closeModal();
    } catch (err) {
      alert(err.response?.data?.message || t('productPanel.errorSave'));
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('productPanel.confirmDelete'))) return;
    try {
      await api.delete(`/products/${id}`);
      setProducts(products.filter(p => p.id !== id));
    } catch (err) {
      alert(err.response?.data?.message || t('productPanel.errorDelete'));
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-40">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('productPanel.title')}</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {isAdmin ? t('productPanel.subtitleAdmin') : t('productPanel.subtitleUser')}
          </p>
        </div>
        {isAdmin && (
          <button
            onClick={() => openModal()}
            className="inline-flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-all duration-200 hover:shadow-lg hover:shadow-primary-500/25 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            {t('productPanel.newProduct')}
          </button>
        )}
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {/* Header + search */}
        <div className="px-6 py-4 border-b flex flex-wrap items-center justify-between gap-3"
          style={{ borderColor: 'var(--border-color)' }}>
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('productPanel.systemProducts')}
            <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
              {t('productPanel.totalCount', { count: filtered.length })}
            </span>
          </span>
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 pointer-events-none"
              style={{ color: 'var(--text-tertiary)' }} />
            <input
              type="text"
              placeholder={t('productPanel.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="rounded-lg border pl-8 pr-8 py-1.5 text-xs outline-none focus:ring-2 w-48"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            />
            {search && (
              <button onClick={() => setSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer"
                style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('productPanel.colId')}</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('productPanel.colName')}</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('productPanel.colStatus')}</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('productPanel.colMaxTickets')}</th>
                <th className="text-right px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', width: '220px' }}>{t('productPanel.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {paginated.map(product => {
                const topicsOpen = expandedTopicsProductId === product.id;
                return (
                <React.Fragment key={product.id}>
                <tr style={{ borderBottom: topicsOpen ? 'none' : '1px solid var(--border-color-light)' }}>
                  <td className="px-4 py-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{product.id}</td>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{product.name}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      product.isActive
                        ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                    }`}>
                      {product.isActive ? t('productPanel.statusActive') : t('productPanel.statusInactive')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {product.maxActiveTickets == null ? (
                      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300">
                        {t('productPanel.unlimited')}
                      </span>
                    ) : (
                      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                        {product.maxActiveTickets}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2 flex-wrap">
                      <button
                        className="inline-flex items-center gap-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                        style={{
                          borderColor: topicsOpen ? '#3b82f6' : 'var(--border-color)',
                          backgroundColor: topicsOpen ? 'rgba(59,130,246,0.08)' : 'transparent',
                          color: topicsOpen ? '#2563eb' : 'var(--text-secondary)',
                        }}
                        onClick={() => setExpandedTopicsProductId(topicsOpen ? null : product.id)}
                        title={t('productPanel.manageTopics')}
                      >
                        <Tag className="h-3 w-3" />
                        {t('productPanel.topics')}
                        {topicsOpen ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
                      </button>
                      <button
                        className="inline-flex items-center gap-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                        style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                        onClick={() => navigate(`/products/${product.id}`)}
                      >
                        <Eye className="h-3 w-3" />
                        {t('productPanel.view')}
                      </button>
                      {isAdmin && (
                        <>
                          <button
                            className="inline-flex items-center gap-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                            onClick={() => openModal(product)}
                          >
                            <Pencil className="h-3 w-3" />
                            {t('productPanel.edit')}
                          </button>
                          <button
                            className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                            onClick={() => handleDelete(product.id)}
                          >
                            <Trash2 className="h-3 w-3" />
                            {t('productPanel.delete')}
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
                {topicsOpen && (
                  <tr style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                    <td colSpan="5" className="px-6 pb-4 pt-0">
                      <ProductTopicsSection productId={product.id} isAdmin={isAdmin} />
                    </td>
                  </tr>
                )}
                </React.Fragment>
                );
              })}
              {paginated.length === 0 && (
                <tr>
                  <td colSpan="5" className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    {search ? t('productPanel.noProductsFiltered') : t('productPanel.noProducts')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <PaginationBar
          page={page}
          totalPages={totalPages}
          totalItems={filtered.length}
          size={size}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0); }}
        />
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={closeModal}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {currentProduct ? t('productPanel.modalEditTitle') : t('productPanel.modalNewTitle')}
              </h3>
              <button onClick={closeModal} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('productPanel.labelName')}</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={e => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. E-Commerce Module"
                  className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
              </div>
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('productPanel.labelMaxTickets')}</label>
                <input
                  type="number"
                  min="1"
                  value={formData.maxActiveTickets}
                  onChange={e => setFormData({ ...formData, maxActiveTickets: e.target.value })}
                  placeholder={t('productPanel.placeholderUnlimited')}
                  className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
                <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('productPanel.hintMaxTickets')}</p>
              </div>
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={e => setFormData({ ...formData, isActive: e.target.checked })}
                  className="h-4 w-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500 cursor-pointer"
                />
                <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{t('productPanel.labelActive')}</span>
              </label>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <button
                onClick={closeModal}
                className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
              >
                {t('productPanel.cancel')}
              </button>
              <button
                onClick={handleSave}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
              >
                {t('productPanel.save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
