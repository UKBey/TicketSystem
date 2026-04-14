import { useState, useEffect } from 'react';
import api from '../../services/api';

export default function AdminPanel() {
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [error, setError] = useState('');

  const fetchData = async () => {
    try {
      setLoading(true);
      const [usersRes, productsRes] = await Promise.all([
        api.get('/users'),
        api.get('/products')
      ]);
      setUsers(usersRes.data);
      setProducts(productsRes.data);
    } catch (err) {
      console.error('Veriler yüklenemedi:', err);
      setError('Veriler yüklenirken bir hata oluştu.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleAssignProduct = async (userId) => {
    if (!selectedProductId) {
      alert('Lütfen eklenecek bir ürün seçin.');
      return;
    }
    try {
      const res = await api.post(`/users/${userId}/products/${selectedProductId}`);
      // API'den donen guncel kullanici nesnesi local listedeki kaydin yerine yazilir.
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Ürün atanamadı.');
    }
  };

  const handleRemoveProduct = async (userId, productId) => {
    if (!window.confirm('Bu ürün yetkisini kaldırmak istediğinize emin misiniz?')) return;
    
    try {
      const res = await api.delete(`/users/${userId}/products/${productId}`);
      // Yetki kaldirma sonrasi donen son durum local listede eszamanlanir.
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Ürün yetkisi kaldırılamadı.');
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
      <div className="page-header">
        <h1 className="page-title">Admin Panel</h1>
      </div>

      {error && (
        <div style={{ color: 'var(--color-danger)', marginBottom: 'var(--space-4)' }}>
          {error}
        </div>
      )}

      <div className="card">
        <div className="card-header">Kullanıcı & Ürün Atamaları</div>
        <div className="card-body" style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>İsim</th>
                <th>E-mail</th>
                <th>Rol</th>
                <th>Yetkili Ürünler</th>
                <th style={{ width: '250px' }}>Ürün Ata</th>
              </tr>
            </thead>
            <tbody>
              {users.map(user => (
                <tr key={user.id} style={{ cursor: 'default' }}>
                  <td style={{ fontWeight: 600 }}>{user.fullName}</td>
                  <td>{user.email}</td>
                  <td>
                    <span className="badge badge-in-progress" style={{ fontSize: '10px' }}>
                      {user.role}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                      {user.authorizedProducts && user.authorizedProducts.map(prod => (
                        <span key={prod.id} className="chip">
                          {prod.name}
                          <button 
                            className="chip-remove" 
                            onClick={() => handleRemoveProduct(user.id, prod.id)}
                            title="Yetkiyi Kaldır"
                          >
                            ×
                          </button>
                        </span>
                      ))}
                      {(!user.authorizedProducts || user.authorizedProducts.length === 0) && (
                        <span style={{ color: 'var(--color-text-light)', fontSize: 'element' }}>Ürün yok</span>
                      )}
                    </div>
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                      <select 
                        className="form-select" 
                        style={{ padding: '4px 8px', borderColor: 'var(--color-border)', fontSize: 'var(--font-size-xs)' }}
                        onChange={(e) => setSelectedProductId(e.target.value)}
                        value={selectedProductId}
                      >
                        <option value="">Seç...</option>
                        {products
                          .filter(p => !(user.authorizedProducts || []).some(ap => ap.id === p.id))
                          .map(p => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                          ))
                        }
                      </select>
                      <button 
                        className="btn btn-primary btn-sm"
                        onClick={() => handleAssignProduct(user.id)}
                      >
                        Ekle
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {users.length === 0 && (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: 'var(--space-6)' }}>
                    Kullanıcı bulunamadı.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
