import React, { useEffect, useState } from 'react';
import AppLayout from '../components/layout/AppLayout';
import { analyticsService } from '../services/api';
import { RefreshCw } from 'lucide-react';

export default function Analytics() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const load = async () => {
    setLoading(true);
    try { setSummary(await analyticsService.getSummary()); setError(false); }
    catch { setSummary(null); setError(true); }
    finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const metric = (label, value) => <div className="kpi-card" key={label}>
    <div className="kpi-label">{label}</div>
    <div className="kpi-value">{loading ? '…' : value ?? '—'}</div>
  </div>;

  return <AppLayout pageTitle="OPERATIONAL ANALYTICS" breadcrumb="Central Operations · Current system records">
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
      <div><h1 style={{ fontSize: 20, margin: 0 }}>Operational Summary</h1>
        <p style={{ margin: '4px 0', color: 'var(--color-text-secondary)' }}>Counts are read from current backend records.</p></div>
      <button className="btn btn-secondary" onClick={load} disabled={loading}><RefreshCw size={15} /> Refresh</button>
    </div>
    {error && <div role="status" className="card" style={{ padding: 14, marginBottom: 16 }}>Analytics data is unavailable.</div>}
    <div className="kpi-grid" style={{ marginBottom: 24 }}>
      {metric('Requests', summary?.totalRequests)}
      {metric('Pending Requests', summary?.pendingRequests)}
      {metric('Mission Records', summary?.totalMissions)}
      {metric('Active Missions', summary?.activeMissions)}
      {metric('Completed Mission Records', summary?.deliveredMissions)}
      {metric('Available Unit Records', summary?.availableDrones)}
    </div>
    <div className="card" style={{ padding: 20 }}>
      <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Records by Zone</h2>
      {!summary ? <div>{error ? 'Zone summary unavailable.' : 'Loading…'}</div> : <div style={{ overflowX: 'auto' }}>
        <table className="data-table"><thead><tr><th>Zone</th><th>Unit records</th><th>Mission records</th></tr></thead>
          <tbody>{[...new Set([...Object.keys(summary.dronesPerZone || {}), ...Object.keys(summary.missionsPerZone || {})])].map(zone =>
            <tr key={zone}><td>{zone}</td><td>{summary.dronesPerZone?.[zone] ?? 0}</td><td>{summary.missionsPerZone?.[zone] ?? 0}</td></tr>)}</tbody>
        </table>
      </div>}
    </div>
  </AppLayout>;
}
