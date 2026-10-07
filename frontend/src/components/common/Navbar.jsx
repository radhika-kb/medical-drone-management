import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { wsService } from '../../services/websocket';
import { 
  Plane, 
  Activity, 
  User, 
  LogOut, 
} from 'lucide-react';

export const Navbar = ({ onOpenNotification }) => {
  const { user, logout } = useAuth();
  const [wsConnected, setWsConnected] = useState(false);

  useEffect(() => {
    wsService.connect(
      () => setWsConnected(true),
      () => setWsConnected(false)
    );

    // Heartbeat check on WebSocket connection state
    const interval = setInterval(() => {
      setWsConnected(wsService.isConnected);
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  const getRoleDisplayName = (role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return 'System Administrator';
      case 'ROLE_CENTRAL_HUB':
        return 'Central Hub Operator';
      case 'ROLE_REQUESTING_HOSPITAL':
        return 'Requesting Hospital';
      case 'ROLE_SUPPLYING_HOSPITAL':
        return 'Supplying Hospital';
      default:
        return 'Medical Personnel';
    }
  };

  const getRoleBadgeClass = (role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return 'badge-danger';
      case 'ROLE_CENTRAL_HUB':
        return 'badge-primary';
      case 'ROLE_REQUESTING_HOSPITAL':
        return 'badge-info';
      case 'ROLE_SUPPLYING_HOSPITAL':
        return 'badge-success';
      default:
        return 'badge-neutral';
    }
  };

  return (
    <header className="navbar" role="banner" style={{
      background: '#FFFFFF',
      borderBottom: '1px solid var(--color-border)',
      padding: '0.625rem 1.5rem',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 1000,
    }}>
      {/* Brand & Hub Info */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.875rem' }}>
        <div style={{
          width: '38px',
          height: '38px',
          borderRadius: '8px',
          background: 'var(--color-nhs-blue)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#FFFFFF',
          boxShadow: '0 2px 4px rgba(0,94,184,0.2)'
        }}>
          <Plane size={20} />
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.05rem', color: 'var(--color-text)', letterSpacing: '-0.01em' }}>
            MedDrone <span style={{ color: 'var(--color-nhs-blue)', fontWeight: 800 }}>Bengaluru</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
            Autonomous Multi-Agent Medical Air Delivery Network
          </div>
        </div>
      </div>

      {/* Live STOMP Telemetry Indicator */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <div 
          title={wsConnected ? 'Connected to Spring Boot STOMP Realtime Gateway' : 'Connecting to STOMP Broker...'}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.375rem',
            padding: '0.25rem 0.625rem',
            borderRadius: '9999px',
            fontSize: '0.75rem',
            fontWeight: 500,
            background: wsConnected ? 'var(--color-success-bg)' : '#F3F4F6',
            color: wsConnected ? 'var(--color-nhs-green)' : 'var(--color-text-secondary)',
            border: `1px solid ${wsConnected ? 'rgba(0,127,59,0.2)' : 'var(--color-border)'}`,
          }}
        >
          <span style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: wsConnected ? 'var(--color-nhs-green)' : 'var(--color-text-muted)',
            display: 'inline-block',
          }} />
          <span>{wsConnected ? 'Live Telemetry Active' : 'Connecting Broker...'}</span>
        </div>

        {/* Current User Pill */}
        {user && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.625rem',
            padding: '0.25rem 0.75rem',
            borderRadius: '6px',
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
          }}>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--color-text)' }}>
                {user.fullName || user.email}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', justifyContent: 'flex-end' }}>
                <span className={`status-badge ${getRoleBadgeClass(user.role)}`} style={{ fontSize: '0.7rem', padding: '0.1rem 0.4rem' }}>
                  {getRoleDisplayName(user.role)}
                </span>
                {user.hospitalName && (
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)', maxWidth: '140px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    • {user.hospitalName}
                  </span>
                )}
              </div>
            </div>

            <button
              onClick={logout}
              className="btn btn-secondary"
              title="Logout"
              style={{ padding: '0.375rem', borderRadius: '6px', minWidth: 'auto' }}
            >
              <LogOut size={16} />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};

export default Navbar;
