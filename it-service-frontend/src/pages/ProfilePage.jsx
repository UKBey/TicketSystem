import { useAuth } from '../context/AuthContext';

export default function ProfilePage() {
  const { user, roles, getPrimaryRole } = useAuth();
  const primaryRole = getPrimaryRole();

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Profile</h1>
      </div>

      <div className="card" style={{ maxWidth: 760 }}>
        <div className="card-header">Kullanici Bilgileri</div>
        <div className="card-body" style={{ display: 'grid', gap: '12px' }}>
          <div className="detail-info-item">
            <div className="detail-info-label">Ad Soyad</div>
            <div className="detail-info-value">{user?.name || '-'}</div>
          </div>

          <div className="detail-info-item">
            <div className="detail-info-label">Kullanici Adi</div>
            <div className="detail-info-value">{user?.username || '-'}</div>
          </div>

          <div className="detail-info-item">
            <div className="detail-info-label">E-posta</div>
            <div className="detail-info-value">{user?.email || '-'}</div>
          </div>

          <div className="detail-info-item">
            <div className="detail-info-label">Birincil Rol</div>
            <div className="detail-info-value">
              <span className="badge badge-in-progress">{primaryRole || 'USER'}</span>
            </div>
          </div>

          <div className="detail-info-item">
            <div className="detail-info-label">Tum Roller</div>
            <div className="detail-info-value" style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {roles?.length ? roles.map((role) => (
                <span key={role} className="badge badge-closed">{role}</span>
              )) : <span>-</span>}
            </div>
          </div>

          <div className="detail-info-item">
            <div className="detail-info-label">Kullanici ID</div>
            <div className="detail-info-value" style={{ wordBreak: 'break-all' }}>{user?.id || '-'}</div>
          </div>
        </div>
      </div>
    </>
  );
}
