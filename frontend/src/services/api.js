import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT token to all requests if present
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Intercept 401 Unauthorized responses to logout or refresh
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Clear token and redirect to login if session expired
      const isAuthUrl = error.config.url.includes('/auth/');
      if (!isAuthUrl) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Auth endpoints
export const authService = {
  login: async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    return res.data;
  },
  register: async (account) => {
    const res = await api.post('/auth/register', account);
    return res.data;
  },
  getCurrentUser: async () => {
    const res = await api.get('/auth/me');
    return res.data;
  },
};

// Medical Requests endpoints
export const requestService = {
  getAll: async () => {
    const res = await api.get('/medical-requests');
    return res.data;
  },
  getSupplierInbox: async () => {
    const res = await api.get('/medical-requests/supplier/inbox');
    return res.data;
  },
  getById: async (id) => {
    const res = await api.get(`/medical-requests/${id}`);
    return res.data;
  },
  create: async (data) => {
    const res = await api.post('/medical-requests', data);
    return res.data;
  },
  cancel: async (id, reason) => {
    const res = await api.put(`/medical-requests/${id}/cancel`, { reason });
    return res.data;
  },
  confirmDelivery: async (id) => {
    const missions = (await api.get('/missions')).data;
    const mission = (missions || []).find(row => String(row.requestId) === String(id));
    if (!mission) throw new Error('No mission is associated with this request yet.');
    const res = await api.post(`/missions/${mission.id}/confirm-delivery`);
    return res.data;
  },
};

// Medical Centers (Hospitals) endpoints
export const hospitalService = {
  getAll: async (verifiedOnly = false) => {
    const res = await api.get(`/hospitals?verifiedOnly=${verifiedOnly}`);
    return res.data;
  },
  getById: async (id) => {
    const res = await api.get(`/medical-centers/${id}`);
    return res.data;
  },
  create: async (data) => {
    const res = await api.post('/medical-centers', data);
    return res.data;
  },
  geocode: async (address) => {
    const res = await api.post('/medical-centers/geocode', { address });
    return res.data;
  },
  updateLocation: async (id, data) => {
    const res = await api.put(`/medical-centers/${id}/location`, data);
    return res.data;
  },
  resolveZone: async (latitude, longitude) => {
    const res = await api.get(`/zones/resolve?lat=${latitude}&lng=${longitude}`);
    return res.data;
  },
  verify: async (id, status) => {
    const res = await api.put(`/medical-centers/${id}/verify`, { status });
    return res.data;
  },
};

export const mapDataService = {
  getZones: async () => (await api.get('/zones')).data,
  getDrones: async () => (await api.get('/drones')).data,
  getNoFlyZones: async () => (await api.get('/no-fly-zones')).data,
  getAirspaceInformation: async () => (await api.get('/airspace-information')).data,
  getHubs: async () => (await api.get('/hubs')).data,
};

export const weatherService = {
  getCurrent: async (lat = 12.9716, lng = 77.5946) => (await api.get(`/weather/current?lat=${lat}&lng=${lng}`)).data,
};

export const analyticsService = {
  getSummary: async () => (await api.get('/analytics/summary')).data,
};

// Inventory endpoints
export const inventoryService = {
  getByHospital: async (hospitalId) => {
    const res = await api.get(`/inventory/hospital/${hospitalId}`);
    return res.data;
  },
  updateStock: async (hospitalId, itemId, data) => {
    const res = await api.post('/inventory/update', { hospitalId, medicalItemId: itemId, ...data });
    return res.data;
  },
  getAvailable: async (medicalItemId, minQuantity = 1) => {
    const res = await api.get(`/inventory/item/${medicalItemId}?minQuantity=${minQuantity}`);
    return res.data;
  },
};

// Supply Responses endpoints
export const supplyService = {
  getByRequest: async (requestId) => {
    const res = await api.get(`/supply-responses/request/${requestId}`);
    return res.data;
  },
  respond: async (data) => {
    const res = await api.post('/supply-responses/respond', data);
    return res.data;
  },
};

// Missions & Drones endpoints
export const missionService = {
  getAll: async () => {
    const res = await api.get('/missions');
    return (res.data || []).map(row => ({ ...row, droneModel: row.droneModel?.replace(/^Simulated\s+/i, '') }));
  },
  getById: async (id) => {
    const res = await api.get(`/missions/${id}`);
    return res.data;
  },
  prepareReview: async (requestId) => {
    const res = await api.post(`/medical-requests/${requestId}/review`);
    return res.data;
  },
  hubDecision: async (missionId, action, reason = '') => {
    const res = await api.post(`/missions/${missionId}/hub-decision`, { action, reason });
    return res.data;
  },
  reassign: async (missionId, reason) => {
    const res = await api.post(`/missions/${missionId}/reassign`, { reason });
    return res.data;
  },
  delay: async (missionId, reason) => {
    const res = await api.post(`/missions/${missionId}/delay`, { reason });
    return res.data;
  },
  abort: async (missionId, reason) => {
    const res = await api.post(`/missions/${missionId}/abort`, { reason });
    return res.data;
  },
  confirmDelivery: async (missionId) => {
    const res = await api.post(`/missions/${missionId}/confirm-delivery`);
    return res.data;
  },
};

// Fleet (Drones) endpoints
export const droneService = {
  getAll: async () => {
    const res = await api.get('/drones');
    return (res.data || []).map(row => ({ ...row, model: row.model?.replace(/^Simulated\s+/i, '') }));
  },
  getById: async (id) => {
    const res = await api.get(`/drones/${id}`);
    return res.data;
  },
};

// AI & Decision Explanation endpoints
export const aiService = {
  getSupplyMatch: async (payload) => {
    const res = await api.post('/ai/supply-match', payload);
    return res.data;
  },
  getDemandForecast: async (payload) => {
    const res = await api.post('/ai/demand-forecast', payload);
    return res.data;
  },
  getDemandPredictions: async () => {
    const res = await api.get('/ai/demand-predictions');
    return res.data;
  },
  getDemandRiskAlerts: async () => {
    const res = await api.get('/ai/demand-risk-alerts');
    return res.data;
  },
  runDemandForecasts: async () => {
    const res = await api.post('/ai/demand-forecasts/run');
    return res.data;
  },
  getAnomalies: async () => {
    const res = await api.get('/ai/anomalies');
    return res.data;
  },
  getEtaPredictions: async (missionId) => {
    const res = await api.get(`/ai/eta-predictions/mission/${missionId}`);
    return res.data;
  },
  getDecisions: async (missionId) => {
    const res = await api.get(`/ai/decisions/mission/${missionId}`);
    return res.data;
  },
};

export const auditService = {
  getRecent: async (limit = 200) => {
    const res = await api.get('/audit-logs', { params: { limit } });
    return res.data;
  },
};

// Payments endpoints
export const paymentService = {
  createOrder: async (requestId) => {
    const res = await api.post('/payments/create-order', { requestId });
    return res.data;
  },
  verify: async (payload) => {
    const res = await api.post('/payments/verify', payload);
    return res.data;
  },
  getByRequest: async (requestId) => {
    const res = await api.get(`/payments/request/${requestId}`);
    return res.data;
  },
};

// Catalog (Medical Items) endpoints
export const catalogService = {
  getAll: async () => {
    const res = await api.get('/medical-items');
    return res.data;
  },
};

export default api;
