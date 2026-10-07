import React, { useEffect, useState } from 'react';
import { weatherService } from '../../services/api';

export default function CurrentWeatherCard() {
  const [weather, setWeather] = useState(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    const refresh = async () => {
      try {
        const response = await weatherService.getCurrent();
        if (!active) return;
        const liveData = response?.status === 'LIVE' && response.data?.observations?.length ? response.data : null;
        setWeather(liveData);
        setUnavailable(!liveData);
      } catch {
        if (active) { setWeather(null); setUnavailable(true); }
      }
    };
    refresh();
    const timer = window.setInterval(refresh, 10 * 60 * 1000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  const observation = weather?.observations?.[0];
  return (
    <section className="card" aria-live="polite" style={{ padding: '14px 18px', marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <strong style={{ color: 'var(--color-primary-navy)' }}>Bengaluru Current Weather</strong>
          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 3 }}>
            {observation ? `${observation.condition} · observed ${observation.observed_at} · ${observation.source || 'Open-Meteo'}` : unavailable ? 'Weather unavailable' : 'Loading current observations…'}
          </div>
        </div>
        {observation && <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', fontSize: 13 }}>
          <span>{observation.temperature_c ?? '—'} °C</span>
          <span>Wind {observation.wind_speed_kmh ?? '—'} km/h</span>
          <span>Visibility {observation.visibility_km ?? '—'} km</span>
          <span>Weather ML: {observation.flight_condition || 'Unavailable'}</span>
        </div>}
      </div>
      {weather?.reason && <div style={{ color: 'var(--color-text-secondary)', fontSize: 11, marginTop: 5 }}>{weather.reason}</div>}
    </section>
  );
}
