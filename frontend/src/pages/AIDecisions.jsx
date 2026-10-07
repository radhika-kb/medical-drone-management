import React, { useEffect, useState } from 'react';
import AppLayout from '../components/layout/AppLayout';
import { aiService, missionService } from '../services/api';
import { RefreshCw, Sparkles } from 'lucide-react';

export const AIDecisions = () => {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadRecords = async () => {
    setLoading(true);
    try {
      const missions = await missionService.getAll();
      const decisionGroups = await Promise.all((missions || []).map(async mission => {
        const values = await aiService.getDecisions(mission.id).catch(() => []);
        return (Array.isArray(values) ? values : []).map(value => ({ ...value, missionCode: mission.missionCode || `Mission ${mission.id}` }));
      }));
      setRecords(decisionGroups.flat());
    } catch (error) {
      console.warn('Unable to load stored AI decisions:', error);
      setRecords([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadRecords(); }, []);

  return (
    <AppLayout pageTitle="AI DECISION RECORDS" breadcrumb="Stored AI decision-support records">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, gap: 12 }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 800, margin: 0, color: 'var(--color-primary-navy)' }}>AI Decision Records</h1>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--color-text-secondary)' }}>Stored agent outputs associated with missions. AI outputs support operator review.</p>
        </div>
        <button className="btn btn-secondary" onClick={loadRecords} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''} /><span>Refresh</span></button>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ padding: 16, display: 'flex', gap: 8, alignItems: 'center' }}><Sparkles size={17} /><strong>{records.length} stored decision records</strong></div>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead><tr><th>Time</th><th>Mission</th><th>Agent</th><th>Decision</th><th>Score</th><th>Reason</th></tr></thead>
            <tbody>
              {records.map(record => (
                <tr key={record.id}>
                  <td>{record.createdAt ? new Date(record.createdAt).toLocaleString() : '—'}</td>
                  <td>{record.missionCode}</td>
                  <td>{record.agentName || '—'}</td>
                  <td>{record.decision || '—'}</td>
                  <td>{record.score == null ? '—' : Number(record.score).toFixed(2)}</td>
                  <td style={{ minWidth: 280, whiteSpace: 'normal' }}>{record.reason || 'No reason recorded.'}</td>
                </tr>
              ))}
              {!loading && records.length === 0 && <tr><td colSpan="6" style={{ textAlign: 'center', padding: 24, color: 'var(--color-text-secondary)' }}>No stored AI decisions are available from the backend.</td></tr>}
              {loading && <tr><td colSpan="6" style={{ textAlign: 'center', padding: 24 }}>Loading stored decisions…</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </AppLayout>
  );
};

export default AIDecisions;
