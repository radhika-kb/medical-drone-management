import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { hospitalService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import HospitalPinMap from '../components/map/HospitalPinMap';
import { 
  Building2, 
  Search, 
  PlusCircle, 
  RefreshCw, 
  CheckCircle2, 
  XCircle, 
  MapPin, 
  ShieldCheck, 
  Radio, 
  X,
  ExternalLink
} from 'lucide-react';

export const Hospitals = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN';
  const [hospitals, setHospitals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [zoneFilter, setZoneFilter] = useState('ALL');
  const [verifiedFilter, setVerifiedFilter] = useState('ALL');
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingHospitalId, setEditingHospitalId] = useState(null);
  const [geocodeResults, setGeocodeResults] = useState([]);
  const [geocoding, setGeocoding] = useState(false);
  const [selectedLocation, setSelectedLocation] = useState(null);
  const [pinConfirmed, setPinConfirmed] = useState(false);
  const [newHospital, setNewHospital] = useState({
    name: '',
    type: 'MULTI_SPECIALTY',
    address: '',
  });

  const loadHospitals = async () => {
    try {
      setLoading(true);
      const data = await hospitalService.getAll(false);
      setHospitals(data || []);
      setLoadError('');
    } catch (e) {
      setHospitals([]);
      setLoadError('Hospital records could not be loaded. Check that the backend and database are available.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHospitals();
  }, []);

  const handleToggleVerify = async (id, currentStatus) => {
    try {
      const nextStatus = currentStatus === 'APPROVED' ? 'SUSPENDED' : 'APPROVED';
      const updated = await hospitalService.verify(id, nextStatus);
      setHospitals(prev => prev.map(h => h.id === id ? updated : h));
    } catch (e) {
      setLoadError(e.response?.data?.message || 'Hospital verification could not be updated.');
    }
  };

  const handleAddHospital = async (e) => {
    e.preventDefault();
    if (!selectedLocation || !pinConfirmed) {
      setLoadError('Select a geocoding result, confirm its map pin, then save the hospital.');
      return;
    }
    try {
      const payload = {
        ...newHospital,
        latitude: selectedLocation.latitude,
        longitude: selectedLocation.longitude,
        locationConfirmed: true,
      };
      const saved = editingHospitalId
        ? await hospitalService.updateLocation(editingHospitalId, payload)
        : await hospitalService.create(payload);
      setHospitals(prev => editingHospitalId
        ? prev.map(h => h.id === editingHospitalId ? saved : h)
        : [...prev, saved]);
      setShowAddModal(false);
      setNewHospital({ name: '', type: 'MULTI_SPECIALTY', address: '' });
      setEditingHospitalId(null);
      setGeocodeResults([]);
      setSelectedLocation(null);
      setPinConfirmed(false);
    } catch (e) {
      setLoadError(e.response?.data?.message || 'Facility could not be submitted for verification.');
    }
  };

  const geocodeNewHospital = async () => {
    if (!newHospital.address.trim()) {
      setLoadError('Enter a Bengaluru address before locating it.');
      return;
    }
    setGeocoding(true);
    setLoadError('');
    setSelectedLocation(null);
    setPinConfirmed(false);
    try {
      const results = await hospitalService.geocode(newHospital.address);
      setGeocodeResults(results || []);
      if (!results?.length) setLoadError('No Bengaluru location matched that address. Add a street, area, or PIN code and try again.');
    } catch (e) {
      setLoadError(e.response?.data?.message || 'Address lookup is temporarily unavailable. Please try again.');
    } finally {
      setGeocoding(false);
    }
  };

  const openLocationForm = (hospital = null) => {
    setEditingHospitalId(hospital?.id ?? null);
    setNewHospital({
      name: hospital?.name || '',
      type: hospital?.type || 'MULTI_SPECIALTY',
      address: hospital?.address || '',
    });
    setGeocodeResults([]);
    setSelectedLocation(null);
    setPinConfirmed(false);
    setLoadError('');
    setShowAddModal(true);
  };

  const moveSelectedPin = async (latitude, longitude) => {
    setSelectedLocation(current => ({ ...current, latitude, longitude, zoneId: null, zoneName: '' }));
    setPinConfirmed(false);
    try {
      const zone = await hospitalService.resolveZone(latitude, longitude);
      setSelectedLocation(current => current && current.latitude === latitude && current.longitude === longitude
        ? { ...current, zoneId: zone.id, zoneName: zone.name }
        : current);
    } catch {
      // The save endpoint performs the authoritative zone resolution.
    }
  };

  const filteredHospitals = hospitals.filter(h => {
    const zone = (h.zoneName || '').replace('_ZONE', '').toLowerCase();
    const matchesSearch = 
      h.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      h.address.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesZone = zoneFilter === 'ALL' || zone === zoneFilter.toLowerCase();
    const matchesVerified = 
      verifiedFilter === 'ALL' || 
      (verifiedFilter === 'VERIFIED' && h.verificationStatus === 'APPROVED') || 
      (verifiedFilter === 'PENDING' && h.verificationStatus === 'PENDING');

    return matchesSearch && matchesZone && matchesVerified;
  });

  return (
    <AppLayout
      pageTitle="HOSPITAL DIRECTORY"
      breadcrumb="Central Operations â€¢ Logistics â€¢ Verified Medical Centers"
    >
      {/* Top Header */}
      {loadError && <div role="alert" className="card" style={{ color: 'var(--color-error)', marginBottom: 16 }}>{loadError}</div>}
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
            Bengaluru Hospital Directory
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Bengaluru hospital directory with locations confirmed by the application's administrator workflow
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={loadHospitals} className="btn btn-secondary">
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Refresh</span>
          </button>
          {isAdmin && <button onClick={() => openLocationForm()} className="btn btn-primary">
            <PlusCircle size={16} />
            <span>Register Facility</span>
          </button>}
        </div>
      </div>

      {/* 4 Zones Summary Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '16px',
        marginBottom: '20px',
      }}>
        {['North', 'East', 'South', 'West'].map((zone) => {
          const zoneHospitals = hospitals.filter(h => (h.zoneName || '').replace('_ZONE', '').toLowerCase() === zone.toLowerCase());
          const verifiedCount = zoneHospitals.filter(h => h.verificationStatus === 'APPROVED').length;
          return (
            <div key={zone} className="kpi-card">
              <div className="kpi-label">{zone} Bengaluru Zone</div>
              <div className="kpi-value" style={{ color: 'var(--color-primary-navy)' }}>
                {zoneHospitals.length} <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)' }}>Facilities</span>
              </div>
              <div className="kpi-subtext" style={{ color: 'var(--color-success)' }}>
                {verifiedCount} approved facilities
              </div>
            </div>
          );
        })}
      </div>

      {/* Filters and Search */}
      <div className="card" style={{ padding: '16px', marginBottom: '20px' }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(240px, 1.8fr) repeat(2, minmax(160px, 1fr)) auto',
          gap: '12px',
          alignItems: 'center',
        }}>
          <div style={{ position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--color-text-secondary)' }} />
            <input
              type="text"
              placeholder="Search by facility name or address..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="form-control"
              style={{ paddingLeft: '32px' }}
            />
          </div>

          <select
            value={zoneFilter}
            onChange={(e) => setZoneFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Bengaluru Zones</option>
            <option value="North">North Corridor</option>
            <option value="East">East Corridor</option>
            <option value="South">South Corridor</option>
            <option value="West">West Corridor</option>
          </select>

          <select
            value={verifiedFilter}
            onChange={(e) => setVerifiedFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Verification Statuses</option>
            <option value="VERIFIED">Admin-approved Only</option>
            <option value="PENDING">Pending Review Only</option>
          </select>

          {(searchTerm || zoneFilter !== 'ALL' || verifiedFilter !== 'ALL') && (
            <button
              onClick={() => {
                setSearchTerm('');
                setZoneFilter('ALL');
                setVerifiedFilter('ALL');
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
                <th>Medical Facility</th>
                <th>Corridor Zone</th>
                <th>Facility Role</th>
                <th>Address & Coordinates</th>
                <th>Airpad ID</th>
                <th>Verification</th>
                <th style={{ textAlign: 'right' }}>Regulatory Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredHospitals.map((h) => (
                <tr key={h.id}>
                  <td>
                    <div style={{ fontWeight: 700, color: 'var(--color-primary-navy)' }}>
                      {h.name}
                    </div>
                  </td>
                  <td>
                    <span style={{ fontWeight: 600 }}>{(h.zoneName || 'Unassigned').replace('_ZONE', '').replace(/^./, letter => letter.toUpperCase())}</span>
                  </td>
                  <td>
                    <span style={{
                      fontSize: '11px',
                      padding: '2px 8px',
                      borderRadius: '4px',
                      background: 'var(--color-pale-blue)',
                      color: 'var(--color-primary-navy)',
                      fontWeight: 600,
                    }}>
                      {h.type}
                    </span>
                  </td>
                  <td>
                    <div style={{ fontSize: '12px' }}>{h.address}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      {h.latitude != null && h.longitude != null
                        ? `${Number(h.latitude).toFixed(4)}Â° N, ${Number(h.longitude).toFixed(4)}Â° E`
                        : 'Location needs confirmation'}
                    </div>
                  </td>
                  <td>
                    <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: '12px', color: 'var(--color-primary-blue)' }}>
                      {h.airpadCode || 'â€”'}
                    </span>
                  </td>
                  <td>
                    {h.verificationStatus === 'APPROVED' ? (
                      <span className="status-badge badge-success">
                        <CheckCircle2 size={13} />
                        <span>Admin approved</span>
                      </span>
                    ) : h.verificationStatus === 'PENDING' ? (
                      <span className="status-badge badge-warning">
                        <ShieldCheck size={13} />
                        <span>Pending Review</span>
                      </span>
                    ) : (
                      <span className="status-badge badge-warning">{h.verificationStatus || 'Needs review'}</span>
                    )}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    {isAdmin && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6 }}>
                      <button onClick={() => openLocationForm(h)} className="btn btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }}>
                        {h.latitude == null ? 'Set location' : 'Edit location'}
                      </button>
                      <button
                        onClick={() => handleToggleVerify(h.id, h.verificationStatus)}
                        disabled={h.latitude == null || h.longitude == null || !h.zoneId}
                        title={h.latitude == null ? 'Confirm location before approval' : undefined}
                        className={`btn ${h.verificationStatus === 'APPROVED' ? 'btn-secondary' : 'btn-primary'}`}
                        style={{ fontSize: '11px', padding: '4px 10px' }}
                      >
                        {h.verificationStatus === 'APPROVED' ? 'Suspend' : 'Approve facility'}
                      </button>
                    </div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add Hospital Modal */}
      {showAddModal && (
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
            padding: '24px',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ fontSize: '16px', fontWeight: 800, margin: 0, color: 'var(--color-primary-navy)' }}>
                {editingHospitalId ? 'Confirm Hospital Location' : 'Register Hospital Airpad'}
              </h2>
              <button onClick={() => setShowAddModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>

            {loadError && <div role="alert" style={{ color: 'var(--color-error)', marginBottom: 12 }}>{loadError}</div>}
            <form onSubmit={handleAddHospital} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div className="form-group">
                <label className="form-label">Hospital Name</label>
                <input
                  type="text"
                  required
                  value={newHospital.name}
                  onChange={(e) => setNewHospital({ ...newHospital, name: e.target.value })}
                  placeholder="e.g. Sparsh Hospital"
                  className="form-control"
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label className="form-label">Facility Type</label>
                  <select
                    value={newHospital.type}
                    onChange={(e) => setNewHospital({ ...newHospital, type: e.target.value })}
                    className="form-control"
                  >
                    <option value="MULTI_SPECIALTY">Multi Specialty</option>
                    <option value="SUPPLY_HUB">Supply Hub</option>
                    <option value="BLOOD_BANK">Blood Bank</option>
                    <option value="TISSUE_BANK">Tissue Bank</option>
                    <option value="TRAUMA_UNIT">Trauma Unit</option>
                    <option value="EMERGENCY_CLINIC">Emergency Clinic</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Address / Landmark</label>
                <input
                  type="text"
                  required
                  value={newHospital.address}
                  onChange={(e) => {
                    setNewHospital({ ...newHospital, address: e.target.value });
                    setGeocodeResults([]);
                    setSelectedLocation(null);
                    setPinConfirmed(false);
                  }}
                  placeholder="e.g. Infantry Road, Bengaluru"
                  className="form-control"
                />
              </div>

              <button type="button" onClick={geocodeNewHospital} disabled={geocoding} className="btn btn-secondary" style={{ alignSelf: 'flex-start' }}>
                <MapPin size={15} /> {geocoding ? 'Finding address…' : 'Find address on map'}
              </button>

              {geocodeResults.length > 0 && !selectedLocation && <div className="form-group">
                <label className="form-label">Choose the matching address</label>
                <select className="form-control" defaultValue="" onChange={e => {
                  const result = geocodeResults[Number(e.target.value)];
                  if (result) { setSelectedLocation(result); setPinConfirmed(false); }
                }}>
                  <option value="" disabled>Select a Bengaluru match…</option>
                  {geocodeResults.map((result, index) => <option key={`${result.latitude}-${result.longitude}`} value={index}>{result.displayName} — {result.zoneName?.replace('_ZONE', '')}</option>)}
                </select>
              </div>}

              {selectedLocation && <>
                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                  Drag the pin or click the map to adjust it. Zone: <strong>{(selectedLocation.zoneName || 'Resolving…').replace('_ZONE', '')}</strong>.
                  <br />Coordinates: {selectedLocation.latitude.toFixed(5)}, {selectedLocation.longitude.toFixed(5)}
                </div>
                <HospitalPinMap latitude={selectedLocation.latitude} longitude={selectedLocation.longitude} onMove={moveSelectedPin} />
                <small>Map data © OpenStreetMap contributors. Address search by OpenStreetMap Nominatim.</small>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                  <input type="checkbox" checked={pinConfirmed} disabled={!selectedLocation.zoneId} onChange={e => setPinConfirmed(e.target.checked)} />
                  I confirm this map pin marks the hospital location.
                </label>
              </>}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button type="button" onClick={() => setShowAddModal(false)} className="btn btn-secondary">Cancel</button>
                <button type="submit" disabled={!pinConfirmed || !selectedLocation?.zoneId} className="btn btn-primary">
                  {editingHospitalId ? 'Save confirmed location' : 'Confirm location & submit'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AppLayout>
  );
};

export default Hospitals;
