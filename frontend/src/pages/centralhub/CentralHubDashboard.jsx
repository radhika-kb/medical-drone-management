import CurrentWeatherCard from '../../components/common/CurrentWeatherCard';
import React, { useState, useEffect } from 'react';
import { missionService, requestService, droneService, aiService } from '../../services/api';
import { wsService } from '../../services/websocket';
import BengaluruRadarMap from '../../components/map/BengaluruRadarMap';
import StatusBadge from '../../components/common/StatusBadge';
import { 
  Radar, 
  Send, 
  ShieldAlert, 
  BatteryCharging, 
  Radio, 
  Plane, 
  AlertTriangle, 
  CheckCircle2, 
  RefreshCw, 
  Thermometer,
  Layers,
  ArrowUpRight
} from 'lucide-react';

const formatEta = (minutes) => minutes != null && Number.isFinite(Number(minutes))
  ? `${Number(minutes).toFixed(1)} min`
  : 'Not available';

export const CentralHubDashboard = () => {
  const [missions, setMissions] = useState([]);
  const [dispatchQueue, setDispatchQueue] = useState([]);
  const [availableDrones, setAvailableDrones] = useState([]);
  const [anomalies, setAnomalies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dispatchingId, setDispatchingId] = useState(null);

  const loadData = async () => {
    try {
      setLoading(true);
      const [missionsData, reqData, dronesData, anomaliesData] = await Promise.all([
        missionService.getAll().catch(() => null),
        requestService.getAll().catch(() => null),
        droneService.getAll().catch(() => null),
        aiService.getAnomalies().catch(() => null),
      ]);

      setMissions(missionsData || []);
      setDispatchQueue((reqData || []).filter(r => r.status === 'READY_FOR_DISPATCH'));
      const readyDrones = (dronesData || []).filter(drone => drone.status === 'AVAILABLE');
      setAvailableDrones(readyDrones);
      setAnomalies(anomaliesData || []);
    } catch (e) {
      console.error('Unable to load central hub data:', e);
      setMissions([]);
      setDispatchQueue([]);
      setAvailableDrones([]);
      setAnomalies([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();

    // Subscribe to STOMP live feeds
    const unsubMissions = wsService.subscribe('/topic/missions', (updatedMission) => {
      setMissions(prev => {
        const exists = prev.find(m => m.id === updatedMission.id);
        if (exists) {
          return prev.map(m => m.id === updatedMission.id ? { ...m, ...updatedMission } : m);
        }
        return [updatedMission, ...prev];
      });
    });

    const unsubAlerts = wsService.subscribe('/topic/alerts', (newAlert) => {
      setAnomalies(prev => [newAlert, ...prev]);
    });

    const unsubRequests = wsService.subscribe('/topic/requests', (updatedRequest) => {
      if (!updatedRequest?.requestId) return;
      if (updatedRequest.status === 'READY_FOR_DISPATCH') {
        requestService.getById(updatedRequest.requestId).then(request => {
          setDispatchQueue(prev => [request, ...prev.filter(row => row.id !== request.id)]);
        }).catch(() => {});
      } else {
        setDispatchQueue(prev => prev.filter(row => row.id !== updatedRequest.requestId));
      }
    });

    return () => {
      unsubMissions();
      unsubAlerts();
      unsubRequests();
    };
  }, []);
const reviewMissions = missions.filter(m => ['CENTRAL_HUB_REVIEW', 'DISPATCH_DELAYED'].includes(m.status));

  const handlePrepareReview = async (req) => {
    setDispatchingId(req.id);
    try {
      const result = await missionService.prepareReview(req.id);
      const preparedMission = result.mission || result;
      setMissions(prev => [preparedMission, ...prev.filter(m => m.id !== preparedMission.id)]);
      setDispatchQueue(prev => prev.filter(item => item.id !== req.id));
    } catch (e) {
      console.error('AI review preparation failed:', e);
      window.alert(e.response?.data?.message || 'Could not prepare the AI recommendation for hub review.');
    } finally {
      setDispatchingId(null);
    }
  };

  const handleHubDecision = async (mission, action) => {
    const needsReason = ['DELAY', 'REJECT', 'REALLOCATE'].includes(action);
    const reason = needsReason ? window.prompt(`Reason for ${action.toLowerCase()} (optional):`) : '';
    if (needsReason && reason === null) return;
    setDispatchingId(mission.id);
    try {
      const updatedMission = await missionService.hubDecision(mission.id, action, reason || '');
      setMissions(prev => prev.map(row => row.id === updatedMission.id ? updatedMission : row));
      await loadData();
    } catch (e) {
      console.error(`Central Hub ${action.toLowerCase()} action failed:`, e);
      window.alert(e.response?.data?.message || `Central Hub could not ${action.toLowerCase()} this recommendation.`);
    } finally {
      setDispatchingId(null);
    }
  };

  const handleAnomalyAction = async (anomaly) => {
    if (!anomaly.missionId) return;
    const recommendation = anomaly.recommendedAction || 'DELAY';
    const reason = window.prompt(`AI recommends ${recommendation} for ${anomaly.missionCode || `mission ${anomaly.missionId}`}. Enter the operational reason:`);
    if (reason === null) return;
    if (!reason.trim()) {
      window.alert('Enter a reason so the operational decision is recorded in the audit log.');
      return;
    }
    setDispatchingId(anomaly.missionId);
    try {
      const action = recommendation.toLowerCase();
      const updatedMission = await missionService[action](anomaly.missionId, reason.trim());
      setMissions(prev => prev.map(row => row.id === updatedMission.id ? updatedMission : row));
      setAnomalies(await aiService.getAnomalies());
    } catch (e) {
      window.alert(e.response?.data?.message || `Could not ${recommendation.toLowerCase()} this mission.`);
    } finally {
      setDispatchingId(null);
    }
  };

  return (
    <div>
      <CurrentWeatherCard />
      {/* Top Banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
            Central Hub Air Operations Radar
          </h1>
          <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Bengaluru central hub � Software-generated mission coordination
          </p>
        </div>

        <button onClick={loadData} className="btn btn-secondary">
          <RefreshCw size={16} />
          <span>Refresh Operations</span>
        </button>
      </div>

      {/* KPI Cards */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Active Missions</span>
            <Plane size={18} color="var(--color-nhs-blue)" />
          </div>
          <div className="kpi-value">{missions.filter(m => m.status === 'IN_TRANSIT').length}</div>
          <div className="kpi-subtext">Mission state from backend records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Requests Awaiting Review</span>
            <Send size={18} color="var(--color-nhs-aqua)" />
          </div>
          <div className="kpi-value">{dispatchQueue.length}</div>
          <div className="kpi-subtext">Awaiting mission review</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Hub Available Fleet</span>
            <BatteryCharging size={18} color="var(--color-nhs-green)" />
          </div>
          <div className="kpi-value">
            {availableDrones.filter(d => d.status === 'AVAILABLE').length} / {availableDrones.length}
          </div>
          <div className="kpi-subtext">Availability from fleet records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Corridor Safety Status</span>
            <ShieldAlert size={18} color="var(--color-nhs-green)" />
          </div>
          <div className="kpi-value">{missions.length ? 'See missions' : '�'}</div>
          <div className="kpi-subtext">Airspace assessment unavailable</div>
        </div>
      </div>

      {/* Flagship Bengaluru Radar Map */}
      <div style={{ marginBottom: '1.75rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 700, margin: 0, color: 'var(--color-text)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Radar size={20} color="var(--color-nhs-blue)" />
            <span>Bengaluru Operations Map</span>
          </h2>
          <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
            Map layers load from the operations directory
          </span>
        </div>

        <BengaluruRadarMap height="520px" activeMissions={missions} />
      </div>

      {/* Grid: Dispatch Queue & Anomaly Feed */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: '1.5rem', marginBottom: '1.75rem' }}>
        {/* Dispatch Queue */}
        <div>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
            Paid Requests Ready for AI Review
          </h2>

          {dispatchQueue.length === 0 ? (
            <div style={{ padding: '2rem', textAlign: 'center', background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', color: 'var(--color-text-secondary)' }}>
              No paid requests are waiting for an AI recommendation.
            </div>
          ) : (
            <div className="data-table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Request Code</th>
                    <th>Payload Item</th>
                    <th>Supplier → Requester</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {dispatchQueue.map((req) => (
                    <tr key={req.id}>
                      <td>
                        <span style={{ fontWeight: 600 }}>{req.requestCode}</span>
                        <div><StatusBadge status={req.priority} size="small" /></div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{req.medicalItemName}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>{req.quantity} units</div>
                      </td>
                      <td>
                        <div style={{ fontSize: '0.8125rem' }}>
                          <b>{req.supplyingHospitalName || 'Supplier'}</b> → <b>{req.hospitalName || 'Requester'}</b>
                        </div>
                      </td>
                      <td>
                        <button
                          onClick={() => handlePrepareReview(req)}
                          disabled={dispatchingId === req.id}
                          className="btn btn-primary"
                          style={{ padding: '0.375rem 0.625rem', fontSize: '0.75rem' }}
                        >
                          <ShieldCheck size={14} />
                          <span>{dispatchingId === req.id ? 'Preparing...' : 'Prepare AI Review'}</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Human review of AI recommendations */}
        <div>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
            Central Hub Decision Queue
          </h2>
          {reviewMissions.length === 0 ? (
            <div style={{ padding: '1.5rem', background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', color: 'var(--color-text-secondary)' }}>
              No AI recommendations are waiting for an operator decision.
            </div>
          ) : reviewMissions.map((mission) => (
            <article key={mission.id} style={{ background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', padding: '1rem', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                <div>
                  <strong>{mission.missionCode}</strong> · Request {mission.requestCode}
                  <div style={{ marginTop: '0.35rem' }}><StatusBadge status={mission.status} /></div>
                </div>
                <div style={{ fontSize: '0.875rem' }}>
                  <b>{mission.droneCode}</b> · {mission.droneModel} · {mission.batteryPct ?? '—'}% battery
                  <div>{mission.distanceKm ?? '—'} km route distance</div>
                </div>
              </div>
              <div aria-label="ETA estimates" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(145px, 1fr))', gap: '0.65rem', margin: '0.9rem 0', padding: '0.75rem', background: '#F7FAFC', borderRadius: '6px' }}>
                <div><div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>Baseline ETA · Routing</div><strong>{formatEta(mission.baselineEtaMinutes)}</strong></div>
                <div><div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>ML ETA · Model</div><strong>{formatEta(mission.mlEtaMinutes)}</strong></div>
                <div><div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>Final operational ETA</div><strong style={{ color: 'var(--color-primary-blue)' }}>{formatEta(mission.finalEtaMinutes ?? mission.etaMinutes)}</strong></div>
              </div>
              {mission.hubDecisionReason && <p style={{ margin: '0.75rem 0', color: 'var(--color-text-secondary)' }}>{mission.hubDecisionReason}</p>}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '0.5rem', margin: '0.9rem 0' }}>
                {(mission.decisionTrail || []).map((item, index) => (
                  <div key={`${item.agent}-${index}`} style={{ background: '#F7FAFC', borderRadius: '6px', padding: '0.65rem', fontSize: '0.78rem' }}>
                    <div style={{ fontWeight: 700 }}>{item.agent}: {item.decision}</div>
                    {item.score != null && <div>Score: {Number(item.score).toFixed(2)}</div>}
                    {item.reason && <div style={{ marginTop: '0.25rem', color: 'var(--color-text-secondary)' }}>{item.reason}</div>}
                  </div>
                ))}
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--color-text-secondary)', marginBottom: '0.8rem' }}>
                Reviewed route: {(mission.waypoints || []).length} waypoints are shown on the radar map.
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <button className="btn btn-primary" disabled={dispatchingId === mission.id} onClick={() => handleHubDecision(mission, 'DISPATCH')}>
                  <Send size={14} /><span>{dispatchingId === mission.id ? 'Processing…' : 'Approve & Dispatch'}</span>
                </button>
                <button className="btn btn-secondary" disabled={dispatchingId === mission.id} onClick={() => handleHubDecision(mission, 'DELAY')}>Delay</button>
                <button className="btn btn-secondary" disabled={dispatchingId === mission.id} onClick={() => handleHubDecision(mission, 'REALLOCATE')}>Reallocate</button>
                <button className="btn btn-secondary" disabled={dispatchingId === mission.id} onClick={() => handleHubDecision(mission, 'REJECT')}>Reject recommendation</button>
              </div>
            </article>
          ))}
        </div>

        {/* Real-time Anomaly Feed */}
        <div>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ShieldAlert size={18} color="var(--color-nhs-blue)" />
            <span>AI Corridor Anomaly Stream</span>
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {anomalies.map((anom) => (
              <div 
                key={anom.id}
                style={{
                  background: '#FFFFFF',
                  border: '1px solid var(--color-border)',
                  borderLeft: `4px solid ${anom.severity === 'CRITICAL' ? 'var(--color-nhs-red)' : ['HIGH', 'WARNING'].includes(anom.severity) ? 'var(--color-nhs-orange)' : 'var(--color-nhs-green)'}`,
                  borderRadius: '6px',
                  padding: '0.75rem',
                  fontSize: '0.8125rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.375rem' }}>
                  <span style={{ fontWeight: 700, color: 'var(--color-text)' }}>
                    {anom.anomalyType.replace(/_/g, ' ')} · {anom.severity}
                  </span>
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-muted)' }}>
                    {anom.detectedAt ? new Date(anom.detectedAt).toLocaleString() : ''}
                  </span>
                </div>
                <div style={{ color: 'var(--color-text-secondary)', lineHeight: 1.4 }}>
                  {anom.explanation}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', marginTop: '0.65rem', flexWrap: 'wrap' }}>
                  <span><b>{anom.missionCode || `Mission ${anom.missionId}`}</b> · AI recommends <b>{anom.recommendedAction || 'DELAY'}</b></span>
                  <button className="btn btn-secondary" disabled={!anom.missionId || dispatchingId === anom.missionId || !missions.some(m => m.id === anom.missionId && ['DISPATCHED', 'TRAVELLING_TO_PICKUP', 'IN_TRANSIT', 'RECOVERY_REQUIRED'].includes(m.status))} onClick={() => handleAnomalyAction(anom)}>
                    {dispatchingId === anom.missionId ? 'Applying…' : `Investigate & ${anom.recommendedAction || 'DELAY'}`}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Active Corridor Flight Monitoring Table */}
      <div>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
          Active Mission Records & Telemetry
        </h2>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Mission Number</th>
                <th>Assigned Unit</th>
                <th>Corridor Trajectory</th>
                <th>Altitude (AGL)</th>
                <th>Airspeed</th>
                <th>Battery SoC</th>
                <th>Chiller Bay Temp</th>
                <th>Baseline ETA</th>
                <th>ML ETA</th>
                <th>Final ETA</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {missions.map((m) => (
                <tr key={m.id}>
                  <td>
                    <span style={{ fontWeight: 700, color: 'var(--color-nhs-blue)' }}>{m.missionCode}</span>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{m.droneCode || m.droneModel}</div>
                  </td>
                  <td>
                    <div style={{ fontSize: '0.8125rem' }}>
                      {m.pickup?.name || 'Depot'} ➔ {m.dropoff?.name || 'Receiver'}
                    </div>
                  </td>
                  <td>
                    <span style={{ fontWeight: 600 }}>{m.altitudeMeters ?? '?'}{m.altitudeMeters != null ? ' m' : ''}</span>
                  </td>
                  <td>
                    <span style={{ fontWeight: 600 }}>{m.speedKmh ?? '?'}{m.speedKmh != null ? ' km/h' : ''}</span>
                  </td>
                  <td>
                    <span style={{ fontWeight: 700, color: 'var(--color-nhs-green)' }}>{m.batteryPct ?? '?'}{m.batteryPct != null ? '%' : ''}</span>
                  </td>
                  <td>
                    <span style={{ fontWeight: 700, color: 'var(--color-nhs-blue)' }}>{m.cargoTempC ?? '?'}°C</span>
                  </td>
                  <td>{formatEta(m.baselineEtaMinutes)}</td>
                  <td>{formatEta(m.mlEtaMinutes)}</td>
                  <td style={{ fontWeight: 700, color: 'var(--color-nhs-blue)' }}>{formatEta(m.finalEtaMinutes ?? m.etaMinutes)}</td>
                  <td>
                    <StatusBadge status={m.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default CentralHubDashboard;
