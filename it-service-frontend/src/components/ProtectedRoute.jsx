import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children, allowedRoles }) {
  const { authenticated, roles, login } = useAuth();

  if (!authenticated) {
    return <Navigate to="/" replace />;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    const hasAccess = allowedRoles.some((role) => roles.includes(role));
    if (!hasAccess) {
      return <Navigate to="/" replace />;
    }
  }

  return children;
}
