import { Navigate, Outlet } from "react-router-dom";

export default function ProtectedRoute() {
  const isLoggedIn = !!localStorage.getItem("user");

  return isLoggedIn ? <Outlet /> : <Navigate to="/login" replace />;
}
