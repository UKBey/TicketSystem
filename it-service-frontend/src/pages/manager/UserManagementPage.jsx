import { useTranslation } from 'react-i18next';
import { UserPlus } from 'lucide-react';

/**
 * Kullanıcı Yönetimi sayfası — AGENT_ADMIN rolüne özel.
 *
 * Bu bileşen şu an iskelet aşamasındadır.
 * Commit 11'de AdminCreateUserModal, Commit 12'de tam tablo implementasyonu eklenecektir.
 */
export default function UserManagementPage() {
  const { t } = useTranslation();

  return (
    <div>
      {/* Sayfa başlığı */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('userManagement.title')}
          </h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {t('userManagement.subtitle')}
          </p>
        </div>

        {/* Yeni Kullanıcı Oluştur butonu — Commit 12'de modal bağlanacak */}
        <button
          className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
        >
          <UserPlus className="h-4 w-4" />
          {t('userManagement.createUser')}
        </button>
      </div>

      {/* İçerik alanı — Commit 12'de kullanıcı tablosu buraya gelecek */}
      <div
        className="rounded-xl border flex items-center justify-center py-24"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
          {t('common.loading')}
        </p>
      </div>
    </div>
  );
}
