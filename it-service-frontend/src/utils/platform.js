/**
 * İşletim sistemi tespiti — komut paleti kısayolunun (⌘K / Ctrl+K) doğru gösterimi için.
 *
 * macOS'te Command (⌘) tuşu, diğer platformlarda Control kullanılır. Modern tarayıcıda
 * `navigator.userAgentData.platform` tercih edilir; eski tarayıcılarda `navigator.platform`
 * ve son çare `userAgent`'a düşülür.
 */
export const isMac = (() => {
  if (typeof navigator === 'undefined') return false;
  const platform =
    navigator.userAgentData?.platform ||
    navigator.platform ||
    navigator.userAgent ||
    '';
  return /mac|iphone|ipad|ipod/i.test(platform);
})();

/** Kısayol ipucunda gösterilecek değiştirici tuş etiketi: macOS'te '⌘', aksi halde 'Ctrl'. */
export const MOD_KEY_LABEL = isMac ? '⌘' : 'Ctrl';
