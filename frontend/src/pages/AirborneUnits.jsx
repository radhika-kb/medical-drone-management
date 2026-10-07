import React, { useState, useEffect } from 'react';
import { droneService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import StatusBadge from '../components/common/StatusBadge';
import { 
  Plane, 
  Battery, 
  Cpu, 
  Clock, 
  ShieldCheck, 
  RotateCcw, 
  Wrench, 
  Search, 
  RefreshCw, 
  Sliders,
  CheckCircle2
} from 'lucide-react';

export const AirborneUnits = () => {
  const [drones, setDrones] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const loadDrones = async () => {
    try {
      setLoading(true);
      const data = await droneService.getAll();
      setDrones(data || []);
    } catch (e) {
      console.error('Unable to load airborne units:', e);
      setDrones([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDrones();
  }, []);
const filteredDrones = drones.filter((drone) => {
    const matchesSearch = 
      drone.registrationNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
      drone.model.toLowerCase().includes(searchTerm.toLowerCase()) ||
      drone.assignedZone.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || drone.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const availableCount = drones.filter(d => d.status === 'AVAILABLE').length;
  const inFlightCount = drones.filter(d => d.status === 'IN_FLIGHT').length;
  const chargingCount = drones.filter(d => d.status === 'CHARGING').length;
  const maintenanceCount = drones.filter(d => d.status === 'MAINTENANCE').length;

  return (
    <AppLayout
      pageTitle="AIRBORNE UNITS"
      breadcrumb="Central Operations • Fleet Resource Allocation • Drones"
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
            Fleet Unit Records
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Unit status, battery values, and mission telemetry
          </p>
        </div>

        <button onClick={loadDrones} className="btn btn-secondary">
          <RefreshCw size={15} className={loading ? 'spin' : ''} />
          <span>Sync Fleet Telemetry</span>
        </button>
      </div>

      {/* Fleet Metrics Strip */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(5, 1fr)',
        gap: '16px',
        marginBottom: '20px',
      }}>
        <div className="kpi-card">
          <div className="kpi-label">Total Fleet</div>
          <div className="kpi-value" style={{ color: 'var(--color-primary-navy)' }}>
            {drones.length}
          </div>
          <div className="kpi-subtext">Unit records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Available / Standby</div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            {availableCount}
          </div>
          <div className="kpi-subtext">Available unit records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Active Mission State</div>
          <div className="kpi-value" style={{ color: 'var(--color-primary-blue)' }}>
            {inFlightCount}
          </div>
          <div className="kpi-subtext">Mission telemetry records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Charging Bays</div>
          <div className="kpi-value" style={{ color: 'var(--color-warning)' }}>
            {chargingCount}
          </div>
          <div className="kpi-subtext">Status from unit records</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Maintenance</div>
          <div className="kpi-value" style={{ color: 'var(--color-error)' }}>
            {maintenanceCount}
          </div>
          <div className="kpi-subtext">Status from unit records</div>
        </div>
      </div>

      {/* Filters */}
      <div className="card" style={{ padding: '16px', marginBottom: '20px' }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(240px, 1.8fr) minmax(180px, 1fr) auto',
          gap: '12px',
          alignItems: 'center',
        }}>
          <div style={{ position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--color-text-secondary)' }} />
            <input
              type="text"
              placeholder="Search by registration number, model, or base..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="form-control"
              style={{ paddingLeft: '32px' }}
            />
          </div>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Fleet Statuses</option>
            <option value="AVAILABLE">Available / Standby</option>
            <option value="IN_FLIGHT">Active state</option>
            <option value="CHARGING">Charging</option>
            <option value="MAINTENANCE">Maintenance</option>
          </select>

          {(searchTerm || statusFilter !== 'ALL') && (
            <button
              onClick={() => {
                setSearchTerm('');
                setStatusFilter('ALL');
              }}
              className="btn btn-secondary"
            >
              Reset
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Registration</th>
                <th>Airframe Specification</th>
                <th>Station Base</th>
                <th>Battery SoC</th>
                <th>Payload Rating</th>
                <th>Flight Hours</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredDrones.map((drone) => (
                <tr key={drone.id}>
                  <td>
                    <div style={{ fontWeight: 800, color: 'var(--color-primary-navy)', fontFamily: 'monospace' }}>
                      {drone.registrationNumber}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{drone.model}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      {drone.hasColdChainPod ? '❄️ Active Cold Pod (2-8°C)' : '📦 Ambient Medical Cargo'}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontSize: '12px', fontWeight: 500 }}>{drone.assignedZone}</div>
                  </td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <div style={{
                        flex: 1,
                        height: '6px',
                        borderRadius: '3px',
                        background: '#E2E8F0',
                        overflow: 'hidden',
                        minWidth: '50px',
                      }}>
                        <div style={{
                          height: '100%',
                          width: `${drone.batteryLevel}%`,
                          background: drone.batteryLevel > 70 ? 'var(--color-success)' : drone.batteryLevel > 30 ? 'var(--color-warning)' : 'var(--color-error)',
                        }} />
                      </div>
                      <strong style={{ fontSize: '12px' }}>{drone.batteryLevel}%</strong>
                    </div>
                  </td>
                  <td>
                    <span style={{ fontWeight: 600 }}>{drone.payloadCapacityKg} kg</span>
                  </td>
                  <td>
                    <div style={{ fontSize: '12px' }}>{drone.totalFlightHours} hrs</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{drone.completedMissions} mission records</div>
                  </td>
                  <td>
                    <StatusBadge status={drone.status} size="sm" />
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      onClick={() => alert(`Diagnostics for ${drone.registrationNumber}: Avionics nominal, compass calibrated, battery cycle count: 182.`)}
                      className="btn btn-secondary"
                      style={{ fontSize: '11px', padding: '4px 10px' }}
                    >
                      <Cpu size={12} />
                      <span>Diagnostics</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppLayout>
  );
};

export default AirborneUnits;
