import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { 
  Radar, 
  Send, 
  Activity, 
  ShieldAlert, 
  Package, 
  FilePlus, 
  CreditCard, 
  Boxes, 
  CheckSquare, 
  Plane, 
  Building, 
  History,
  AlertCircle
} from 'lucide-react';

export const Sidebar = () => {
  const { user } = useAuth();
  const role = user?.role || 'ROLE_CENTRAL_HUB';

  return (
    <aside className="sidebar" style={{
      width: '240px',
      background: '#FFFFFF',
      borderRight: '1px solid var(--color-border)',
      height: 'calc(100vh - 61px)',
      position: 'sticky',
      top: '61px',
      display: 'flex',
      flexDirection: 'column',
      padding: '1.25rem 0.75rem',
      gap: '1.5rem',
      flexShrink: 0,
      overflowY: 'auto',
    }}>
      {/* Role Navigation section */}
      <div>
        <div style={{
          fontSize: '0.6875rem',
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          color: 'var(--color-text-secondary)',
          padding: '0 0.75rem 0.5rem',
        }}>
          Operations Menu
        </div>

        <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          {/* Central Hub Nav */}
          {(role === 'ROLE_CENTRAL_HUB' || role === 'ROLE_ADMIN') && (
            <>
              <NavLink
                to="/hub"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Radar size={18} />
                <span>Command Radar</span>
              </NavLink>
              <NavLink
                to="/hub/dispatch"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Send size={18} />
                <span>Dispatch Queue</span>
              </NavLink>
              <NavLink
                to="/hub/flights"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Activity size={18} />
                <span>Active Flights</span>
              </NavLink>
              <NavLink
                to="/hub/anomalies"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <ShieldAlert size={18} />
                <span>Anomaly Feed</span>
              </NavLink>
            </>
          )}

          {/* Requesting Hospital Nav */}
          {(role === 'ROLE_REQUESTING_HOSPITAL' || role === 'ROLE_ADMIN') && (
            <>
              <NavLink
                to="/requester"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Package size={18} />
                <span>Active Requests</span>
              </NavLink>
              <NavLink
                to="/requester/new"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <FilePlus size={18} />
                <span>Create Request</span>
              </NavLink>
              <NavLink
                to="/requester/billing"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <CreditCard size={18} />
                <span>Logistics Invoices</span>
              </NavLink>
            </>
          )}

          {/* Supplying Hospital Nav */}
          {(role === 'ROLE_SUPPLYING_HOSPITAL' || role === 'ROLE_ADMIN') && (
            <>
              <NavLink
                to="/supplier"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <CheckSquare size={18} />
                <span>Supply Matching</span>
              </NavLink>
              <NavLink
                to="/supplier/inventory"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Boxes size={18} />
                <span>Stock Inventory</span>
              </NavLink>
            </>
          )}

          {/* Administrator Nav */}
          {role === 'ROLE_ADMIN' && (
            <>
              <div style={{
                fontSize: '0.6875rem',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                color: 'var(--color-text-secondary)',
                padding: '1rem 0.75rem 0.5rem',
              }}>
                Administration
              </div>
              <NavLink
                to="/admin"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Building size={18} />
                <span>Hospital Approvals</span>
              </NavLink>
              <NavLink
                to="/admin/fleet"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Plane size={18} />
                <span>Drone Fleet</span>
              </NavLink>
              <NavLink
                to="/admin/catalog"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                style={navLinkStyle}
              >
                <Package size={18} />
                <span>Medical Catalog</span>
              </NavLink>
            </>
          )}
        </nav>
      </div>

      {/* Logistics Zone Quick Info */}
      <div style={{
        marginTop: 'auto',
        padding: '0.875rem',
        borderRadius: '8px',
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        fontSize: '0.75rem',
      }}>
        <div style={{ fontWeight: 600, color: 'var(--color-text)', marginBottom: '0.375rem', display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
          <AlertCircle size={14} color="var(--color-nhs-blue)" />
          <span>Airspace Corridor Status</span>
        </div>
        <div style={{ color: 'var(--color-text-secondary)', lineHeight: 1.4 }}>
          Cubbon Central Hub active. 5 No-Fly buffers enforced. Max flight ceiling 120m AGL.
        </div>
      </div>
    </aside>
  );
};

const navLinkStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: '0.75rem',
  padding: '0.625rem 0.75rem',
  borderRadius: '6px',
  color: 'var(--color-text-secondary)',
  textDecoration: 'none',
  fontWeight: 500,
  fontSize: '0.875rem',
  transition: 'background-color 0.15s, color 0.15s',
};

export default Sidebar;
