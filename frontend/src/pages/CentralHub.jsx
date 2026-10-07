import React, { useState, useEffect } from 'react';
import { missionService, requestService, droneService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import StatusBadge from '../components/common/StatusBadge';
import CurrentWeatherCard from '../components/common/CurrentWeatherCard';
import { 
  Radio, 
  Send, 
  ShieldCheck, 
  Wind, 
  CloudRain, 
  Eye, 
  Thermometer, 
  CheckCircle2, 
  AlertTriangle, 
  Plane, 
  RefreshCw,
  Clock,
  ArrowRight
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const CentralHub = () => {
  const [dispatchQueue, setDispatchQueue] = useState([]);
  const [availableDrones, setAvailableDrones] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedDroneByReq, setSelectedDroneByReq] = useState({});
  const [dispatchingId, setDispatchingId] = useState(null);
  const [dispatchNotice, setDispatchNotice] = useState(null);

  const loadData = async () => {
    try {
      setLoading(true);
      const [reqData, droneData] = await Promise.all([
        requestService.getAll().catch(() => null),
        droneService.getAll().catch(() => null),
      ]);

      setDispatchQueue((reqData || []).filter(r => r.status === 'READY_FOR_DISPATCH' || r.status === 'SUPPLIER_ACCEPTED' || r.status === 'RESOURCE_ASSIGNED'));
      setAvailableDrones((droneData || []).filter(d => d.status === 'AVAILABLE'));
    } catch (e) {
      setDispatchQueue([]);
      setAvailableDrones([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAuthorizeDispatch = async (reqId) => {
    setDispatchingId(reqId);
    setDispatchNotice(null);
    try {
      const result = await missionService.prepareReview(reqId);
      const mission = result.mission || result;
      setDispatchNotice({ type: 'success', text: `AI recommendation ${mission.missionCode} is ready for Central Hub review. No drone has launched.` });
      setDispatchQueue(prev => prev.filter(r => r.id !== reqId));
    } catch (e) {
      setDispatchNotice({ type: 'error', text: 'Dispatch authorization failed.' });
    } finally {
      setDispatchingId(null);
    }
  };

  return (
    <AppLayout
      pageTitle="CENTRAL HUB OPERATIONS"
      breadcrumb="Central Operations • Flight Authorization • Bengaluru Operations"
    >
      <CurrentWeatherCard />
      {/* Top Header */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '20px',
        flexWrap: 'wrap',
        gap: '12px',
      }}>
        <div>
          <h1 style={{ fontSize: '20px', fontWeight: 800, color: 'var(--color-primary-navy)', margin: 0 }}>
            Cubbon Park Central Logistics Command
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Primary coordinator for multi-hospital autonomous medical drone dispatches
          </p>
        </div>

        <button onClick={loadData} className="btn btn-secondary">
          <RefreshCw size={15} className={loading ? 'spin' : ''} />
          <span>Sync Hub Status</span>
        </button>
      </div>

      {/* Weather & Safety Operational Banner */}
      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-md)',
        padding: '16px 20px',
        marginBottom: '20px',
        display: 'grid',
        gridTemplateColumns: 'auto 1fr auto',
        gap: '20px',
        alignItems: 'center',
      }}>
        <div style={{
          width: '42px',
          height: '42px',
          borderRadius: 'var(--radius-sm)',
          background: 'rgba(22, 128, 60, 0.1)',
          color: 'var(--color-success)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          <ShieldCheck size={24} />
        </div>

        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontWeight: 800, fontSize: '14px', color: 'var(--color-primary-navy)' }}>
              Airspace assessment status
            </span>
            <span className="status-badge badge-success" style={{ fontSize: '11px' }}>
              Safety agent output is shown per mission
            </span>
          </div>
          <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '4px' }}>
            No current weather observation is loaded in this panel.
          </div>
        </div>

        <Link to="/tracking" className="btn btn-secondary" style={{ fontSize: '12px' }}>
          <span>View Air Radar</span>
          <ArrowRight size={13} />
        </Link>
      </div>

      {/* Notice Banner */}
      {dispatchNotice && (
        <div style={{
          padding: '12px 16px',
          borderRadius: 'var(--radius-sm)',
          marginBottom: '20px',
          fontSize: '13px',
          background: dispatchNotice.type === 'success' ? 'rgba(22, 128, 60, 0.1)' : 'rgba(198, 40, 40, 0.1)',
          color: dispatchNotice.type === 'success' ? 'var(--color-success)' : 'var(--color-error)',
          border: `1px solid ${dispatchNotice.type === 'success' ? 'rgba(22, 128, 60, 0.3)' : 'rgba(198, 40, 40, 0.3)'}`,
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}>
          <CheckCircle2 size={16} />
          <span>{dispatchNotice.text}</span>
        </div>
      )}

      {/* Immediate Dispatch Authorization Bay */}
      <div className="card" style={{ padding: '20px', marginBottom: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div>
            <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
              Central Hub Mission Review Queue
            </h2>
            <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
              Backend request records awaiting AI preparation and operator review
            </p>
          </div>
          <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--color-primary-blue)' }}>
            {dispatchQueue.length} Ready for Authorization
          </span>
        </div>

        {dispatchQueue.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {dispatchQueue.map((req) => (
              <div
                key={req.id}
                style={{
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: '16px',
                  background: 'var(--color-surface)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '16px',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                    <span style={{ fontWeight: 800, color: 'var(--color-primary-navy)', fontSize: '14px' }}>
                      {req.requestCode}
                    </span>
                    <StatusBadge status={req.priority} size="sm" />
                    <StatusBadge status={req.status} size="sm" />
                  </div>

                  <div style={{ fontWeight: 600, fontSize: '13px' }}>
                    {req.medicalItemName} (Qty: {req.quantity})
                  </div>

                  <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    Origin: <strong>{req.supplyingHospitalName}</strong> ➔ Destination: <strong>{req.hospitalName}</strong>
                  </div>

                  <div style={{ fontSize: '11px', color: 'var(--color-primary-blue)', marginTop: '4px' }}>
                    Corridor Distance: {req.flightDistanceKm} km • Baseline Flight Time: {req.estimatedMinutes} min
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ minWidth: '180px' }}>
                    <label style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', display: 'block', marginBottom: '4px' }}>
                      Assign Airframe
                    </label>
                    <select
                      value={selectedDroneByReq[req.id] || req.recommendedDroneId || availableDrones[0]?.registrationNumber}
                      onChange={(e) => setSelectedDroneByReq({ ...selectedDroneByReq, [req.id]: e.target.value })}
                      className="form-control"
                      style={{ fontSize: '12px', padding: '6px 10px' }}
                    >
                      {availableDrones.map(d => (
                        <option key={d.id} value={d.registrationNumber}>
                          {d.registrationNumber} ({d.model} • {d.batteryLevel}%)
                        </option>
                      ))}
                    </select>
                  </div>

                  <button
                    onClick={() => handleAuthorizeDispatch(req.id)}
                    disabled={dispatchingId === req.id}
                    className="btn btn-primary"
                    style={{ marginTop: '16px' }}
                  >
                    <Send size={15} />
                    <span>{dispatchingId === req.id ? 'Authorizing...' : 'Authorize & Dispatch'}</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '36px', color: 'var(--color-text-secondary)' }}>
            <CheckCircle2 size={32} color="var(--color-success)" style={{ margin: '0 auto 8px' }} />
            <div style={{ fontWeight: 600 }}>Eligible requests are available for Central Hub review.</div>
            <div style={{ fontSize: '12px', marginTop: '4px' }}>Corridor queue is empty.</div>
          </div>
        )}
      </div>

      {/* 4-Zone Corridor Monitoring Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '16px',
      }}>
        <div className="card" style={{ padding: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
            North Corridor
          </div>
          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
            Hebbal / Yelahanka / Bellary Rd
          </div>
          <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="status-badge badge-success" style={{ fontSize: '11px' }}>Operational</span>
            <span style={{ fontSize: '12px', fontWeight: 600 }}>{availableDrones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('NORTH')).length} Units</span>
          </div>
        </div>

        <div className="card" style={{ padding: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
            East Corridor
          </div>
          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
            Whitefield / KR Puram / ITPL
          </div>
          <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="status-badge badge-success" style={{ fontSize: '11px' }}>Operational</span>
            <span style={{ fontSize: '12px', fontWeight: 600 }}>{availableDrones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('EAST')).length} Units</span>
          </div>
        </div>

        <div className="card" style={{ padding: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
            South Corridor
          </div>
          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
            Electronic City / Bommasandra
          </div>
          <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="status-badge badge-success" style={{ fontSize: '11px' }}>Operational</span>
            <span style={{ fontSize: '12px', fontWeight: 600 }}>{availableDrones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('SOUTH')).length} Units</span>
          </div>
        </div>

        <div className="card" style={{ padding: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
            West Corridor
          </div>
          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
            Rajajinagar / Mysore Road
          </div>
          <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="status-badge badge-success" style={{ fontSize: '11px' }}>Operational</span>
            <span style={{ fontSize: '12px', fontWeight: 600 }}>{availableDrones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('WEST')).length} Units</span>
          </div>
        </div>
      </div>
    </AppLayout>
  );
};

export default CentralHub;
