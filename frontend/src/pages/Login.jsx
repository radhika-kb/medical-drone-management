import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { hospitalService } from '../services/api';
import { 
  Plane, 
  ShieldCheck, 
  Radio, 
  Building2, 
  ArrowRight, 
  AlertCircle,
  Lock,
  Mail,
  CheckCircle2
} from 'lucide-react';

export const Login = () => {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState('SIGN_IN');
  const [name, setName] = useState('');
  const [hospitalId, setHospitalId] = useState('');
  const [role, setRole] = useState('ROLE_REQUESTING_HOSPITAL');
  const [hospitals, setHospitals] = useState([]);

  useEffect(() => {
    if (mode !== 'REGISTER') return;
    hospitalService.getAll(true).then(setHospitals).catch(() => setHospitals([]));
  }, [mode]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const res = mode === 'REGISTER'
      ? await register({ name, email, password, role, hospitalId: Number(hospitalId) })
      : await login(email, password);
    setLoading(false);

    if (res.success) {
      navigate('/dashboard');
    } else {
      setError(res.error || (mode === 'REGISTER' ? 'Account registration failed.' : 'Authentication failed. Please verify institutional credentials.'));
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      background: 'var(--color-background)',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      padding: '24px 16px',
    }}>
      <div style={{
        maxWidth: '460px',
        width: '100%',
        background: '#FFFFFF',
        borderRadius: '12px',
        border: '1px solid var(--color-border)',
        boxShadow: 'var(--shadow-md)',
        overflow: 'hidden',
      }}>
        {/* Brand Header Banner */}
        <div style={{
          background: 'var(--color-primary-navy)',
          padding: '28px 24px',
          color: '#FFFFFF',
          textAlign: 'center',
        }}>
          <div style={{
            width: '44px',
            height: '44px',
            borderRadius: '10px',
            background: 'rgba(255, 255, 255, 0.15)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 12px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          }}>
            <Plane size={24} color="#FFFFFF" />
          </div>
            <h1 style={{ fontSize: '20px', fontWeight: 800, margin: 0, letterSpacing: '0.04em' }}>
            MEDAIR
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '12px', opacity: 0.85, fontWeight: 500, letterSpacing: '0.02em' }}>
            Autonomous Medical Drone Logistics Network • Bengaluru Corridor
          </p>
        </div>

        {/* Content & Inputs */}
        <div style={{ padding: '24px' }}>
          {error && (
            <div style={{
              background: 'rgba(198, 40, 40, 0.08)',
              border: '1px solid rgba(198, 40, 40, 0.25)',
              color: 'var(--color-error)',
              borderRadius: '6px',
              padding: '10px 12px',
              fontSize: '13px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              marginBottom: '18px',
            }}>
              <AlertCircle size={16} style={{ flexShrink: 0 }} />
              <span>{error}</span>
            </div>
          )}

          <h2 style={{ fontSize: 18, margin: '0 0 16px', color: 'var(--color-primary-navy)' }}>
            {mode === 'REGISTER' ? 'Create a medical center account' : 'Sign in'}
          </h2>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {mode === 'REGISTER' && <>
              <div className="form-group">
                <label className="form-label" htmlFor="name">Your name</label>
                <input id="name" required className="form-control" value={name} onChange={e => setName(e.target.value)} autoComplete="name" />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="hospital">Hospital, blood bank, or tissue bank</label>
                <select id="hospital" required className="form-control" value={hospitalId} onChange={e => setHospitalId(e.target.value)}>
                  <option value="">Select your approved Bengaluru facility</option>
                  {hospitals.map(center => <option key={center.id} value={center.id}>{center.name} · {center.type} · {center.zoneName || 'Bengaluru'}</option>)}
                </select>
                {hospitals.length === 0 && <small>No approved facilities are available. Contact an administrator to register and verify your facility.</small>}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="role">Account purpose</label>
                <select id="role" className="form-control" value={role} onChange={e => setRole(e.target.value)}>
                  <option value="ROLE_REQUESTING_HOSPITAL">Request medical supplies</option>
                  <option value="ROLE_SUPPLYING_HOSPITAL">Manage and supply inventory</option>
                </select>
                <small style={{ display: 'block', marginTop: 6, color: 'var(--color-text-secondary)' }}>
                  Any approved facility can request supplies. Supplier accounts can respond to requests when matching inventory is recorded.
                </small>
              </div>
            </>}
            <div className="form-group">
              <label className="form-label" htmlFor="email">
                {mode === 'REGISTER' ? 'Work email' : 'Institutional Email ID'}
              </label>
              <div style={{ position: 'relative' }}>
                <Mail size={16} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--color-text-secondary)' }} />
                <input
                  id="email"
                  type="email"
                  required
                  className="form-control"
                  style={{ paddingLeft: '34px' }}
                  placeholder="name@hospital.org"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">
                {mode === 'REGISTER' ? 'Password (at least 8 characters)' : 'Security Password'}
              </label>
              <div style={{ position: 'relative' }}>
                <Lock size={16} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--color-text-secondary)' }} />
                <input
                  id="password"
                  type="password"
                  minLength={mode === 'REGISTER' ? 8 : undefined}
                  required
                  className="form-control"
                  style={{ paddingLeft: '34px' }}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn btn-primary"
              style={{ width: '100%', marginTop: '6px', padding: '10px', fontSize: '13px' }}
            >
              <span>{loading ? 'Please wait…' : mode === 'REGISTER' ? 'Create account' : 'Sign In to Operations Portal'}</span>
              <ArrowRight size={15} />
            </button>
          </form>

          <div style={{ marginTop: 18, textAlign: 'center', fontSize: 13 }}>
            {mode === 'REGISTER' ? 'Already registered? ' : 'New to MEDAIR? '}
            <button type="button" className="btn-link" onClick={() => { setError(''); setMode(mode === 'REGISTER' ? 'SIGN_IN' : 'REGISTER'); }}>
              {mode === 'REGISTER' ? 'Sign in' : 'Create an account'}
            </button>
          </div>
        </div>

        {/* Card Footer */}
        <div style={{
          padding: '10px 16px',
          background: 'var(--color-background)',
          borderTop: '1px solid var(--color-border)',
          fontSize: '11px',
          color: 'var(--color-text-secondary)',
          textAlign: 'center',
        }}>
          Bengaluru medical logistics · real facility directory · mission management
        </div>
      </div>
    </div>
  );
};

export default Login;
