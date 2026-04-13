import { useState, useEffect } from 'react';
import api from '../services/api';

export default function CreateTicketModal({ isOpen, onClose, onCreated }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [productId, setProductId] = useState('');
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      api.get('/products').then((res) => setProducts(res.data)).catch(() => {});
    }
  }, [isOpen]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !productId) {
      setError('Başlık ve Ürün alanları zorunludur.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await api.post('/tickets', {
        title,
        description,
        priority,
        productId: Number(productId),
      });
      onCreated(res.data);
      resetForm();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Bilet oluşturulamadı.');
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setTitle('');
    setDescription('');
    setPriority('MEDIUM');
    setProductId('');
    setError('');
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Yeni Destek Talebi</h3>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div style={{ color: 'var(--color-danger)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--space-3)' }}>
                {error}
              </div>
            )}
            <div className="form-group">
              <label>Başlık *</label>
              <input
                className="form-input"
                type="text"
                placeholder="Sorununuzu kısaca özetleyin..."
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label>Açıklama</label>
              <textarea
                className="form-textarea"
                placeholder="Detaylı açıklama..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label>Öncelik</label>
              <select className="form-select" value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </div>
            <div className="form-group">
              <label>Ürün *</label>
              <select className="form-select" value={productId} onChange={(e) => setProductId(e.target.value)}>
                <option value="">Ürün seçiniz...</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>İptal</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Oluşturuluyor...' : '🎫 Bilet Oluştur'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
