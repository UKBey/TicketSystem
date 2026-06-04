/**
 * Kullanıcı rolleriyle ilgili paylaşılan yardımcılar. UserManagementPage ve AdminPanel
 * daha önce bu mantığı birebir kopyalıyordu — tek kaynak burada.
 */

/**
 * Kullanıcının TÜM rollerini döndürür (çoklu rol). Eski tekil `role` alanına geriye-dönük uyum.
 *
 * @param {object} user
 * @returns {string[]}
 */
export const rolesOf = (user) =>
  Array.isArray(user?.roles) && user.roles.length
    ? user.roles
    : (user?.role ? [user.role] : []);

/**
 * Rol badge renkleri. Her rol belirgin biçimde ayrı bir renk tonu kullanır:
 * MANAGER (mor) ile CUSTOMER (yeşil) artık net şekilde ayrışır — eskiden ikisi de
 * yeşil tonundaydı ve birbirine çok benziyordu.
 *
 * @param {string} role
 * @returns {{backgroundColor: string, color: string}}
 */
export const roleBadgeStyle = (role) => {
  switch (role) {
    case 'ADMIN':       return { backgroundColor: 'rgba(245,158,11,0.15)',  color: '#b45309' }; // amber
    case 'MANAGER':     return { backgroundColor: 'rgba(124,58,237,0.15)',  color: '#6d28d9' }; // violet
    case 'LEAD_AGENT':  return { backgroundColor: 'rgba(99,102,241,0.15)',  color: '#4f46e5' }; // indigo
    case 'AGENT':       return { backgroundColor: 'rgba(59,130,246,0.15)',  color: '#1d4ed8' }; // blue
    case 'CUSTOMER':    return { backgroundColor: 'rgba(16,185,129,0.15)',  color: '#047857' }; // emerald
    default:            return { backgroundColor: 'rgba(100,116,139,0.15)', color: '#475569' }; // slate
  }
};
