import React, { useEffect, useState } from 'react';
import AppLayout from '../components/layout/AppLayout';
import { aiService } from '../services/api';
import { AlertTriangle, AlertOctagon, Info, RefreshCw } from 'lucide-react';

const normalizeAlert = (row, source) => ({
  id: `${source}-${row.id ?? row.missionId ?? row.detectedAt ?? row.createdAt ?? row.title ?? row.description ?? 'row'}`,
  severity: row.severity || row.riskLevel || 'INFO',
  title: row.title || row.anomalyType?.replaceAll('_', ' ') || row.riskType?.replaceAll('_', ' ') || 'Operational alert',
  message: row.explanation || row.description || row.message || 'No explanation was supplied by the backend.',
  timestamp: row.detectedAt || row.createdAt || row.updatedAt || null,
  missionRef: row.missionCode || row.missionId || null,
  status: row.acknowledged ? 'RESOLVED' : (row.status || 'ACTIVE'),
});

export const Alerts = () => {
  const [filter, setFilter] = useState('ALL');
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadAlerts = async () => {
    setLoading(true);
    const [anomalies, inventoryRisks] = await Promise.all([
      aiService.getAnomalies().catch(() => []),
      aiService.getDemandRiskAlerts().catch(() => []),
    ]);
    setAlerts([
      ...(anomalies || []).map(row => normalizeAlert(row, 'mission')),
      ...(inventoryRisks || []).map(row => normalizeAlert(row, 'inventory')),
    ]);
    setLoading(false);
  };

  useEffect(() => { loadAlerts(); }, []);

  const filteredAlerts = alerts.filter(alert => {
    if (filter === 'ALL') return true;
    if (filter === 'ACTIVE') return alert.status !== 'RESOLVED';
    return alert.severity === filter;
  });

  return (
    <AppLayout pageTitle="OPERATIONAL ALERTS" breadcrumb="Mission monitoring and inventory-risk alerts">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, gap: 12 }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 800, color: 'var(--color-primary-navy)', margin: 0 }}>Operational Alerts</h1>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--color-text-secondary)' }}>Alerts returned by mission anomaly and inventory-risk services.</p>
        </div>
        <button onClick={loadAlerts} className="btn btn-secondary" disabled={loading}>
          <RefreshCw size={15} className={loading ? 'spin' : ''} /><span>Refresh</span>
        </button>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
        {[['ALL', 'All'], ['ACTIVE', 'Unresolved'], ['CRITICAL', 'Critical'], ['HIGH', 'High'], ['WARNING', 'Warning'], ['INFO', 'Information']].map(([key, label]) => (
          <button key={key} onClick={() => setFilter(key)} className={`btn ${filter === key ? 'btn-primary' : 'btn-secondary'}`} style={{ fontSize: 12, padding: '6px 14px' }}>{label}</button>
        ))}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {filteredAlerts.map(alert => {
          const critical = alert.severity === 'CRITICAL';
          const warning = ['WARNING', 'HIGH'].includes(alert.severity);
          const Icon = critical ? AlertOctagon : warning ? AlertTriangle : Info;
          return (
            <article key={alert.id} style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderLeft: `4px solid ${critical ? 'var(--color-error)' : warning ? 'var(--color-warning)' : 'var(--color-primary-blue)'}`, borderRadius: 'var(--radius-md)', padding: '16px 20px', display: 'flex', gap: 14 }}>
              <Icon size={20} />
              <div>
                <strong>{alert.title}</strong><span style={{ marginLeft: 8, fontSize: 11 }}>{alert.status}</span>
                <p style={{ margin: '6px 0', fontSize: 13 }}>{alert.message}</p>
                <small>{alert.timestamp ? new Date(alert.timestamp).toLocaleString() : 'Timestamp unavailable'}{alert.missionRef ? ` · Mission ${alert.missionRef}` : ''}</small>
              </div>
            </article>
          );
        })}
        {!loading && filteredAlerts.length === 0 && (
          <div className="card" style={{ padding: 24, color: 'var(--color-text-secondary)', textAlign: 'center' }}>
            No alerts are currently available from the backend.
          </div>
        )}
      </div>
    </AppLayout>
  );
};

export default Alerts;
