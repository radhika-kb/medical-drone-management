import CurrentWeatherCard from '../components/common/CurrentWeatherCard';
import React, { useState, useEffect } from 'react';
import AppLayout from '../components/layout/AppLayout';
import BengaluruRadarMap from '../components/map/BengaluruRadarMap';
import StatusBadge from '../components/common/StatusBadge';
import { missionService } from '../services/api';
import { 
  Radio, 
  RefreshCw,
  Layers,
  MapPin,
  ExternalLink
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const LiveTracking = () => {
  const [selectedZone, setSelectedZone] = useState('ALL');
  const [missions, setMissions] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    try {
      setLoading(true);
      const [missionsData] = await Promise.all([
        missionService.getAll().catch(() => null),
      ]);

      setMissions(missionsData || []);
    } catch (e) {
      setMissions([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <AppLayout
      pageTitle="LIVE TRACKING RADAR"
      breadcrumb="Central Operations • Corridor Monitoring • Bengaluru Radar"
    >
      <CurrentWeatherCard />
      {/* Top Banner */}
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
            Bengaluru Mission Tracking
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Mission telemetry and route status
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{
            fontSize: '12px',
            color: 'var(--color-success)',
            background: 'rgba(22, 128, 60, 0.1)',
            padding: '4px 10px',
            borderRadius: '12px',
            border: '1px solid rgba(22, 128, 60, 0.2)',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            fontWeight: 600,
          }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--color-success)' }} />
            Mission Feed
          </span>

          <button onClick={loadData} className="btn btn-secondary">
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Poll Updates</span>
          </button>
        </div>
      </div>

      {/* Corridor Radar Statistics Strip */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '16px',
        marginBottom: '20px',
      }}>
        <div className="kpi-card">
          <div className="kpi-label">Active Mission States</div>
          <div className="kpi-value" style={{ color: 'var(--color-primary-navy)' }}>
            {missions.filter(m => ['IN_TRANSIT', 'TRAVELLING_TO_PICKUP', 'DISPATCHED'].includes(m.status)).length} <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>Active</span>
          </div>
          <div className="kpi-subtext">Status from mission records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Route Checks</div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            {missions.length ? 'Calculated' : '—'}
          </div>
          <div className="kpi-subtext">Current DigitalSky airspace clearance is unavailable</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Average Ground Speed</div>
          <div className="kpi-value">
            — <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>km/h</span>
          </div>
          <div className="kpi-subtext">Telemetry from mission records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Airspace Conflict Index</div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            — <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>N/A</span>
          </div>
          <div className="kpi-subtext">Airborne unit records</div>
        </div>
      </div>

      {/* Main Map & Live Drone Fleet Split */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
        gap: '24px',
      }}>
        {/* Full Radar Map */}
        <div className="card" style={{ padding: '16px', display: 'flex', flexDirection: 'column' }}>
          <div style={{ flex: 1, minHeight: '520px' }}>
            <BengaluruRadarMap
              height="520px"
              activeMissions={missions}
              selectedZone={selectedZone}
              onSelectZone={setSelectedZone}
            />
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '12px',
            fontSize: '11px',
            color: 'var(--color-text-secondary)',
            borderTop: '1px solid var(--color-border-subtle)',
            paddingTop: '8px',
          }}>
            <div>
              Hospital markers use admin-confirmed locations; mission positions are shown from recorded telemetry.
            </div>
            <div>
              Attribution: © OpenStreetMap contributors
            </div>
          </div>
        </div>

        {/* Mission Unit Records */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column' }}>
          <div style={{ marginBottom: '16px' }}>
            <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
              Mission Telemetry
            </h2>
            <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
              Telemetry recorded for each mission
            </p>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', overflowY: 'auto' }}>
            {missions.length === 0 ? (
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem' }}>No backend mission records are available.</div>
            ) : missions.map((m) => (
              <div
                key={m.id}
                style={{
                  background: 'var(--color-surface)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: '14px',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ fontWeight: 800, fontSize: '14px', color: 'var(--color-primary-navy)' }}>
                    {m.missionCode || `Mission ${m.id}`} <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>({m.droneCode || m.droneModel || 'Unit record'})</span>
                  </div>
                  <StatusBadge status={m.status} size="sm" />
                </div>

                <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--color-text)', marginBottom: '4px' }}>
                  {m.medicalItemName || m.requestCode || 'Request record'}
                </div>

                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginBottom: '10px' }}>
                  {(m.pickup?.name || m.pickupName || 'Supplier')} to {(m.dropoff?.name || m.dropoffName || 'Requester')}
                </div>

                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '6px',
                  background: 'var(--color-background)',
                  padding: '8px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '11px',
                }}>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Speed:</span>
                    <div><strong>{m.speedKmh ?? 'N/A'} km/h</strong></div>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Altitude:</span>
                    <div><strong>{m.altitudeMeters ?? m.altitudeM ?? 'N/A'} m</strong></div>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Battery:</span>
                    <div><strong style={{ color: 'var(--color-success)' }}>{m.batteryPct ?? 'N/A'}%</strong></div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' }}>
                  <span style={{ fontSize: '11px', color: 'var(--color-primary-blue)', fontWeight: 600 }}>
                    ETA: {m.finalEtaMinutes ?? m.etaMinutes ?? 'N/A'} min
                  </span>
                  <Link
                    to="/missions"
                    style={{ fontSize: '11px', color: 'var(--color-text-secondary)', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '3px' }}
                  >
                    <span>View Mission</span>
                    <ExternalLink size={11} />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </AppLayout>
  );
};

export default LiveTracking;
