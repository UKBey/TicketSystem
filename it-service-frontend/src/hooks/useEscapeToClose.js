import { useEffect, useRef } from 'react';

/**
 * ESC tuşuna basıldığında onClose'u çağıran reusable hook.
 *
 * Direkt useEffect ile yazılan eski pattern'de `onClose` dependency array'inde
 * olduğu için parent her render'da yeni callback referansı geçince listener
 * attach/remove sırasında race condition ve memory yapışıklığı oluşuyordu.
 * Burada onClose'u ref'te tutarak listener tek seferlik kurulur, sinyal her
 * zaman güncel callback'e düşer.
 *
 * @param {boolean} isActive — modal/dropdown açık mı; false ise listener kurulmaz
 * @param {Function} onClose — ESC bastığında çağrılacak fonksiyon
 * @param {Object} [options]
 * @param {boolean} [options.disabled] — true iken ESC pasifleştirilir (örn. submit ederken iptali engelle)
 */
export function useEscapeToClose(isActive, onClose, options) {
  const onCloseRef = useRef(onClose);
  useEffect(() => { onCloseRef.current = onClose; }, [onClose]);

  const disabled = options?.disabled === true;

  useEffect(() => {
    if (!isActive || disabled) return undefined;
    const handle = (event) => {
      if (event.key === 'Escape') onCloseRef.current?.();
    };
    window.addEventListener('keydown', handle);
    return () => window.removeEventListener('keydown', handle);
  }, [isActive, disabled]);
}
