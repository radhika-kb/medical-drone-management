import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { hospitalService, mapDataService } from '../../services/api';
import { hasConfirmedHospitalLocation } from '../../utils/confirmedHospitalLocation';

// Fix Leaflet default marker icons for Vite bundler
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Custom Clean SVG Markers
const createSvgIcon = (color, isHub = false) => {
  const size = isHub ? 32 : 24;
  const svg = isHub
    ? `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="${color}" stroke="#FFFFFF" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>`
    : `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="${color}" stroke="#FFFFFF" stroke-width="1.5"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"/><circle cx="12" cy="9" r="2.5" fill="#FFFFFF"/></svg>`;

  return L.divIcon({
    className: 'custom-leaflet-icon',
    html: `<div style="display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 1px 3px rgba(18,59,93,0.3));">${svg}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, isHub ? size / 2 : size],
    popupAnchor: [0, -size / 2],
  });
};

const createDroneIcon = (heading = 0, callsign = 'V-04') => {
  return L.divIcon({
    className: 'drone-leaflet-icon',
    html: `
      <div style="display:flex; flex-direction:column; align-items:center;">
        <div style="transform: rotate(${heading}deg); width:30px; height:30px; background:#123B5D; border:2px solid #FFFFFF; border-radius:50%; display:flex; align-items:center; justify-content:center; box-shadow:0 2px 6px rgba(18,59,93,0.4);">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="12 2 19 21 12 17 5 21 12 2"/>
          </svg>
        </div>
        <span style="font-family:Inter,sans-serif; font-size:10px; font-weight:700; background:#FFFFFF; color:#123B5D; padding:1px 4px; border-radius:3px; border:1px solid #DDE3EA; margin-top:2px; box-shadow:0 1px 2px rgba(0,0,0,0.1);">
          ${callsign}
        </span>
      </div>
    `,
    iconSize: [34, 46],
    iconAnchor: [17, 15],
    popupAnchor: [0, -18],
  });
};

export const BengaluruRadarMap = ({
  height = '480px',
  activeMissions = [],
  selectedZone = 'ALL',
  onSelectZone = null,
}) => {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const flightLayerGroupRef = useRef(null);
  const hospitalLayerGroupRef = useRef(null);
  const environmentLayerGroupRef = useRef(null);
  const droneLayerGroupRef = useRef(null);
  const [internalZone, setInternalZone] = useState(selectedZone || 'ALL');
  const [hospitalRegistry, setHospitalRegistry] = useState([]);
  const [zones, setZones] = useState([]);
  const [airspaceInformation, setAirspaceInformation] = useState([]);
  const [airborneUnits, setAirborneUnits] = useState([]);

  const currentZone = onSelectZone ? selectedZone : internalZone;
  const setZone = onSelectZone || setInternalZone;

  useEffect(() => {
    Promise.all([
      hospitalService.getAll(true),
      mapDataService.getZones(),
      mapDataService.getDrones(),
      mapDataService.getAirspaceInformation(),
    ]).then(([hospitals, zoneData, droneData, nfzData]) => {
      setHospitalRegistry(hospitals || []);
      setZones((zoneData || []).filter(zone => zone.active));
      setAirborneUnits(droneData || []);
      setAirspaceInformation(nfzData || []);
    }).catch(() => {
      setHospitalRegistry([]);
      setZones([]);
      setAirborneUnits([]);
      setAirspaceInformation([]);
    });
  }, []);

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Centered on Bengaluru
    const map = L.map(mapContainerRef.current, {
      center: [12.9716, 77.5946],
      zoom: 11,
      minZoom: 10,
      maxZoom: 16,
      zoomControl: true,
    });
    mapInstanceRef.current = map;

    // OpenStreetMap raster tiles do not require an API key. Keep the endpoint
    // configurable so deployments can use a tile provider with an SLA.
    L.tileLayer(import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
      maxZoom: 19,
    }).addTo(map);

    // These layers are populated from Spring Boot after the map initializes.
    environmentLayerGroupRef.current = L.layerGroup().addTo(map);
    droneLayerGroupRef.current = L.layerGroup().addTo(map);

    // Groups for dynamic updates
    const hospGroup = L.layerGroup().addTo(map);
    hospitalLayerGroupRef.current = hospGroup;

    const flightGroup = L.layerGroup().addTo(map);
    flightLayerGroupRef.current = flightGroup;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
      hospitalLayerGroupRef.current = null;
      flightLayerGroupRef.current = null;
      environmentLayerGroupRef.current = null;
      droneLayerGroupRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!mapInstanceRef.current || !environmentLayerGroupRef.current) return;
    const layer = environmentLayerGroupRef.current;
    layer.clearLayers();

    zones.forEach(zone => {
      if (zone.centerLat == null || zone.centerLng == null || zone.radiusKm == null) return;
      const color = zone.name?.includes('NORTH') ? '#005EB8' : zone.name?.includes('EAST') ? '#7C2855' : zone.name?.includes('SOUTH') ? '#007F3B' : '#D4351C';
      L.circle([zone.centerLat, zone.centerLng], { radius: zone.radiusKm * 1000, color, weight: 1.5, fillColor: color, fillOpacity: 0.035 })
        .bindTooltip(`<b>${zone.name}</b><br/>${zone.description || ''}`).addTo(layer);
    });

  }, [zones]);

  useEffect(() => {
    if (!mapInstanceRef.current || !droneLayerGroupRef.current) return;
    const layer = droneLayerGroupRef.current;
    layer.clearLayers();
    airborneUnits.forEach(drone => {
      if (drone.latitude == null || drone.longitude == null) return;
      L.marker([drone.latitude, drone.longitude], { icon: createDroneIcon(0, drone.droneCode || `Unit ${drone.id}`) })
        .bindPopup(`<b>${drone.droneCode}</b><br/>${drone.model?.replace(/^Simulated\s+/i, '') || 'Airborne unit'}<br/>Status: ${drone.status}<br/>Battery: ${drone.batteryPct ?? '—'}%`)
        .addTo(layer);
    });
  }, [airborneUnits]);

  // Update Hospital Pins based on Selected Zone filter
  useEffect(() => {
    if (!mapInstanceRef.current || !hospitalLayerGroupRef.current) return;
    const hospGroup = hospitalLayerGroupRef.current;
    hospGroup.clearLayers();

    const confirmedHospitals = hospitalRegistry.filter(hasConfirmedHospitalLocation);
    const filteredHospitals = currentZone === 'ALL'
      ? confirmedHospitals
      : confirmedHospitals.filter(h => (h.zoneName || '').toUpperCase().includes(currentZone.toUpperCase()));

    filteredHospitals.forEach((hosp) => {
      if (hosp.latitude == null || hosp.longitude == null) return;
      const marker = L.marker([hosp.latitude, hosp.longitude], { icon: createSvgIcon('#16803C', false) }).addTo(hospGroup);

      marker.bindPopup(`
        <div style="font-family:Inter,sans-serif;font-size:12px;min-width:180px;">
          <div style="font-weight:700;color:#17212B;font-size:13px;">${hosp.name}</div>
          <div style="color:#667085;margin-top:2px;">Sector: <b>${hosp.zoneName || 'Unassigned'}</b></div>
          <div style="color:#667085;font-size:11px;">${hosp.address}</div>
        </div>
      `);
    });

    // Zoom to zone if specific zone selected
    if (currentZone !== 'ALL' && mapInstanceRef.current) {
      const targetZone = zones.find(z => z.name.includes(currentZone.toUpperCase()));
      if (targetZone && targetZone.centerLat != null && targetZone.centerLng != null) {
        const bounds = L.circle([targetZone.centerLat, targetZone.centerLng], { radius: (targetZone.radiusKm || 18) * 1000 }).getBounds();
        mapInstanceRef.current.flyToBounds(bounds, { padding: [20, 20], duration: 0.8 });
      }
    }
  }, [currentZone, hospitalRegistry, zones]);

  // Update Active Flights & Routes (Section 6 & 13)
  useEffect(() => {
    if (!mapInstanceRef.current || !flightLayerGroupRef.current) return;
    const flightGroup = flightLayerGroupRef.current;
    flightGroup.clearLayers();

    const missionsToRender = activeMissions || [];

    missionsToRender.forEach((m) => {
      if (['COMPLETED', 'DELIVERED', 'CANCELLED'].includes(m.status)) return;

      const plannedRoute = Array.isArray(m.waypoints)
        ? [...m.waypoints].sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))
          .filter(point => point.lat != null && point.lng != null).map(point => [point.lat, point.lng])
        : [];
      if (plannedRoute.length > 1) {
        L.polyline(plannedRoute, {
          color: '#2F6FED',
          weight: 2.5,
          opacity: 0.8,
          dashArray: '5, 8',
        }).addTo(flightGroup);
      }

      if (m.currentLat == null || m.currentLng == null) return;
      // 2. Airborne Unit Live Marker
      const droneMarker = L.marker([m.currentLat, m.currentLng], {
        icon: createDroneIcon(m.heading ?? 0, m.callSign || m.droneCode || `Mission ${m.id}`),
        zIndexOffset: 1000,
      }).addTo(flightGroup);

      // 3. Factual Telemetry Popup (Section 12 & 13)
      droneMarker.bindPopup(`
        <div style="font-family:Inter,sans-serif;font-size:12px;min-width:210px;line-height:1.4;">
          <div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #DDE3EA;padding-bottom:4px;margin-bottom:6px;">
            <span style="font-weight:700;color:#123B5D;">${m.callSign || m.droneCode || 'Airborne unit'} â€¢ ${m.missionNumber || m.missionCode || `Mission ${m.id}`}</span>
            <span style="background:#EAF3F8;color:#123B5D;font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600;">${m.status || 'STATUS UNKNOWN'}</span>
          </div>

          <div style="display:grid;grid-template-columns:1fr 1fr;gap:4px;margin-bottom:6px;font-size:11px;">
            <div style="background:#F8FAFC;padding:4px 6px;border-radius:4px;">
              <span style="color:#667085;">Altitude:</span> <b>${m.altitudeMeters ?? "ï¿½"}${m.altitudeMeters != null ? " m" : ""}</b>
            </div>
            <div style="background:#F8FAFC;padding:4px 6px;border-radius:4px;">
              <span style="color:#667085;">Speed:</span> <b>${m.speedKmh ?? "ï¿½"}${m.speedKmh != null ? " km/h" : ""}</b>
            </div>
            <div style="background:#F8FAFC;padding:4px 6px;border-radius:4px;">
              <span style="color:#667085;">Battery:</span> <b style="color:#16803C;">${m.batteryPct ?? "ï¿½"}${m.batteryPct != null ? "%" : ""}</b>
            </div>
            <div style="background:#F8FAFC;padding:4px 6px;border-radius:4px;">
              <span style="color:#667085;">Heading:</span> <b>${m.heading ?? "ï¿½"}${m.heading != null ? "ï¿½" : ""}</b>
            </div>
          </div>

          <div style="font-size:11px;color:#17212B;border-top:1px solid #DDE3EA;padding-top:4px;">
            Route: <b>${m.pickup?.name || 'Origin'}</b> âž” <b>${m.dropoff?.name || 'Receiver'}</b>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:4px;font-size:11px;">
            <span style="color:#667085;">ETA: <b>${m.etaMinutes ?? "ï¿½"}${m.etaMinutes != null ? " min" : ""}</b></span>
            <span style="color:#667085;font-weight:600;">Route: Planned</span>
          </div>
        </div>
      `);
    });
  }, [activeMissions]);

  return (
    <div style={{ position: 'relative', width: '100%', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--color-border)', background: '#FFFFFF' }}>
      {/* Map Header & Zone Filter Controls (Sections 6 & 7) */}
      <div style={{
        padding: '12px 16px',
        borderBottom: '1px solid var(--color-border-subtle)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: '#FFFFFF',
        zIndex: 500,
        position: 'relative',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--color-primary-navy)' }}>
            Live Operations
          </span>
          <span style={{ fontSize: '11px', color: 'var(--color-success)', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--color-success)' }} />
            Software-managed tracking
          </span>
        </div>

        {/* 4-Zone Operational Filter Tabs */}
        <div className="zone-tabs">
          {['ALL', 'NORTH', 'EAST', 'SOUTH', 'WEST'].map((z) => (
            <button
              key={z}
              onClick={() => setZone(z)}
              className={`zone-tab ${currentZone.toUpperCase() === z ? 'active' : ''}`}
            >
              {z === 'ALL' ? 'All' : z.charAt(0) + z.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      {/* Map Canvas */}
      <div ref={mapContainerRef} style={{ width: '100%', height }} />

      {/* Map Legend Overlay (Section 6.2) */}
      <div style={{
        position: 'absolute',
        bottom: '12px',
        left: '12px',
        zIndex: 900,
        background: 'rgba(255, 255, 255, 0.95)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-sm)',
        padding: '8px 12px',
        fontSize: '11px',
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
        boxShadow: 'var(--shadow-subtle)',
      }}>
        <div style={{ fontWeight: 700, color: 'var(--color-primary-navy)', marginBottom: '2px' }}>
          Map Legend
        </div>
        {airspaceInformation.map(item => <div key={item.name} style={{ fontSize: 10, maxWidth: 245 }}>
          <strong>{item.name}</strong><br />{item.classificationDetail}<br />{item.boundaryDescription}<br />{item.currentStatus}
        </div>)}
        <div style={{ fontSize: 10, color: 'var(--color-error)' }}>DigitalSky UAS zone status: unavailable</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#123B5D', display: 'inline-block' }} />
          <span>Unit records</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#2F6FED', display: 'inline-block' }} />
          <span>Hospitals</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#16803C', display: 'inline-block' }} />
          <span>Mission routes</span>
        </div>
      </div>
    </div>
  );
};

export default BengaluruRadarMap;
