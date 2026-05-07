import { useState, useEffect } from 'react';
import { Plus, X, ChevronDown } from 'lucide-react';
import api from '../../services/api';
import { getAgentLimits, setAgentLimit, deleteAgentLimit } from '../../services/api';

export default function AdminPanel() {
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [error, setError] = useState('');
  
  // Limit override yönetimi için state
  const [selectedAgentId, setSelectedAgentId] = useState('');
  const [agentLimits, setAgentLimits] = useState([]);
  const [limitFormData, setLimitFormData] = useState({});
  const [limitsSectionOpen, setLimitsSectionOpen] = useState(false);
  const [savingLimit, setSavingLimit] = useState(false);

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
      console.error('Could not load data:', err);
      setError('An error occurred while loading data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleAssignProduct = async (userId) => {
    if (!selectedProductId) {
      alert('Please select a product to assign.');
      return;
    }
    try {
      const res = await api.post(`/users/${userId}/products/${selectedProductId}`);
      // API'den donen guncel kullanici nesnesi local listedeki kaydin yerine yazilir.
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not assign product.');
    }
  };

  const handleRemoveProduct = async (userId, productId) => {
    if (!window.confirm('Are you sure you want to remove this product authorization?')) return;
    
    try {
      const res = await api.delete(`/users/${userId}/products/${productId}`);
      // Yetki kaldirma sonrasi donen son durum local listede eszamanlanir.
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not remove product authorization.');
    }
  };

  // Agent limit override'larını yükle
  const fetchAgentLimits = async (agentId) => {
    try {
      const res = await getAgentLimits(agentId);
      setAgentLimits(res.data);
      
      // Form verilerini populate et
      const formData = {};
      res.data.forEach(limit => {
        formData[limit.productId] = {
          useCustomLimit: limit.useCustomLimit,
          maxActiveTickets: limit.maxActiveTickets || ''
        };
      });
      setLimitFormData(formData);
    } catch (err) {
      console.error('Could not load agent limits:', err);
    }
  };

  // Agent seçimini işle
  const handleSelectAgent = (userId) => {
    setSelectedAgentId(userId);
    setLimitsSectionOpen(true);
    fetchAgentLimits(userId);
  };

  // Limit ayarını kaydet
  const handleSaveLimit = async (productId) => {
    setSavingLimit(true);
    try {
      const formConfig = limitFormData[productId] || { useCustomLimit: false, maxActiveTickets: null };
      const maxActiveTickets = formConfig.maxActiveTickets === '' ? null : Number(formConfig.maxActiveTickets);
      
      await setAgentLimit(selectedAgentId, productId, formConfig.useCustomLimit, maxActiveTickets);
      
      // Limitleri yeniden yükle
      await fetchAgentLimits(selectedAgentId);
    } catch (err) {
      console.error('Could not save limit:', err);
      alert(err.response?.data?.message || 'Could not save limit.');
    } finally {
      setSavingLimit(false);
    }
  };

  // Limit override'ını sil
  const handleDeleteLimit = async (productId) => {
    if (!window.confirm('Are you sure you want to delete this limit override?')) return;
    
    try {
      await deleteAgentLimit(selectedAgentId, productId);
      await fetchAgentLimits(selectedAgentId);
    } catch (err) {
      console.error('Could not delete limit:', err);
      alert(err.response?.data?.message || 'Could not delete limit.');
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
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Admin Panel</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Manage user product authorizations.</p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <div className="px-6 py-4 border-b font-semibold text-sm" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
          User & Product Assignments
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Name</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Email</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Role</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Authorized Products</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', width: '250px' }}>Assign Product</th>
                <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', width: '120px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map(user => (
                <tr key={user.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                  <td className="px-4 py-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{user.fullName}</td>
                  <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>{user.email}</td>
                  <td className="px-4 py-3">
                    <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                      {user.role}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1.5">
                      {user.authorizedProducts && user.authorizedProducts.map(prod => (
                        <span key={prod.id} className="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
                          {prod.name}
                          <button 
                            onClick={() => handleRemoveProduct(user.id, prod.id)}
                            title="Remove"
                            className="ml-0.5 rounded hover:text-danger-500 transition-colors cursor-pointer"
                            style={{ color: 'var(--text-tertiary)' }}
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </span>
                      ))}
                      {(!user.authorizedProducts || user.authorizedProducts.length === 0) && (
                        <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>No products</span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <select 
                        className="flex-1 rounded-lg border px-2 py-1.5 text-xs outline-none transition-all focus:ring-2 cursor-pointer"
                        style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        onChange={(e) => setSelectedProductId(e.target.value)}
                        value={selectedProductId}
                      >
                        <option value="">Select...</option>
                        {products
                          .filter(p => !(user.authorizedProducts || []).some(ap => ap.id === p.id))
                          .map(p => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                          ))
                        }
                      </select>
                      <button 
                        className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                        onClick={() => handleAssignProduct(user.id)}
                      >
                        <Plus className="h-3 w-3" />
                        Add
                      </button>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <button 
                      className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-info-500 hover:bg-info-600 transition-colors cursor-pointer"
                      onClick={() => handleSelectAgent(user.id)}
                    >
                      Limit
                    </button>
                  </td>
                </tr>
              ))}
              {users.length === 0 && (
                <tr>
                  <td colSpan="5" className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    No users found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Product Limit Settings Accordion */}
      {selectedAgentId && (
        <div className="mt-6 rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <button
            onClick={() => setLimitsSectionOpen(!limitsSectionOpen)}
            className="w-full flex items-center justify-between px-6 py-4 border-b font-semibold text-sm hover:bg-opacity-50 transition-colors"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)', backgroundColor: 'var(--bg-surface)' }}
          >
            <span>Product Limit Settings for {users.find(u => u.id === selectedAgentId)?.fullName}</span>
            <ChevronDown className={`h-4 w-4 transition-transform ${limitsSectionOpen ? 'rotate-180' : ''}`} />
          </button>

          {limitsSectionOpen && (
            <div className="px-6 py-4">
              {(() => {
                const selectedAgent = users.find(u => u.id === selectedAgentId);
                const authorizedProds = selectedAgent?.authorizedProducts || [];
                
                if (authorizedProds.length === 0) {
                  return <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>No authorized products for this agent.</p>;
                }

                return (
                  <div className="space-y-4">
                    {authorizedProds.map(prod => {
                      const prodDefaultLimit = products.find(p => p.id === prod.id)?.maxActiveTickets;
                      const formConfig = limitFormData[prod.id] || { useCustomLimit: false, maxActiveTickets: '' };
                      const existingLimit = agentLimits.find(l => l.productId === prod.id);

                      return (
                        <div key={prod.id} className="rounded-lg border p-4" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}>
                          <div className="grid grid-cols-4 gap-4 items-end">
                            {/* Product info */}
                            <div>
                              <label className="block text-xs font-semibold mb-1" style={{ color: 'var(--text-tertiary)' }}>Product</label>
                              <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{prod.name}</p>
                              <p className="text-xs mt-1" style={{ color: 'var(--text-tertiary)' }}>
                                Default: {prodDefaultLimit ? `${prodDefaultLimit}` : 'Unlimited'}
                              </p>
                            </div>

                            {/* Checkbox */}
                            <div className="flex items-end gap-2">
                              <input 
                                type="checkbox"
                                checked={formConfig.useCustomLimit}
                                onChange={(e) => {
                                  setLimitFormData({
                                    ...limitFormData,
                                    [prod.id]: {
                                      ...formConfig,
                                      useCustomLimit: e.target.checked
                                    }
                                  });
                                }}
                                className="cursor-pointer"
                              />
                              <label className="text-xs font-medium cursor-pointer" style={{ color: 'var(--text-primary)' }}>Use custom limit</label>
                            </div>

                            {/* Custom limit input */}
                            <div>
                              <label className="block text-xs font-semibold mb-1" style={{ color: 'var(--text-tertiary)' }}>Custom Limit</label>
                              <input 
                                type="number"
                                min="1"
                                placeholder="No limit"
                                disabled={!formConfig.useCustomLimit}
                                value={formConfig.maxActiveTickets}
                                onChange={(e) => {
                                  setLimitFormData({
                                    ...limitFormData,
                                    [prod.id]: {
                                      ...formConfig,
                                      maxActiveTickets: e.target.value
                                    }
                                  });
                                }}
                                className="w-full rounded-lg border px-2 py-1.5 text-xs outline-none transition-all disabled:opacity-50"
                                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                              />
                            </div>

                            {/* Action buttons */}
                            <div className="flex gap-2">
                              <button 
                                onClick={() => handleSaveLimit(prod.id)}
                                disabled={savingLimit}
                                className="flex-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-success-500 hover:bg-success-600 transition-colors cursor-pointer disabled:opacity-50"
                              >
                                Save
                              </button>
                              {existingLimit && (
                                <button 
                                  onClick={() => handleDeleteLimit(prod.id)}
                                  className="rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                                >
                                  <X className="h-3 w-3" />
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                );
              })()}
            </div>
          )}
        </div>
      )}
    </>
  );
}
