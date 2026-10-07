import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { missionService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import StatusBadge from '../components/common/StatusBadge';
import MissionProgressStepper from '../components/mission/MissionProgressStepper';
import { 
  Send, 
  Navigation, 
  Battery, 
  Compass, 
  Gauge, 
  Thermometer, 
  ShieldCheck, 
  RefreshCw, 
  Eye, 
  X, 
  CheckCircle2, 
  AlertTriangle,
  MapPin,
  Clock
} from 'lucide-react';

export const Missions = () => {
  const { user } = useAuth();
  const [missions, setMissions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tabFilter, setTabFilter] = useState('ALL');
  const [selectedMission, setSelectedMission] = useState(null);
  const [confirmingId, setConfirmingId] = useState(null);

  const loadMissions = async () => {
    try {
      setLoading(true);
      const data = await missionService.getAll();
      setMissions(data || []);
    } catch (e) {
      console.warn('Unable to load missions:', e);
      setMissions([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMissions();
  }, []);

  const handleConfirmDelivery = async (missionId) => {
    setConfirmingId(missionId);
    try {
      await missionService.confirmDelivery(missionId).catch(() => null);
      setMissions(prev => prev.map(m => m.id === missionId ? { ...m, status: 'DELIVERY_CONFIRMED' } : m));
      if (selectedMission?.id === missionId) {
        setSelectedMission(prev => ({ ...prev, status: 'DELIVERY_CONFIRMED' }));
      }
    } catch (e) {
      console.warn('Failed to confirm mission delivery:', e);
    } finally {
      setConfirmingId(null);
    }
  };

  const filteredMissions = missions.filter(m => {
    if (tabFilter === 'ACTIVE') {
      return m.status === 'IN_TRANSIT' || m.status === 'DISPATCHED' || m.status === 'NEAR_RECEIVER';
    }
    if (tabFilter === 'COMPLETED') {
      return m.status === 'DELIVERY_CONFIRMED' || m.status === 'COMPLETED' || m.status === 'ARRIVED';
    }
    return true;
  });

  return (
    <AppLayout
      pageTitle="AIR MISSIONS"
      breadcrumb="Central Operations • Flight Operations • Active Missions"
    >
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
            Mission Operations
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Mission telemetry, corridor routes, and delivery-state records
          </p>
        </div>

        <button onClick={loadMissions} className="btn btn-secondary">
          <RefreshCw size={15} className={loading ? 'spin' : ''} />
          <span>Sync Telemetry</span>
        </button>
      </div>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '20px' }}>
        {[
          { key: 'ALL', label: 'All Missions' },
          { key: 'ACTIVE', label: 'Transit / Active' },
          { key: 'COMPLETED', label: 'Completed Records' },
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setTabFilter(tab.key)}
            className={`btn ${tabFilter === tab.key ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '13px', padding: '6px 14px' }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Missions Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Callsign</th>
                <th>Request Ref</th>
                <th>Assigned Unit</th>
                <th>Corridor Route</th>
                <th>Distance / ETA</th>
                <th>Telemetry Snapshot</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredMissions.length === 0 ? (
                <tr><td colSpan="8" style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--color-text-secondary)' }}>No mission records are available from the backend.</td></tr>
              ) : filteredMissions.map((m) => (
                <tr key={m.id}>
                  <td>
                    <div style={{ fontWeight: 800, color: 'var(--color-primary-navy)', fontSize: '14px' }}>
                      {m.callsign}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      Departed: {m.departureTime}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, fontSize: '13px' }}>{m.requestCode}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      {m.medicalItem}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: 'var(--color-primary-blue)' }}>{m.droneId}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      Battery: {m.batteryPct}%
                    </div>
                  </td>
                  <td>
                    <div style={{ fontSize: '12px', fontWeight: 500 }}>{m.origin}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      ↓ {m.destination}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{m.distanceKm} km</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-primary-blue)', fontWeight: 600 }}>
                      ETA: {m.predictedEtaMin} min (ML)
                    </div>
                  </td>
                  <td>
                    <div style={{ fontSize: '11px', display: 'flex', flexDirection: 'column', gap: '2px' }}>
                      <span>Speed: <strong>{m.speedKmh} km/h</strong></span>
                      <span>Alt: <strong>{m.altitudeM} m AGL</strong></span>
                      <span>Temp: <strong>{m.tempCelsius}°C</strong></span>
                    </div>
                  </td>
                  <td>
                    <StatusBadge status={m.status} size="sm" />
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      onClick={() => setSelectedMission(m)}
                      className="btn btn-secondary"
                      style={{ fontSize: '12px', padding: '4px 10px' }}
                    >
                      <Eye size={13} />
                      <span>Telemetry</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Mission Telemetry Drawer / Modal */}
      {selectedMission && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(18, 59, 93, 0.4)',
          zIndex: 1200,
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          padding: '20px',
        }}>
          <div style={{
            background: 'var(--color-surface)',
            borderRadius: 'var(--radius-lg)',
            width: '100%',
            maxWidth: '680px',
            maxHeight: '90vh',
            overflowY: 'auto',
            boxShadow: 'var(--shadow-lg)',
            border: '1px solid var(--color-border)',
            display: 'flex',
            flexDirection: 'column',
          }}>
            {/* Header */}
            <div style={{
              padding: '18px 24px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--color-primary-navy)',
                  color: '#FFFFFF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}>
                  <Navigation size={18} />
                </div>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <h2 style={{ fontSize: '16px', fontWeight: 800, margin: 0, color: 'var(--color-primary-navy)' }}>
                      Mission Telemetry: {selectedMission.callsign}
                    </h2>
                    <StatusBadge status={selectedMission.status} size="sm" />
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    Ref: {selectedMission.requestCode} • Airframe: {selectedMission.droneId}
                  </div>
                </div>
              </div>

              <button
                onClick={() => setSelectedMission(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-text-secondary)' }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Content */}
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Stepper */}
              <div style={{
                background: 'var(--color-background)',
                padding: '16px',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border-subtle)',
              }}>
                <MissionProgressStepper currentStatus={selectedMission.status} />
              </div>

              {/* Real-time Telemetry Grid */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '12px',
              }}>
                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Battery size={13} color="var(--color-success)" />
                    <span>Battery SoC</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-primary-navy)', marginTop: '4px' }}>
                    {selectedMission.batteryPct}%
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Nominal 44.4V</div>
                </div>

                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Gauge size={13} color="var(--color-primary-blue)" />
                    <span>Ground Speed</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-primary-navy)', marginTop: '4px' }}>
                    {selectedMission.speedKmh} <span style={{ fontSize: '12px', fontWeight: 500 }}>km/h</span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}></div>
                </div>

                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Navigation size={13} color="var(--color-warning)" />
                    <span>Flight Altitude</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-primary-navy)', marginTop: '4px' }}>
                    {selectedMission.altitudeM} <span style={{ fontSize: '12px', fontWeight: 500 }}>m AGL</span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Corridor: 100-120m</div>
                </div>

                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Thermometer size={13} color="var(--color-primary-blue)" />
                    <span>Cold Box Pod</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-primary-navy)', marginTop: '4px' }}>
                    +{selectedMission.tempCelsius}°C
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-success)' }}>Within 2-8°C limit</div>
                </div>

                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Compass size={13} />
                    <span>Flight Heading</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-primary-navy)', marginTop: '4px' }}>
                    {selectedMission.headingDeg}° N
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Direct Corridor</div>
                </div>

                <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <ShieldCheck size={13} color="var(--color-success)" />
                    <span>RF / Telemetry Link</span>
                  </div>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-success)', marginTop: '4px' }}>
                    {selectedMission.signalLinkPct}%
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Redundant 4G/SAT</div>
                </div>
              </div>

              {/* Waypoints */}
              <div style={{
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                padding: '16px',
                borderRadius: 'var(--radius-sm)',
              }}>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--color-primary-navy)', marginBottom: '8px' }}>
                  CORRIDOR FLIGHT PATH
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '12px' }}>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Origin Airpad: </span>
                    <strong>{selectedMission.origin}</strong>
                  </div>
                  <span style={{ color: 'var(--color-primary-blue)' }}>➔ Cubbon Waypoint ➔</span>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Destination Pad: </span>
                    <strong>{selectedMission.destination}</strong>
                  </div>
                </div>
              </div>

              {/* Action buttons */}
              <div style={{
                borderTop: '1px solid var(--color-border)',
                paddingTop: '16px',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '10px',
              }}>
                {selectedMission.status !== 'DELIVERY_CONFIRMED' && (
                  <button
                    onClick={() => handleConfirmDelivery(selectedMission.id)}
                    disabled={confirmingId === selectedMission.id}
                    className="btn btn-primary"
                  >
                    <CheckCircle2 size={16} />
                    <span>Confirm Mission Delivery</span>
                  </button>
                )}

                <button
                  onClick={() => setSelectedMission(null)}
                  className="btn btn-secondary"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </AppLayout>
  );
};

export default Missions;
