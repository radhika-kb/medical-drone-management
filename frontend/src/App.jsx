import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Requests from './pages/Requests';
import Missions from './pages/Missions';
import LiveTracking from './pages/LiveTracking';
import Hospitals from './pages/Hospitals';
import AirborneUnits from './pages/AirborneUnits';
import AIDecisions from './pages/AIDecisions';
import Analytics from './pages/Analytics';
import Alerts from './pages/Alerts';
import Settings from './pages/Settings';
import SupplierDashboard from './pages/supplier/SupplierDashboard';
import AdminDashboard from './pages/admin/AdminDashboard';
import CentralHubDashboard from './pages/centralhub/CentralHubDashboard';

// Protected Route Guard
const ProtectedRoute = ({ children, roles }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        background: 'var(--color-background)',
        fontFamily: 'Inter, sans-serif',
      }}>
        <div style={{ color: 'var(--color-primary-navy)', fontWeight: 700, fontSize: '14px' }}>
          Loading MEDAIR Flight Operations...
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (roles && user.role !== 'ROLE_ADMIN' && !roles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
};

// Root index redirector
const RootRedirect = () => {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to="/dashboard" replace />;
};

const RoleDashboard = () => {
  const { user } = useAuth();
  switch (user?.role) {
    case 'ROLE_ADMIN': return <AdminDashboard />;
    case 'ROLE_REQUESTING_HOSPITAL': return <Dashboard />;
    case 'ROLE_SUPPLYING_HOSPITAL': return <SupplierDashboard />;
    case 'ROLE_CENTRAL_HUB':
    case 'ROLE_OPERATOR': return <CentralHubDashboard />;
    default: return <Navigate to="/login" replace />;
  }
};

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Authentication */}
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<RootRedirect />} />

          {/* Core Operations Pages */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <RoleDashboard />
              </ProtectedRoute>
            }
          />

          <Route
            path="/requests"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_REQUESTING_HOSPITAL', 'ROLE_SUPPLYING_HOSPITAL', 'ROLE_CENTRAL_HUB']}>
                <Requests />
              </ProtectedRoute>
            }
          />

          <Route
            path="/missions"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_REQUESTING_HOSPITAL', 'ROLE_SUPPLYING_HOSPITAL', 'ROLE_CENTRAL_HUB']}>
                <Missions />
              </ProtectedRoute>
            }
          />

          <Route
            path="/tracking"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_REQUESTING_HOSPITAL', 'ROLE_SUPPLYING_HOSPITAL', 'ROLE_CENTRAL_HUB']}>
                <LiveTracking />
              </ProtectedRoute>
            }
          />

          <Route
            path="/hospitals"
            element={
              <ProtectedRoute>
                <Hospitals />
              </ProtectedRoute>
            }
          />

          <Route
            path="/airborne-units"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_CENTRAL_HUB']}>
                <AirborneUnits />
              </ProtectedRoute>
            }
          />

          <Route
            path="/hub"
            element={
              <ProtectedRoute roles={['ROLE_CENTRAL_HUB', 'ROLE_OPERATOR']}>
                <CentralHubDashboard />
              </ProtectedRoute>
            }
          />

          <Route
            path="/ai-decisions"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_CENTRAL_HUB']}>
                <AIDecisions />
              </ProtectedRoute>
            }
          />

          <Route
            path="/analytics"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_CENTRAL_HUB']}>
                <Analytics />
              </ProtectedRoute>
            }
          />

          <Route
            path="/alerts"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_CENTRAL_HUB']}>
                <Alerts />
              </ProtectedRoute>
            }
          />

          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <Settings />
              </ProtectedRoute>
            }
          />

          {/* Role-specific dashboard URLs retained by the navigation. */}
          <Route path="/requests/new" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_REQUESTING_HOSPITAL']}><Navigate to="/dashboard" replace state={{ openCreateRequest: true }} /></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminDashboard /></ProtectedRoute>} />
          <Route path="/admin/fleet" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AirborneUnits /></ProtectedRoute>} />
          <Route path="/admin/catalog" element={<ProtectedRoute roles={['ROLE_ADMIN']}><Settings /></ProtectedRoute>} />
          <Route path="/admin/*" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminDashboard /></ProtectedRoute>} />
          <Route path="/requester/new" element={<ProtectedRoute roles={['ROLE_REQUESTING_HOSPITAL']}><Navigate to="/dashboard" replace state={{ openCreateRequest: true }} /></ProtectedRoute>} />
          <Route path="/requester/*" element={<ProtectedRoute roles={['ROLE_REQUESTING_HOSPITAL']}><Dashboard /></ProtectedRoute>} />
          <Route path="/supplier/*" element={<ProtectedRoute roles={['ROLE_SUPPLYING_HOSPITAL']}><SupplierDashboard /></ProtectedRoute>} />
          <Route path="/hub/*" element={<ProtectedRoute roles={['ROLE_CENTRAL_HUB', 'ROLE_OPERATOR']}><CentralHubDashboard /></ProtectedRoute>} />

          {/* Catch-all fallback */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
