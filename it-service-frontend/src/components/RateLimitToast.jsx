import { useState, useEffect } from 'react';
import { AlertCircle, X } from 'lucide-react';

export default function RateLimitToast() {
  const [visible, setVisible] = useState(false);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    const handleRateLimitExceeded = (event) => {
      const retryAfter = event.detail.retryAfter || 60;
      setCountdown(retryAfter);
      setVisible(true);
    };

    window.addEventListener('rate-limit-exceeded', handleRateLimitExceeded);

    return () => {
      window.removeEventListener('rate-limit-exceeded', handleRateLimitExceeded);
    };
  }, []);

  useEffect(() => {
    if (!visible || countdown <= 0) return;

    const timer = setInterval(() => {
      setCountdown((prev) => {
        const next = prev - 1;
        if (next <= 0) {
          setVisible(false);
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [visible, countdown]);

  if (!visible) return null;

  return (
    <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-top-2 duration-300">
      <div className="flex items-center gap-3 rounded-lg border border-warning-200 bg-warning-50 px-4 py-3 text-warning-800 shadow-lg dark:border-warning-500/20 dark:bg-warning-500/10 dark:text-warning-300 min-w-[320px]">
        <AlertCircle className="h-5 w-5 shrink-0" />
        <div className="flex-1 text-sm font-medium">
          Çok fazla işlem yaptınız. Lütfen {countdown} saniye bekleyin.
        </div>
        <button 
          onClick={() => setVisible(false)}
          className="rounded-md p-1 hover:bg-warning-100 dark:hover:bg-warning-500/20 transition-colors"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
