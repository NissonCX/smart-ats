import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/auth';

interface Props {
  children: React.ReactNode;
}

export default function AuthGuard({ children }: Props) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
