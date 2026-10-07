import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { wsService } from '../../services/websocket';
import { 
  Bell, 
  Search, 
  ChevronDown, 
  LogOut, 
  User, 
} from 'lucide-react';

export const Header = ({ pageTitle = 'OVERVIEW', breadcrumb = 'Central Operations • Bengaluru • All Zones' }) => {
  const { user, logout } = useAuth();
  const [wsConnected, setWsConnected] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [unreadAlerts, setUnreadAlerts] = useState([]);

  useEffect(() => {
    wsService.connect(
      () => setWsConnected(true),
      () => setWsConnected(false)
    );

    const checkInterval = setInterval(() => {
      setWsConnected(wsService.isConnected);
    }, 4000);

    const unsubAlerts = wsService.subscribe('/topic/alerts', (newAlert) => {
      setUnreadAlerts((prev) => [
        { id: Date.now(), severity: 'warning', text: newAlert.description || 'Corridor telemetry alert', time: 'Just now' },
        ...prev
      ]);
    });

    return () => {
      clearInterval(checkInterval);
      unsubAlerts();
    };
  }, []);

  const getRoleLabel = (role) => {
    switch (role) {
      case 'ROLE_ADMIN': return 'Administrator';
      case 'ROLE_CENTRAL_HUB': return 'Central Hub Operator';
      case 'ROLE_REQUESTING_HOSPITAL': return 'Request Hospital';
      case 'ROLE_SUPPLYING_HOSPITAL': return 'Supplying Facility';
      default: return 'Operator';
    }
  };

  return (
    <header style={{
      height: 'var(--header-height)',
      background: 'var(--color-surface)',
      borderBottom: '1px solid var(--color-border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 24px',
      position: 'sticky',
      top: 0,
      zIndex: 1000,
    }}>
      {/* Left: MEDAIR / PAGE TITLE & Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '15px', fontWeight: 800, color: 'var(--color-primary-navy)', letterSpacing: '0.04em' }}>
              MEDAIR
            </span>
            <span style={{ color: 'var(--color-border)' }}>/</span>
            <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--color-text)', textTransform: 'uppercase', letterSpacing: '0.02em' }}>
              {pageTitle}
            </span>
          </div>
          {breadcrumb && (
            <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '1px' }}>
              {breadcrumb}
            </div>
          )}
        </div>

        {/* Small Operational Status Indicator */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          padding: '3px 10px',
          borderRadius: '999px',
          background: wsConnected ? 'var(--color-success-bg)' : '#F2F4F7',
          border: `1px solid ${wsConnected ? '#C1E5C4' : 'var(--color-border)'}`,
          fontSize: '11px',
          fontWeight: 600,
          color: wsConnected ? 'var(--color-success)' : 'var(--color-text-secondary)',
          marginLeft: '8px',
        }}>
          <span style={{
            width: '6px',
            height: '6px',
            borderRadius: '50%',
            backgroundColor: wsConnected ? 'var(--color-success)' : 'var(--color-text-muted)',
            display: 'inline-block'
          }} />
          <span>{wsConnected ? 'System Online • Live' : 'Connecting to Gateway...'}</span>
        </div>
      </div>

      {/* Right Controls: Search, Notifications, User Profile Menu */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        {/* Search Input */}
        <div style={{ position: 'relative', width: '220px' }}>
          <Search size={14} style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--color-text-muted)' }} />
          <input
            type="text"
            placeholder="Search missions, hospitals..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '6px 12px 6px 30px',
              fontSize: '12px',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-sm)',
              background: '#F9FAFB',
              outline: 'none',
              fontFamily: 'inherit',
            }}
          />
        </div>

        {/* Notification Bell */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setAlertsOpen(!alertsOpen)}
            style={{
              background: 'transparent',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-sm)',
              width: '34px',
              height: '34px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              color: 'var(--color-primary-navy)',
              position: 'relative',
            }}
            title="Operational Notifications"
          >
            <Bell size={16} />
            {unreadAlerts.length > 0 && (
              <span style={{
                position: 'absolute',
                top: '-4px',
                right: '-4px',
                background: 'var(--color-error)',
                color: '#FFFFFF',
                borderRadius: '50%',
                fontSize: '10px',
                fontWeight: 700,
                width: '16px',
                height: '16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
                {unreadAlerts.length}
              </span>
            )}
          </button>

          {/* Notifications Flyout */}
          {alertsOpen && (
            <div style={{
              position: 'absolute',
              right: 0,
              top: '42px',
              width: '320px',
              background: '#FFFFFF',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              boxShadow: 'var(--shadow-dropdown)',
              padding: '12px',
              zIndex: 1100,
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', paddingBottom: '6px', borderBottom: '1px solid var(--color-border-subtle)' }}>
                <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-primary-navy)' }}>
                  Active Alerts ({unreadAlerts.length})
                </span>
                <button 
                  onClick={() => setUnreadAlerts([])}
                  style={{ background: 'transparent', border: 'none', fontSize: '11px', color: 'var(--color-primary-blue)', cursor: 'pointer', fontWeight: 500 }}
                >
                  Clear all
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
                {unreadAlerts.length === 0 ? (
                  <div style={{ padding: '16px 0', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: '12px' }}>
                    No alerts have been received from the backend.
                  </div>
                ) : (
                  unreadAlerts.map((a) => (
                    <div key={a.id} style={{
                      padding: '8px 10px',
                      borderRadius: '6px',
                      background: a.severity === 'critical' ? 'var(--color-error-bg)' : a.severity === 'warning' ? 'var(--color-warning-bg)' : '#F8FAFC',
                      border: `1px solid ${a.severity === 'critical' ? '#FACDCD' : a.severity === 'warning' ? '#F6DEB5' : 'var(--color-border)'}`,
                      fontSize: '12px',
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontWeight: 600, color: a.severity === 'critical' ? 'var(--color-error)' : a.severity === 'warning' ? 'var(--color-warning)' : 'var(--color-primary-navy)' }}>
                          {a.severity.toUpperCase()}
                        </span>
                        <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>{a.time}</span>
                      </div>
                      <div style={{ color: 'var(--color-text)', marginTop: '2px', lineHeight: 1.3 }}>{a.text}</div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        {/* User Profile & Demo Switcher Menu */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setUserMenuOpen(!userMenuOpen)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '6px 12px',
              borderRadius: 'var(--radius-sm)',
              background: '#F8FAFC',
              border: '1px solid var(--color-border)',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            <div style={{
              width: '24px',
              height: '24px',
              borderRadius: '50%',
              background: 'var(--color-primary-navy)',
              color: '#FFFFFF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '11px',
              fontWeight: 700,
            }}>
              {user?.fullName ? user.fullName.charAt(0) : 'U'}
            </div>
            <div style={{ textAlign: 'left', lineHeight: 1.2 }}>
              <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--color-text)' }}>
                {user?.fullName || 'Operator'}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                {getRoleLabel(user?.role)}
              </div>
            </div>
            <ChevronDown size={14} color="var(--color-text-secondary)" />
          </button>

          {/* User Menu Dropdown */}
          {userMenuOpen && (
            <div style={{
              position: 'absolute',
              right: 0,
              top: '44px',
              width: '260px',
              background: '#FFFFFF',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              boxShadow: 'var(--shadow-dropdown)',
              padding: '8px',
              zIndex: 1100,
            }}>
              {user?.hospitalName && (
                <div style={{ padding: '8px 12px', background: 'var(--color-pale-blue)', borderRadius: '6px', marginBottom: '8px' }}>
                  <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-primary-navy)' }}>
                    ASSIGNED FACILITY
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--color-text)', marginTop: '2px', fontWeight: 500 }}>
                    {user.hospitalName}
                  </div>
                </div>
              )}

              <div style={{ marginTop: '8px', paddingTop: '6px', borderTop: '1px solid var(--color-border-subtle)' }}>
                <button
                  onClick={logout}
                  className="btn btn-secondary"
                  style={{ width: '100%', justifyContent: 'flex-start', border: 'none', color: 'var(--color-error)', padding: '6px 8px', fontSize: '12px' }}
                >
                  <LogOut size={14} />
                  <span>Sign Out</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Header;
