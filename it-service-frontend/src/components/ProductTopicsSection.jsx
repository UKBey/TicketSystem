import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Plus, Pencil, Trash2, X, Tag } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { useToast } from '../context/ToastContext';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';
import BilingualField from './BilingualField';
import Button from './Button';

export default function ProductTopicsSection({ productId, isAdmin }) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const [topics, setTopics] = useState([]);
  const [loading, setLoading] = useState(true);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [formData, setFormData] = useState({ nameTr: '', nameEn: '', isActive: true });
  const [activeLang, setActiveLang] = useState('tr'); // modal'daki çift dilli ad sekmesi
  const [saving, setSaving] = useState(false);

  const fetchTopics = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get(`/products/${productId}/topics`, {
        params: isAdmin ? { includeInactive: true } : undefined,
      });
      setTopics(sortByLocalizedName(res.data));
    } catch (err) {
      console.error('Could not load topics:', err);
      toast.error(t('topic.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [productId, isAdmin, t, toast]);

  useEffect(() => {
    fetchTopics();
  }, [fetchTopics]);

  const openCreate = () => {
    setEditing(null);
    setFormData({ nameTr: '', nameEn: '', isActive: true });
    setActiveLang(i18n.language?.startsWith('tr') ? 'tr' : 'en');
    setIsModalOpen(true);
  };

  const openEdit = (topic) => {
    setEditing(topic);
    setFormData({ nameTr: topic.nameTr || '', nameEn: topic.nameEn || '', isActive: topic.isActive });
    setActiveLang(topic.nameTr ? 'tr' : (topic.nameEn ? 'en' : 'tr'));
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditing(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.nameTr.trim() && !formData.nameEn.trim()) {
      toast.error(t('topic.errorNameRequired'));
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        const res = await api.put(`/topics/${editing.id}`, formData);
        setTopics((prev) => prev.map((tp) => (tp.id === editing.id ? res.data : tp)));
      } else {
        const res = await api.post(`/products/${productId}/topics`, formData);
        setTopics((prev) => sortByLocalizedName([...prev, res.data]));
      }
      toast.success(t('topic.saveSuccess'));
      closeModal();
    } catch (err) {
      toast.error(err.response?.data?.message || t('topic.errorSave'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (topic) => {
    if (!window.confirm(t('topic.confirmDelete', { name: localizedName(topic) }))) return;
    try {
      await api.delete(`/topics/${topic.id}`);
      setTopics((prev) => prev.filter((tp) => tp.id !== topic.id));
      toast.success(t('topic.deleteSuccess'));
    } catch (err) {
      toast.error(err.response?.data?.message || t('topic.errorDelete'));
    }
  };

  return (
    <div
      className="rounded-xl border overflow-hidden mb-6"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
    >
      <div
        className="px-4 sm:px-6 py-4 border-b flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
        style={{ borderColor: 'var(--border-color)' }}
      >
        <div className="flex items-center gap-2 flex-wrap min-w-0">
          <Tag className="h-4 w-4 flex-shrink-0" style={{ color: 'var(--text-secondary)' }} />
          <span className="font-semibold text-sm break-words" style={{ color: 'var(--text-primary)' }}>
            {t('topic.sectionTitle')}
          </span>
          <span className="text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
            {t('topic.totalCount', { count: topics.length })}
          </span>
        </div>
        {isAdmin && (
          <Button
            onClick={openCreate}
            size="sm"
            className="self-start sm:self-auto"
          >
            <Plus className="h-3.5 w-3.5" />
            {t('topic.add')}
          </Button>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-10">
          <div
            className="h-6 w-6 rounded-full border-[3px] animate-spin"
            style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
          />
        </div>
      ) : topics.length === 0 ? (
        <div className="py-10 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
          {isAdmin ? t('topic.emptyAdmin') : t('topic.emptyUser')}
        </div>
      ) : (
        <>
          <ul className="lg:hidden p-4 space-y-3">
            {topics.map((topic) => (
              <li
                key={topic.id}
                className="rounded-xl border p-4"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
              >
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="text-sm font-semibold break-words min-w-0" style={{ color: 'var(--text-primary)' }}>
                    {localizedName(topic)}
                  </span>
                  <span
                    className={`shrink-0 inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      topic.isActive
                        ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                    }`}
                  >
                    {topic.isActive ? t('topic.statusActive') : t('topic.statusInactive')}
                  </span>
                </div>
                {isAdmin && (
                  <div
                    className="mt-3 pt-3 border-t flex flex-col gap-2"
                    style={{ borderColor: 'var(--border-color-light)' }}
                  >
                    <button
                      className="w-full inline-flex items-center justify-center gap-1 rounded-lg border px-2.5 py-2 text-xs font-medium transition-colors cursor-pointer"
                      style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                      onClick={() => openEdit(topic)}
                    >
                      <Pencil className="h-3 w-3" />
                      {t('topic.edit')}
                    </button>
                    <Button
                      variant="danger"
                      size="sm"
                      fullWidth
                      onClick={() => handleDelete(topic)}
                    >
                      <Trash2 className="h-3 w-3" />
                      {t('topic.delete')}
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>

          <div className="hidden lg:block">
            {/* table-fixed: uzun topic ismi kendi sütununda sarılsın; aksiyon/durum sütunları
                sabit genişlikle her zaman görünür kalsın (auto-layout uzun ismi taşırıyordu). */}
            <table className="w-full table-fixed">
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <th
                    className="text-left px-4 py-2.5 text-xs font-semibold uppercase tracking-wider border-b"
                    style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}
                  >
                    {t('topic.colName')}
                  </th>
                  <th
                    className="text-left px-4 py-2.5 text-xs font-semibold uppercase tracking-wider border-b"
                    style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', width: '140px' }}
                  >
                    {t('topic.colStatus')}
                  </th>
                  {isAdmin && (
                    <th
                      className="text-right px-4 py-2.5 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', width: '160px' }}
                    >
                      {t('topic.colActions')}
                    </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {topics.map((topic) => (
                  <tr key={topic.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                    <td className="px-4 py-2.5 text-sm font-medium break-words" style={{ color: 'var(--text-primary)' }}>
                      {localizedName(topic)}
                    </td>
                    <td className="px-4 py-2.5">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                          topic.isActive
                            ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                            : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                        }`}
                      >
                        {topic.isActive ? t('topic.statusActive') : t('topic.statusInactive')}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-2.5 text-right">
                        <div className="flex justify-end gap-2">
                          <button
                            className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors cursor-pointer"
                            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                            onClick={() => openEdit(topic)}
                          >
                            <Pencil className="h-3 w-3" />
                            {t('topic.edit')}
                          </button>
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => handleDelete(topic)}
                          >
                            <Trash2 className="h-3 w-3" />
                            {t('topic.delete')}
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* Portal şart: bu bileşen ProductPanel'de resizable-table hücresi içinde render edilir;
          td'lerin position+z-index'i stacking context yarattığından fixed modal tabloda öteki
          satırların ALTINDA kalır. body'ye taşınınca z-50 overlay her zaman en üsttedir. */}
      {isModalOpen && createPortal(
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
          style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
          onClick={closeModal}
        >
          <div
            className="w-full max-w-md sm:max-w-lg max-h-[90vh] flex flex-col rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {editing ? t('topic.modalEditTitle') : t('topic.modalNewTitle')}
              </h3>
              <button
                onClick={closeModal}
                className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
                style={{ color: 'var(--text-tertiary)' }}
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleSave} className="flex-1 flex flex-col min-h-0">
              <div className="flex-1 overflow-y-auto px-6 py-5 space-y-4">
                <BilingualField
                  label={t('topic.labelName')}
                  required
                  hint={t('topic.nameHint')}
                  lang={activeLang}
                  onLang={setActiveLang}
                  valueTr={formData.nameTr}
                  valueEn={formData.nameEn}
                  onChangeTr={(v) => setFormData({ ...formData, nameTr: v })}
                  onChangeEn={(v) => setFormData({ ...formData, nameEn: v })}
                  maxLength={255}
                />
                <label className="flex items-center gap-2.5 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.isActive}
                    onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                    className="h-4 w-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500 cursor-pointer"
                  />
                  <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {t('topic.labelActive')}
                  </span>
                </label>
              </div>
              <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-6 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={closeModal}
                >
                  {t('topic.cancel')}
                </Button>
                <Button
                  type="submit"
                  disabled={saving}
                >
                  {saving ? t('topic.saving') : t('topic.save')}
                </Button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
