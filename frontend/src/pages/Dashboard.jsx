import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { missionService, requestService, droneService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import StatusBadge from '../components/common/StatusBadge';
import BengaluruRadarMap from '../components/map/BengaluruRadarMap';
import AIDecisionTrail from '../components/ai/AIDecisionTrail';
import CurrentWeatherCard from '../components/common/CurrentWeatherCard';
import TwoStepRequestModal from '../components/forms/TwoStepRequestModal';
import { 
  Send, 
  Clock, 
  Cpu, 
  CheckCircle2, 
  AlertTriangle, 
  PlusCircle, 
  RefreshCw, 
  ShieldCheck,
  TrendingUp,
  Radio,
  MapPin,
  ArrowRight,
  ExternalLink
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const Dashboard = () => {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [missions, setMissions] = useState([]);
  const [requests, setRequests] = useState([]);
  const [drones, setDrones] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedZone, setSelectedZone] = useState('ALL');
  const [isModalOpen, setIsModalOpen] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      const [missionsData, reqData, dronesData] = await Promise.all([
        missionService.getAll().catch(() => null),
        requestService.getAll().catch(() => null),
        droneService.getAll().catch(() => null),
      ]);

      setMissions(missionsData || []);
      setRequests(reqData || []);
      setDrones(dronesData || []);
    } catch (e) {
      console.error('Unable to load operational data:', e);
      setMissions([]);
      setRequests([]);
      setDrones([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (location.state?.openCreateRequest) {
      setIsModalOpen(true);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.key]);

  const activeMissionsCount = missions.filter(m => ['IN_TRANSIT', 'DISPATCHED'].includes(m.status)).length;
  const pendingRequestsCount = requests.filter(r => ['PENDING', 'ALLOCATING_RESOURCE', 'READY_FOR_DISPATCH'].includes(r.status)).length;
  const airborneDronesCount = drones.filter(d => d.status === 'AVAILABLE').length;

  const greeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  };

  return (
    <AppLayout 
      pageTitle="OPERATIONS DASHBOARD" 
      breadcrumb="Central Operations • Bengaluru Corridor • All Zones"
    >
      <CurrentWeatherCard />
      {/* Top Welcome & Quick Actions Bar */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '24px',
        flexWrap: 'wrap',
        gap: '16px',
      }}>
        <div>
          <h1 style={{
            fontSize: '22px',
            fontWeight: 800,
            color: 'var(--color-primary-navy)',
            margin: 0,
            letterSpacing: '-0.02em',
          }}>
            {greeting()}, {user?.fullName || 'Flight Director'}
          </h1>
          <p style={{
            margin: '4px 0 0',
            fontSize: '13px',
            color: 'var(--color-text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}>
            <span>Bengaluru Operations</span>
            <span>•</span>
            <span style={{ color: 'var(--color-text-secondary)', fontWeight: 600 }}>Operations</span>
            <span>•</span>
            <span>Central Hub</span>
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button 
            onClick={loadData}
            className="btn btn-secondary"
            title="Refresh operational telemetry"
          >
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Sync Telemetry</span>
          </button>

          <button 
            onClick={() => setIsModalOpen(true)}
            className="btn btn-primary"
            style={{ padding: '9px 18px' }}
          >
            <PlusCircle size={16} />
            <span>Create Medical Request</span>
          </button>
        </div>
      </div>

      {/* 6 Key Operational Metric Cards (Section 5) */}
      <div className="kpi-grid" style={{ marginBottom: '24px' }}>
        <div className="kpi-card">
          <div className="kpi-label">Active Missions</div>
          <div className="kpi-value" style={{ color: 'var(--color-primary-navy)' }}>
            {activeMissionsCount < 10 ? `0${activeMissionsCount}` : activeMissionsCount}
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--color-success)', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <TrendingUp size={12} />
            <span>{missions.filter(m => m.status === 'IN_TRANSIT').length} in transit · {missions.filter(m => m.status === 'DISPATCHED').length} dispatched (records)</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Pending Requests</div>
          <div className="kpi-value" style={{ color: 'var(--color-warning)' }}>
            {pendingRequestsCount < 10 ? `0${pendingRequestsCount}` : pendingRequestsCount}
          </div>
          <div className="kpi-subtext">
            <span>{requests.filter(r => r.priority === 'CRITICAL').length} critical · {requests.filter(r => r.status === 'READY_FOR_DISPATCH').length} ready for dispatch</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Available Units</div>
          <div className="kpi-value" style={{ color: 'var(--color-primary-blue)' }}>
            {airborneDronesCount}
          </div>
          <div className="kpi-subtext">
            <span>Fleet records</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Delivery SLA</div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            —
          </div>
          <div className="kpi-subtext">
            <span>Not enough completed mission history</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Average Transit ETA</div>
          <div className="kpi-value">
            — <span style={{ fontSize: '14px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>min</span>
          </div>
          <div className="kpi-subtext">
            <span>Not enough completed mission history</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Airspace Assessment</div>
          <div className="kpi-value" style={{ color: 'var(--color-error)' }}>
            —
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--color-error)' }}>
            <span>Alerts are supplied by backend monitoring services</span>
          </div>
        </div>
      </div>

      {/* Main Operations Radar Stage (65% Map / 35% Active Missions) */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1.8fr) minmax(0, 1fr)',
        gap: '24px',
        marginBottom: '24px',
      }}>
        {/* Left: Radar Airspace Map with Zone Filters */}
        <div className="card" style={{ padding: '16px', display: 'flex', flexDirection: 'column' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '14px',
            flexWrap: 'wrap',
            gap: '10px',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Radio size={18} color="var(--color-primary-blue)" />
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
                Bengaluru Operations Map
              </h2>
              <span style={{
                fontSize: '11px',
                fontWeight: 600,
                padding: '2px 8px',
                borderRadius: '12px',
                background: 'rgba(22, 128, 60, 0.1)',
                color: 'var(--color-success)',
                border: '1px solid rgba(22, 128, 60, 0.2)',
              }}>
                Telemetry Active
              </span>
            </div>

            <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
              Operational Map
            </div>
          </div>

          {/* Radar Map Component */}
          <div style={{ flex: 1, minHeight: '440px' }}>
            <BengaluruRadarMap 
              height="440px" 
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
              <strong>Legend:</strong> 🟦 Central Hub • 🏥 Hospital Node • 🔴 Geofence Restricted • ✈️ Active Drone
            </div>
            <Link to="/tracking" style={{ color: 'var(--color-primary-blue)', textDecoration: 'none', fontWeight: 600 }}>
              Full Radar Console →
            </Link>
          </div>
        </div>

        {/* Right: Active Priority Missions Queue */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}>
            <div>
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
                Active Flight Corridor
              </h2>
              <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                Mission positions from backend records
              </p>
            </div>
            <Link to="/missions" className="btn btn-secondary" style={{ fontSize: '12px', padding: '4px 10px' }}>
              <span>View All</span>
              <ArrowRight size={13} />
            </Link>
          </div>

          {/* Missions List */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto', flex: 1 }}>
            {missions.slice(0, 3).map((mission) => (
              <div
                key={mission.id}
                style={{
                  background: 'var(--color-surface)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: '14px',
                  boxShadow: 'var(--shadow-sm)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{
                      fontWeight: 700,
                      fontSize: '13px',
                      color: 'var(--color-primary-navy)',
                    }}>
                      {mission.callsign}
                    </span>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      • {mission.droneId}
                    </span>
                  </div>
                  <StatusBadge status={mission.status} size="sm" />
                </div>

                <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-text)' }}>
                  {mission.medicalItem}
                </div>

                <div style={{
                  fontSize: '11px',
                  color: 'var(--color-text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                }}>
                  <MapPin size={12} />
                  <span>{mission.origin} → {mission.destination}</span>
                </div>

                {/* Telemetry pill row */}
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '6px',
                  background: 'var(--color-background)',
                  padding: '6px 8px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '11px',
                  marginTop: '4px',
                }}>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>ETA: </span>
                    <strong style={{ color: 'var(--color-primary-blue)' }}>{mission.etaMinutes ?? '?'}{mission.etaMinutes != null ? ' min' : ''}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Battery: </span>
                    <strong>{mission.batteryPct ?? '?'}{mission.batteryPct != null ? '%' : ''}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-text-secondary)' }}>Speed: </span>
                    <strong>{mission.speedKmh ?? '?'}{mission.speedKmh != null ? ' km/h' : ''}</strong>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
                  <Link
                    to={`/missions`}
                    style={{
                      fontSize: '11px',
                      fontWeight: 600,
                      color: 'var(--color-primary-blue)',
                      textDecoration: 'none',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    <span>Mission Details</span>
                    <ExternalLink size={11} />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom Row: AI Multi-Agent Decision Trail & 4-Zone Resource Allocation */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 1fr)',
        gap: '24px',
      }}>
        {/* Left: Auditable AI Multi-Agent Decision Trail */}
        <div className="card" style={{ padding: '20px' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}>
            <div>
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
                Multi-Agent AI Decision Trail
              </h2>
              <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                Auditable decision pipeline for current mission REQ-2026-BLR-0042
              </p>
            </div>
            <Link to="/ai-decisions" className="btn btn-secondary" style={{ fontSize: '12px', padding: '4px 10px' }}>
              <span>Audit Hub</span>
              <ArrowRight size={13} />
            </Link>
          </div>

          <AIDecisionTrail />
        </div>

        {/* Right: Four-Zone Bengaluru Resource Allocation */}
        <div className="card" style={{ padding: '20px' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}>
            <div>
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--color-primary-navy)' }}>
                Bengaluru 4-Zone Resource Hub
              </h2>
              <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                Bengaluru fleet and facility overview
              </p>
            </div>
            <Link to="/airborne-units" className="btn btn-secondary" style={{ fontSize: '12px', padding: '4px 10px' }}>
              <span>Manage Fleet</span>
            </Link>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {/* North Zone */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--color-border)',
              background: 'var(--color-surface)',
            }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
                  North Corridor (Hebbal / Yelahanka)
                </div>
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                  Facility locations: see the confirmed hospital directory
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span className="status-badge badge-success" style={{ fontSize: '11px' }}>
                  {drones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('NORTH')).length} Units
                </span>
                
              </div>
            </div>

            {/* East Zone */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--color-border)',
              background: 'var(--color-surface)',
            }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
                  East Corridor (Whitefield / KR Puram)
                </div>
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                  Facility locations: see the confirmed hospital directory
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span className="status-badge badge-success" style={{ fontSize: '11px' }}>
                  {drones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('EAST')).length} Units
                </span>
                
              </div>
            </div>

            {/* South Zone */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--color-border)',
              background: 'var(--color-surface)',
            }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
                  South Corridor (Electronic City / Bommasandra)
                </div>
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                  Facility locations: see the confirmed hospital directory
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span className="status-badge badge-success" style={{ fontSize: '11px' }}>
                  {drones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('SOUTH')).length} Units
                </span>
                
              </div>
            </div>

            {/* West Zone */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--color-border)',
              background: 'var(--color-surface)',
            }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)' }}>
                  West Corridor (Rajajinagar / Mysore Rd)
                </div>
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                  Facility locations: see the confirmed hospital directory
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span className="status-badge badge-success" style={{ fontSize: '11px' }}>
                  {drones.filter(d => (d.zoneName || d.zone?.name || '').toUpperCase().includes('WEST')).length} Units
                </span>
                
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 2-Step Request Creation Modal */}
      <TwoStepRequestModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onRequestCreated={() => {
          setIsModalOpen(false);
          loadData();
        }}
      />
    </AppLayout>
  );
};

export default Dashboard;
