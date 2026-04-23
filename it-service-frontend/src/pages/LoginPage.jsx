import { useAuth } from '../context/AuthContext';
import { Headset, ArrowRight } from 'lucide-react';

export default function LoginPage() {
  const { login } = useAuth();

  return (
    <div className="flex min-h-screen items-center justify-center p-4 relative overflow-hidden" style={{ background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 40%, #0f172a 70%, #1a1a2e 100%)' }}>
      {/* Background decorative elements */}
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary-500/10 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-violet-500/10 blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[600px] w-[600px] rounded-full bg-primary-500/5 blur-3xl" />
      </div>

      <div className="relative w-full max-w-md animate-slide-up">
        {/* Main card */}
        <div className="rounded-2xl border p-10 text-center backdrop-blur-xl" style={{ backgroundColor: 'rgba(255,255,255,0.05)', borderColor: 'rgba(255,255,255,0.1)', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
          {/* Logo */}
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-500 to-primary-700 shadow-lg shadow-primary-500/25">
            <Headset className="h-8 w-8 text-white" />
          </div>

          <h1 className="text-3xl font-bold text-white mb-2">IT Service Desk</h1>
          <p className="text-slate-400 text-sm mb-8 leading-relaxed">
            Create, track, and manage your support requests with ease.
          </p>

          <button
            onClick={login}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-500 px-6 py-3.5 text-sm font-semibold text-white transition-all duration-200 hover:bg-primary-600 hover:scale-[1.02] hover:shadow-lg hover:shadow-primary-500/25 active:scale-[0.98] cursor-pointer"
          >
            Sign In
            <ArrowRight className="h-4 w-4" />
          </button>

        </div>
      </div>
    </div>
  );
}
