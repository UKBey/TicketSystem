import { useState, useEffect } from 'react';
import api from '../../services/api';

export default function ProductPanel() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Urun ekleme/duzenleme penceresinin aciklik durumu ve secili kayit.
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentProduct, setCurrentProduct] = useState(null); // null oldugunda form yeni urun modunda calisir.
  
  // Modal icerisinde kullanilan form alanlarinin yerel durumu.
  const [formData, setFormData] = useState({ name: '', isActive: true });

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const res = await api.get('/products');
      setProducts(res.data);
    } catch (err) {
      console.error('Ürünler yüklenemedi:', err);
      setError('Ürünler yüklenirken bir hata oluştu.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, []);

  const openModal = (product = null) => {
    if (product) {
      setCurrentProduct(product);
      setFormData({ name: product.name, isActive: product.isActive });
    } else {
      setCurrentProduct(null);
      setFormData({ name: '', isActive: true });
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
      alert('Ürün adı boş olamaz.');
      return;
    }
    
    try {
      if (currentProduct) {
        // Duzenleme modunda secili urun kaydi guncellenir.
        const res = await api.put(`/products/${currentProduct.id}`, formData);
        setProducts(products.map(p => p.id === currentProduct.id ? res.data : p));
      } else {
        // Yeni urun modunda olusan kayit listeye eklenir.
        const res = await api.post('/products', formData);
        setProducts([...products, res.data]);
      }
      closeModal();
    } catch (err) {
      alert(err.response?.data?.message || 'Ürün kaydedilemedi.');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bu ürünü silmek istediğinize emin misiniz?')) return;
    try {
      await api.delete(`/products/${id}`);
      setProducts(products.filter(p => p.id !== id));
    } catch (err) {
      alert(err.response?.data?.message || 'Ürün silinemedi.');
    }
  };

  if (loading) {
    return (
      <div className="app-loading" style={{ minHeight: '60vh' }}>
        <div className="spinner" />
      </div>
    );
  }

  return (
    <>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1 className="page-title">Product Panel</h1>
        <button className="btn btn-primary" onClick={() => openModal()}>+ Yeni Ürün</button>
      </div>

      {error && (
        <div style={{ color: 'var(--color-danger)', marginBottom: 'var(--space-4)' }}>
          {error}
        </div>
      )}

      <div className="card">
        <div className="card-header">Sistem Ürünleri</div>
        <div className="card-body" style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Ürün Adı</th>
                <th>Durum</th>
                <th style={{ width: '150px', textAlign: 'right' }}>İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {products.map(product => (
                <tr key={product.id}>
                  <td style={{ fontWeight: 600 }}>{product.id}</td>
                  <td>{product.name}</td>
                  <td>
                    <span className={`badge ${product.isActive ? 'badge-success' : 'badge-closed'}`}>
                      {product.isActive ? 'Aktif' : 'Pasif'}
                    </span>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button 
                      className="btn btn-outline btn-sm" 
                      style={{ marginRight: '8px' }}
                      onClick={() => openModal(product)}
                    >
                      Düzenle
                    </button>
                    <button 
                      className="btn btn-danger btn-sm"
                      onClick={() => handleDelete(product.id)}
                    >
                      Sil
                    </button>
                  </td>
                </tr>
              ))}
              {products.length === 0 && (
                <tr>
                  <td colSpan="4" style={{ textAlign: 'center', padding: 'var(--space-6)' }}>
                    Hiç ürün bulunamadı.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Urun ekleme/duzenleme islemlerini yapan modal. */}
      {isModalOpen && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{currentProduct ? 'Ürünü Düzenle' : 'Yeni Ürün Ekle'}</h3>
              <button className="modal-close" onClick={closeModal}>×</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Ürün Adı</label>
                <input 
                  type="text" 
                  className="form-input" 
                  value={formData.name}
                  onChange={e => setFormData({ ...formData, name: e.target.value })}
                  placeholder="Örn: E-Ticaret Modülü"
                />
              </div>
              <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <input 
                  type="checkbox" 
                  id="isActive"
                  checked={formData.isActive}
                  onChange={e => setFormData({ ...formData, isActive: e.target.checked })}
                />
                <label htmlFor="isActive" style={{ margin: 0 }}>Aktif (Kullanıma Açık)</label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={closeModal}>İptal</button>
              <button className="btn btn-primary" onClick={handleSave}>Kaydet</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
