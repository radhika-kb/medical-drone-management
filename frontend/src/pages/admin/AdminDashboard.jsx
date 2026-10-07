import CurrentWeatherCard from '../../components/common/CurrentWeatherCard';
import React, { useState, useEffect } from 'react';
import { hospitalService, droneService, catalogService, aiService, auditService } from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import { 
  Building2, 
  Plane, 
  Package, 
  CheckCircle2, 
  XCircle, 
  ShieldCheck, 
  Activity, 
  RefreshCw,
  Database,
  Cpu
} from 'lucide-react';

export const AdminDashboard = () => {
  const [hospitals, setHospitals] = useState([]);
  const [drones, setDrones] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [demandAlerts, setDemandAlerts] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [runningForecast, setRunningForecast] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    try {
      setLoading(true);
      const [hospData, droneData, catData, alertData, auditData] = await Promise.all([
        hospitalService.getAll(false).catch(() => null),
        droneService.getAll().catch(() => null),
        catalogService.getAll().catch(() => null),
        aiService.getDemandRiskAlerts().catch(() => null),
        auditService.getRecent(200).catch(() => null),
      ]);

      setHospitals(hospData || []);
      setDrones(droneData || []);
      setCatalog(catData || []);
      setDemandAlerts(alertData || []);
      setAuditLogs(auditData || []);
    } catch (e) {
      console.error('Unable to load admin data:', e);
      setHospitals([]);
      setDrones([]);
      setCatalog([]);
      setDemandAlerts([]);
      setAuditLogs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      aiService.getDemandRiskAlerts().then(setDemandAlerts).catch(() => {});
    }, 60_000);
    return () => window.clearInterval(timer);
  }, []);
const handleVerify = async (id, approved) => {
    try {
      const updated = await hospitalService.verify(id, approved ? 'APPROVED' : 'REJECTED');
      setHospitals(prev => prev.map(h => h.id === id ? updated : h));
    } catch (e) {
      console.error('Hospital verification failed:', e);
      return;
    }
  };

  const handleRunDemandForecast = async () => {
    try {
      setRunningForecast(true);
      await aiService.runDemandForecasts();
      const alerts = await aiService.getDemandRiskAlerts();
      setDemandAlerts(alerts || []);
    } catch (e) {
      console.error('Demand forecast run failed:', e);
    } finally {
      setRunningForecast(false);
    }
  };

  return (
    <div>
      <CurrentWeatherCard />
      {/* Top Banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
            System Administration
          </h1>
          <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Bengaluru institution directory and logistics administration
          </p>
        </div>

        <button onClick={loadData} className="btn btn-secondary">
          <RefreshCw size={16} />
          <span>Refresh Records</span>
        </button>
      </div>

      {/* KPI Cards */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Admin-approved facilities</span>
            <Building2 size={18} color="var(--color-nhs-blue)" />
          </div>
          <div className="kpi-value">{hospitals.filter(h => h.verificationStatus === 'APPROVED').length}</div>
          <div className="kpi-subtext">Across 4 Bengaluru zones</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Pending facility review</span>
            <Activity size={18} color="var(--color-nhs-orange)" />
          </div>
          <div className="kpi-value">{hospitals.filter(h => h.verificationStatus !== 'APPROVED').length}</div>
          <div className="kpi-subtext">Requires location confirmation by an administrator</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Fleet unit records</span>
            <Plane size={18} color="var(--color-nhs-green)" />
          </div>
          <div className="kpi-value">{drones.length}</div>
          <div className="kpi-subtext">Unit and status data from application records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Database & Lock Health</span>
            <Database size={18} color="var(--color-nhs-aqua)" />
          </div>
          <div className="kpi-value">MySQL + Redis</div>
          <div className="kpi-subtext">Transactional locking active</div>
        </div>
      </div>

      {/* Forecasts compare model demand with unreserved and reserved stock */}
      <section style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
              Demand Forecast & Inventory Risk
            </h2>
            <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>
              Hospital and zone forecasts run every six hours from recent medical requests.
            </p>
          </div>
          <button onClick={handleRunDemandForecast} disabled={runningForecast || loading} className="btn btn-secondary">
            <RefreshCw size={15} />
            <span>{runningForecast ? 'Forecasting…' : 'Run Forecast Now'}</span>
          </button>
        </div>

        {demandAlerts.length === 0 ? (
          <div style={{ padding: '1rem', background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', color: 'var(--color-text-secondary)' }}>
            No forecast snapshots yet. Run a forecast to evaluate approved hospitals and medical items.
          </div>
        ) : (
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Medical Item</th>
                  <th>Hospital</th>
                  <th>Zone</th>
                  <th>Predicted / 24h</th>
                  <th>Available</th>
                  <th>Reserved</th>
                  <th>Shortage</th>
                  <th>Risk</th>
                </tr>
              </thead>
              <tbody>
                {demandAlerts.map((alert) => (
                  <tr key={alert.id}>
                    <td><b>{alert.medicalItemName}</b><div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>{alert.medicalItemCode}</div></td>
                    <td>{alert.hospitalName}</td>
                    <td>{(alert.zoneName || 'Unassigned').replace('_ZONE', '')}</td>
                    <td>{alert.predictedDemand} {alert.unit}</td>
                    <td>{alert.availableQuantity}</td>
                    <td>{alert.reservedQuantity}</td>
                    <td><b>{alert.shortageQuantity}</b></td>
                    <td><StatusBadge status={alert.riskLevel} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Hospital Verification Approvals */}
      <div style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
          Hospital Registration & Helipad Verification Approvals
        </h2>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Hospital / Facility</th>
                <th>Classification</th>
                <th>Airspace Sector</th>
                <th>Physical Address</th>
                <th>Certification Status</th>
                <th>Regulatory Action</th>
              </tr>
            </thead>
            <tbody>
              {hospitals.map((hosp) => (
                <tr key={hosp.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{hosp.name}</div>
                  </td>
                  <td>
                    <span className="status-badge badge-neutral" style={{ fontSize: '0.7rem' }}>
                      {(hosp.type || 'HOSPITAL').replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td>
                    <b>{(hosp.zoneName || 'Unassigned').replace('_ZONE', '')} Zone</b>
                  </td>
                  <td>
                    <span style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)' }}>{hosp.address}</span>
                  </td>
                  <td>
                    <span className={`status-badge ${hosp.verificationStatus === 'APPROVED' ? 'badge-success' : 'badge-warning'}`}>
                      {hosp.verificationStatus || 'Pending Verification'}
                    </span>
                  </td>
                  <td>
                    {hosp.verificationStatus !== 'APPROVED' ? (
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <button
                          onClick={() => handleVerify(hosp.id, true)}
                          disabled={hosp.latitude == null || hosp.longitude == null || !hosp.zoneId}
                          title={hosp.latitude == null ? 'Confirm the hospital location before approval' : undefined}
                          className="btn btn-primary"
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                        >
                          <CheckCircle2 size={14} />
                          <span>Approve</span>
                        </button>
                        <button
                          onClick={() => handleVerify(hosp.id, false)}
                          className="btn btn-secondary"
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', color: 'var(--color-nhs-red)' }}
                        >
                          <XCircle size={14} />
                          <span>Reject</span>
                        </button>
                      </div>
                    ) : (
                      <span style={{ fontSize: '0.75rem', color: 'var(--color-nhs-green)', fontWeight: 600 }}>
                        Approved
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Fleet Overview */}
      <div style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
          Fleet Unit Records
        </h2>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Unit Code</th>
                <th>Unit Model</th>
                <th>Operating Status</th>
                <th>Battery SoC</th>
                <th>Flight Hours</th>
                <th>Additional Operational Data</th>
              </tr>
            </thead>
            <tbody>
              {drones.map((drone) => (
                <tr key={drone.id}>
                  <td>
                      <span style={{ fontWeight: 700, color: 'var(--color-nhs-blue)' }}>{drone.droneCode}</span>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{drone.model}</div>
                  </td>
                  <td>
                    <StatusBadge status={drone.status} />
                  </td>
                  <td>
                    <span style={{ fontWeight: 700, color: (drone.batteryPct || 0) > 70 ? 'var(--color-nhs-green)' : 'var(--color-nhs-orange)' }}>
                      {drone.batteryPct ?? '—'}%
                    </span>
                  </td>
                  <td>
                    <span>—</span>
                  </td>
                  <td>
                    <span style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)' }}>—</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Catalog Overview */}
      <div>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
          Medical Item Catalog Examples
        </h2>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Item Name</th>
                <th>Classification</th>
                <th>Unit</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {catalog.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{item.name}</div>
                  </td>
                  <td>
                    <span className="status-badge badge-neutral" style={{ fontSize: '0.7rem' }}>
                      {(item.category || 'OTHER').replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td>
                    <span style={{ fontWeight: 600, color: 'var(--color-nhs-blue)' }}>{item.unit || '—'}</span>
                  </td>
                  <td>
                    <span>{item.description || '—'}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Immutable-style operational history from backend audit records */}
      <section style={{ marginTop: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>Audit Logs</h2>
            <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>Recent authenticated user and system events, newest first.</p>
          </div>
          <button onClick={loadData} className="btn btn-secondary"><RefreshCw size={15} /><span>Refresh Logs</span></button>
        </div>
        {auditLogs.length === 0 ? (
          <div style={{ padding: '1rem', background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', color: 'var(--color-text-secondary)' }}>No audit events have been recorded yet.</div>
        ) : (
          <div className="data-table-container">
            <table className="data-table">
              <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
              <tbody>
                {auditLogs.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.createdAt ? new Date(entry.createdAt).toLocaleString() : '—'}</td>
                    <td>{entry.actorEmail || (entry.actorUserId ? `User ${entry.actorUserId}` : 'System')}</td>
                    <td><b>{entry.action}</b></td>
                    <td>{entry.entityName || '—'}{entry.entityId ? ` #${entry.entityId}` : ''}</td>
                    <td style={{ maxWidth: '420px', whiteSpace: 'normal' }}>{entry.metadata || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};

export default AdminDashboard;
