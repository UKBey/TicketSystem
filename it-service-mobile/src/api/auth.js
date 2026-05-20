import api from './client';

/**
 * Şifre sıfırlama e-postası talep eder. Anonim uç — oturum gerektirmez.
 * language ve theme, gönderilen e-postanın dilini ve görünümünü belirler.
 */
export const requestPasswordReset = (email, language, theme) =>
  api.post('/auth/forgot-password', { email, language, theme });
