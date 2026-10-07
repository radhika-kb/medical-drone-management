import React, { useState } from 'react';
import AppLayout from '../components/layout/AppLayout';
import { 
  Settings as SettingsIcon, 
  ShieldCheck, 
  Sliders, 
  Radio, 
  Database, 
  Save, 
  CheckCircle2 
} from 'lucide-react';

export const Settings = () => {
  const [saved, setSaved] = useState(false);
  const [config, setConfig] = useState({
    minAltitudeM: 100,
    maxAltitudeM: 120,
    maxWindThresholdKmh: 25.0,
    batteryReservePct: 30,
    maxCruisingSpeedKmh: 75,
    autoRerouteGeofences: true,
    springBootApiUrl: 'http://localhost:8080/api',
    fastApiUrl: 'http://localhost:8000',
    websocketEndpoint: 'http://localhost:8080/ws-logistics',
  });

  const handleSave = (e) => {
    e.preventDefault();
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  return (
    <AppLayout
      pageTitle="SYSTEM SETTINGS"
      breadcrumb="Central Operations • Platform Configuration • Airspace Parameters"
    >
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
            Operational Settings & Safety Parameters
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Configure operational parameters and service connections
          </p>
        </div>

        {saved && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            color: 'var(--color-success)',
            fontSize: '13px',
            fontWeight: 600,
          }}>
            <CheckCircle2 size={16} />
            <span>Parameters Saved Successfully</span>
          </div>
        )}
      </div>

      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        {/* Airspace Flight Envelope */}
        <div className="card" style={{ padding: '20px' }}>
          <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 4px', color: 'var(--color-primary-navy)' }}>
            Route Parameters
          </h2>
          <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
            Strict vertical and horizontal flight parameters enforced by the Autonomous Route Agent
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Corridor Altitude Floor (m AGL)</label>
              <input
                type="number"
                value={config.minAltitudeM}
                onChange={(e) => setConfig({ ...config, minAltitudeM: parseInt(e.target.value) })}
                className="form-control"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Corridor Altitude Ceiling (m AGL)</label>
              <input
                type="number"
                value={config.maxAltitudeM}
                onChange={(e) => setConfig({ ...config, maxAltitudeM: parseInt(e.target.value) })}
                className="form-control"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Max Ground Speed (km/h)</label>
              <input
                type="number"
                value={config.maxCruisingSpeedKmh}
                onChange={(e) => setConfig({ ...config, maxCruisingSpeedKmh: parseInt(e.target.value) })}
                className="form-control"
              />
            </div>
          </div>
        </div>

        {/* Multi-Agent Safety Thresholds */}
        <div className="card" style={{ padding: '20px' }}>
          <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 4px', color: 'var(--color-primary-navy)' }}>
            Safety & Battery Margin Policies
          </h2>
          <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
            Thresholds for mission dispatch approval and automated return-to-base triggers
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">Maximum Wind Threshold (km/h)</label>
              <input
                type="number"
                step="0.5"
                value={config.maxWindThresholdKmh}
                onChange={(e) => setConfig({ ...config, maxWindThresholdKmh: parseFloat(e.target.value) })}
                className="form-control"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Minimum Return-to-Base Battery SoC (%)</label>
              <input
                type="number"
                value={config.batteryReservePct}
                onChange={(e) => setConfig({ ...config, batteryReservePct: parseInt(e.target.value) })}
                className="form-control"
              />
            </div>
          </div>
        </div>

        {/* Microservice Endpoint Topology */}
        <div className="card" style={{ padding: '20px' }}>
          <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 4px', color: 'var(--color-primary-navy)' }}>
            Backend Microservice Endpoints
          </h2>
          <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
            Configured proxy targets for Spring Boot, FastAPI Multi-Agent Service, and STOMP WebSockets
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label">Spring Boot REST API Gateway</label>
              <input
                type="text"
                value={config.springBootApiUrl}
                onChange={(e) => setConfig({ ...config, springBootApiUrl: e.target.value })}
                className="form-control"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Python AI FastAPI / LangGraph Agent</label>
              <input
                type="text"
                value={config.fastApiUrl}
                onChange={(e) => setConfig({ ...config, fastApiUrl: e.target.value })}
                className="form-control"
              />
            </div>

            <div className="form-group">
              <label className="form-label">STOMP WebSocket Endpoint</label>
              <input
                type="text"
                value={config.websocketEndpoint}
                onChange={(e) => setConfig({ ...config, websocketEndpoint: e.target.value })}
                className="form-control"
              />
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" className="btn btn-primary" style={{ padding: '10px 24px' }}>
            <Save size={16} />
            <span>Save Operational Parameters</span>
          </button>
        </div>
      </form>
    </AppLayout>
  );
};

export default Settings;
