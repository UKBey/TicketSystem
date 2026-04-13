import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🎫</div>
        <h1>IT Service Desk</h1>
        <p>Destek taleplerinizi kolayca oluşturun, takip edin ve yönetin.</p>
        <button className="btn btn-primary login-btn" onClick={login}>
          Giriş Yap
        </button>
      </div>
    </div>
  );
}
