import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export default function HospitalPinMap({ latitude, longitude, onMove }) {
  const elementRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const onMoveRef = useRef(onMove);
  onMoveRef.current = onMove;

  useEffect(() => {
    if (!elementRef.current || mapRef.current || latitude == null || longitude == null) return;
    const map = L.map(elementRef.current).setView([latitude, longitude], 15);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);
    const marker = L.marker([latitude, longitude], {
      draggable: true,
      icon: L.divIcon({ className: 'hospital-confirmation-pin', html: '<span>📍</span>', iconSize: [30, 30], iconAnchor: [15, 28] }),
    }).addTo(map);
    marker.on('dragend', () => {
      const point = marker.getLatLng();
      onMoveRef.current(point.lat, point.lng);
    });
    map.on('click', event => {
      marker.setLatLng(event.latlng);
      onMoveRef.current(event.latlng.lat, event.latlng.lng);
    });
    mapRef.current = map;
    markerRef.current = marker;
    const resizeTimer = window.setTimeout(() => map.invalidateSize(), 100);
    return () => {
      window.clearTimeout(resizeTimer);
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
  }, [latitude == null || longitude == null]);

  useEffect(() => {
    if (mapRef.current && markerRef.current && latitude != null && longitude != null) {
      const point = [latitude, longitude];
      markerRef.current.setLatLng(point);
      mapRef.current.setView(point, mapRef.current.getZoom());
    }
  }, [latitude, longitude]);

  return <div ref={elementRef} role="application" aria-label="Confirm hospital location on map" style={{ height: 280, width: '100%', borderRadius: 8, zIndex: 0 }} />;
}
