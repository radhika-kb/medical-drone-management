import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { 
  Plane, 
  LayoutDashboard, 
  Package, 
  Send, 
  Navigation, 
  Building2, 
  Cpu, 
  Sparkles, 
  BarChart3, 
  AlertTriangle, 
  Settings, 
  FileText, 
  PlusCircle, 
  Radio,
  Boxes,
  Compass
} from 'lucide-react';

export const Sidebar = () => {
  const { user } = useAuth();
  const role = user?.role || 'ROLE_ADMIN';

  // Navigation Items by Role (Section 4)
  const getNavItems = () => {
    if (role === 'ROLE_REQUESTING_HOSPITAL') {
      return [
        { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
        { to: '/requests/new', label: 'Create Request', icon: PlusCircle, isAction: true },
        { to: '/requests', label: 'My Requests', icon: Package },
        { to: '/missions', label: 'Active Missions', icon: Send },
        { to: '/tracking', label: 'Live Tracking', icon: Navigation },
        { to: '/alerts', label: 'Notifications', icon: AlertTriangle },
      ];
    }

    if (role === 'ROLE_SUPPLYING_HOSPITAL') {
      return [
        { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
        { to: '/dashboard#incoming-requests', label: 'Incoming Requests', icon: Boxes },
        { to: '/dashboard#facility-inventory', label: 'Stock Inventory', icon: Package },
        { to: '/missions', label: 'Active Missions', icon: Send },
        { to: '/tracking', label: 'Live Tracking', icon: Navigation },
        { to: '/alerts', label: 'Notifications', icon: AlertTriangle },
      ];
    }

    if (role === 'ROLE_CENTRAL_HUB') {
      return [
        { to: '/dashboard', label: 'Operations Overview', icon: LayoutDashboard },
        { to: '/requests', label: 'Requests', icon: Package },
        { to: '/missions', label: 'Missions', icon: Send },
        { to: '/tracking', label: 'Live Map', icon: Navigation },
        { to: '/hub', label: 'Central Hub', icon: Radio },
        { to: '/airborne-units', label: 'Resource Allocation', icon: Cpu },
        { to: '/ai-decisions', label: 'AI Decisions', icon: Sparkles },
        { to: '/alerts', label: 'Alerts', icon: AlertTriangle },
      ];
    }

    // Default / Admin
    return [
      { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
      { to: '/requests/new', label: 'Create Request', icon: PlusCircle, isAction: true },
      { to: '/requests', label: 'Requests', icon: Package },
      { to: '/missions', label: 'Missions', icon: Send },
      { to: '/tracking', label: 'Live Tracking', icon: Navigation },
      { to: '/hospitals', label: 'Hospitals', icon: Building2 },
      { to: '/airborne-units', label: 'Airborne Units', icon: Cpu },
      { to: '/hub', label: 'Central Hub', icon: Radio },
      { to: '/ai-decisions', label: 'AI Decisions', icon: Sparkles },
      { to: '/analytics', label: 'Analytics', icon: BarChart3 },
      { to: '/alerts', label: 'Alerts', icon: AlertTriangle },
    ];
  };

  const navItems = getNavItems();

  return (
    <aside style={{
      width: 'var(--sidebar-width)',
      background: 'var(--color-surface)',
      borderRight: '1px solid var(--color-border)',
      height: '100vh',
      position: 'sticky',
      top: 0,
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'space-between',
      flexShrink: 0,
      padding: '16px 12px',
      overflowY: 'auto',
    }}>
      <div>
        {/* Brand Header */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '8px 12px 20px',
          borderBottom: '1px solid var(--color-border-subtle)',
          marginBottom: '16px',
        }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--color-primary-navy)',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 4px rgba(18, 59, 93, 0.2)',
          }}>
            <Plane size={18} />
          </div>
          <div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: 'var(--color-primary-navy)', letterSpacing: '0.04em' }}>
              MEDAIR
            </div>
            <div style={{ fontSize: '10px', fontWeight: 600, color: 'var(--color-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
              Flight Logistics
            </div>
          </div>
        </div>

        {/* Navigation Links */}
        <nav style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                style={({ isActive }) => ({
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  padding: '9px 12px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '13px',
                  fontWeight: isActive ? 600 : 500,
                  textDecoration: 'none',
                  color: isActive ? 'var(--color-primary-navy)' : 'var(--color-text-secondary)',
                  background: isActive ? 'var(--color-pale-blue)' : item.isAction ? 'rgba(47, 111, 237, 0.08)' : 'transparent',
                  transition: 'background-color var(--transition-fast), color var(--transition-fast)',
                })}
              >
                <Icon size={16} style={{ flexShrink: 0 }} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Bottom Area: Settings & Role Context */}
      <div style={{ borderTop: '1px solid var(--color-border-subtle)', paddingTop: '12px', display: 'flex', flexDirection: 'column', gap: '3px' }}>
        <NavLink
          to="/settings"
          style={({ isActive }) => ({
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            padding: '8px 12px',
            borderRadius: 'var(--radius-sm)',
            fontSize: '13px',
            fontWeight: 500,
            textDecoration: 'none',
            color: isActive ? 'var(--color-primary-navy)' : 'var(--color-text-secondary)',
            background: isActive ? 'var(--color-pale-blue)' : 'transparent',
          })}
        >
          <Settings size={16} />
          <span>Settings</span>
        </NavLink>

        {/* User Card */}
        <div style={{
          marginTop: '8px',
          padding: '10px 12px',
          borderRadius: 'var(--radius-sm)',
          background: '#F8FAFC',
          border: '1px solid var(--color-border-subtle)',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}>
          <span style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: 'var(--color-success)',
            display: 'inline-block'
          }} />
          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', lineHeight: 1.2 }}>
            <div style={{ fontWeight: 600, color: 'var(--color-primary-navy)' }}>
              {user?.fullName || 'Operator'}
            </div>
            <div>Bengaluru Corridor</div>
          </div>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
