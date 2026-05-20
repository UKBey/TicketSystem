import { View, Text, StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';

/** Kalan SLA süresini "Xsa Ydk" / "Xdk Ysn" biçiminde gösterir. */
function formatSlaTime(ms) {
  const totalSecs = Math.max(0, Math.floor((ms ?? 0) / 1000));
  const totalMins = Math.floor(totalSecs / 60);
  if (totalMins < 60) {
    return `${totalMins}dk ${totalSecs % 60}sn`;
  }
  const hours = Math.floor(totalMins / 60);
  const mins = totalMins % 60;
  return mins > 0 ? `${hours}sa ${mins}dk` : `${hours}sa`;
}

/**
 * Biletin SLA durumunu rozet olarak gösterir.
 * slaInfo: { slaState: 'active'|'paused'|'expired'|'completed', remainingMs }.
 */
export default function SlaBadge({ slaInfo }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  if (!slaInfo || !slaInfo.slaState) return null;

  const { slaState, remainingMs } = slaInfo;

  const palette = theme.dark
    ? {
        breach: { bg: 'rgba(239,68,68,0.22)', fg: '#fca5a5' },
        warning: { bg: 'rgba(245,158,11,0.22)', fg: '#fde68a' },
        success: { bg: 'rgba(34,197,94,0.22)', fg: '#86efac' },
        neutral: { bg: theme.bgSurfaceSecondary, fg: theme.textSecondary },
      }
    : {
        breach: { bg: '#fee2e2', fg: '#991b1b' },
        warning: { bg: '#fef3c7', fg: '#92400e' },
        success: { bg: '#dcfce7', fg: '#166534' },
        neutral: { bg: '#f1f5f9', fg: '#475569' },
      };

  let type = 'neutral';
  let label;

  if (slaState === 'completed') {
    type = 'neutral';
    label = t('ticketDetail.slaCompleted', 'Tamamlandı');
  } else if (slaState === 'expired' || (slaState === 'active' && (remainingMs ?? 0) <= 0)) {
    type = 'breach';
    label = t('ticketDetail.slaExpired', 'Süresi Doldu');
  } else if (slaState === 'paused') {
    type = 'neutral';
    label = `${formatSlaTime(remainingMs)} (${t('ticketDetail.slaPaused', 'Duraklatıldı')})`;
  } else {
    const mins = Math.floor((remainingMs ?? 0) / 60000);
    type = mins < 1 ? 'breach' : mins < 2 ? 'warning' : 'success';
    label = formatSlaTime(remainingMs);
  }

  const c = palette[type];
  return (
    <View style={[styles.badge, { backgroundColor: c.bg }]}>
      <Text style={[styles.text, { color: c.fg }]} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999, alignSelf: 'flex-start' },
  text: { fontSize: 11, fontWeight: '700' },
});
