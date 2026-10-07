import CurrentWeatherCard from '../../components/common/CurrentWeatherCard';
import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { requestService, paymentService, catalogService } from '../../services/api';
import { wsService } from '../../services/websocket';
import StatusBadge from '../../components/common/StatusBadge';
import ExplainableAIPanel from '../../components/common/ExplainableAIPanel';
import { 
  PlusCircle, 
  Package, 
  Send, 
  CheckCircle2, 
  AlertTriangle, 
  CreditCard, 
  Radio, 
  ShieldCheck,
  RefreshCw,
  ExternalLink,
  X
} from 'lucide-react';

export const RequesterDashboard = () => {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedRequestForPayment, setSelectedRequestForPayment] = useState(null);
  const [paymentProcessing, setPaymentProcessing] = useState(false);

  // Form State
  const [catalogItems, setCatalogItems] = useState([]);

  const [formData, setFormData] = useState({
    medicalItemId: '',
    quantity: 2,
    priority: 'STAT_EMERGENCY',
    destinationHospitalId: user?.hospitalId || '',
    destinationName: user?.hospitalName || 'No facility assigned',
    zone: '',
    clinicalNotes: '',
  });

  // Load existing requests
  const loadRequests = async () => {
    try {
      setLoading(true);
      const data = await requestService.getAll();
      setRequests(data || []);
    } catch (err) {
      console.warn('Unable to load requests:', err);
      setRequests([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    catalogService.getAll().then(items => {
      const rows = (items || []).filter(item => item.active !== false);
      setCatalogItems(rows);
      if (rows.length) setFormData(current => ({ ...current, medicalItemId: rows[0].id }));
    }).catch(() => setCatalogItems([]));
    loadRequests();

    // Listen to real-time status updates via STOMP
    const unsub = wsService.subscribe('/topic/requests', (updatedReq) => {
      setRequests((prev) =>
        prev.map((r) => (r.id === updatedReq.id ? { ...r, ...updatedReq } : r))
      );
    });

    return () => unsub();
  }, []);

  // Submit new request
  const handleCreateRequest = async (e) => {
    e.preventDefault();
    try {
      const selectedItem = catalogItems.find(i => i.id === Number(formData.medicalItemId));
      const payload = {
        medicalItemId: Number(formData.medicalItemId),
        itemName: selectedItem?.name,
        quantity: Number(formData.quantity),
        priority: formData.priority,
        requestingHospitalId: user?.hospitalId,
        clinicalNotes: formData.clinicalNotes,
        zone: formData.zone,
      };

      const created = await requestService.create(payload);
      setRequests([created, ...requests]);
      setShowCreateModal(false);
    } catch (err) {
      console.error('Request creation failed:', err);
      window.alert(err.response?.data?.message || 'The backend did not create this request. No local request was added.');
    }
  };

  // Start checkout from a server-created order; only backend verification updates request state.
  const handlePayment = async () => {
    if (!selectedRequestForPayment) return;
    setPaymentProcessing(true);
    try {
      const order = await paymentService.createOrder(selectedRequestForPayment.id);
      if (!order.razorpayKeyId || !order.gatewayOrderId) throw new Error('Razorpay keys are not configured on the backend.');
      if (!window.Razorpay) {
        await new Promise((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://checkout.razorpay.com/v1/checkout.js';
          script.async = true;
          script.onload = resolve;
          script.onerror = () => reject(new Error('Razorpay Checkout could not be loaded.'));
          document.body.appendChild(script);
        });
      }
      const checkout = new window.Razorpay({
        key: order.razorpayKeyId,
        amount: order.amountInPaise,
        currency: order.currency,
        name: 'Bengaluru Medical Drone Network',
        description: `Medical delivery ${selectedRequestForPayment.requestCode}`,
        order_id: order.gatewayOrderId,
        handler: async (response) => {
          try {
            const verified = await paymentService.verify({
              paymentReference: order.paymentReference,
              gatewayOrderId: response.razorpay_order_id,
              gatewayPaymentId: response.razorpay_payment_id,
              gatewaySignature: response.razorpay_signature,
            });
            if (verified.paymentStatus !== 'SUCCESS') throw new Error('Payment has not been confirmed by the backend.');
            setRequests(prev => prev.map(request => request.id === selectedRequestForPayment.id
              ? { ...request, paid: true, status: 'READY_FOR_DISPATCH' } : request));
            setShowPaymentModal(false);
          } catch (error) {
            window.alert(error.response?.data?.message || error.message || 'Backend payment verification failed.');
          } finally {
            setPaymentProcessing(false);
          }
        },
        modal: { ondismiss: () => setPaymentProcessing(false) },
        theme: { color: '#123B5D' },
      });
      checkout.on('payment.failed', (event) => {
        setPaymentProcessing(false);
        window.alert(event.error?.description || 'Razorpay reported that payment did not complete.');
      });
      checkout.open();
    } catch (error) {
      setPaymentProcessing(false);
      window.alert(error.response?.data?.message || error.message || 'Could not start Razorpay checkout.');
    }
  };

  // Confirm delivery receipt
  const handleConfirmDelivery = async (reqId) => {
    try {
      await requestService.confirmDelivery(reqId);
      setRequests(prev => prev.map(r => r.id === reqId ? { ...r, status: 'DELIVERY_CONFIRMED' } : r));
    } catch (e) {
      window.alert(e.response?.data?.message || e.message || 'The backend did not confirm delivery.');
    }
  };

  return (
    <div>
      <CurrentWeatherCard />
      {/* Top Action Bar & Title */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
            Hospital Medical Requests
          </h1>
          <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            {user?.hospitalName || 'Your registered facility'} · Medical Requests
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button onClick={loadRequests} className="btn btn-secondary" title="Refresh Requests">
            <RefreshCw size={16} />
            <span>Refresh</span>
          </button>
          <button 
            onClick={() => setShowCreateModal(true)} 
            className="btn btn-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <PlusCircle size={18} />
            <span>New Emergency Request</span>
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Active Requests</span>
            <Package size={18} color="var(--color-nhs-blue)" />
          </div>
          <div className="kpi-value">{requests.filter(r => r.status !== 'DELIVERY_CONFIRMED' && r.status !== 'CANCELLED').length}</div>
          <div className="kpi-subtext">Across Bengaluru corridors</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Active Request Records</span>
            <Send size={18} color="var(--color-nhs-aqua)" />
          </div>
          <div className="kpi-value">{requests.filter(r => r.status === 'IN_TRANSIT' || r.status === 'DISPATCHED').length}</div>
          <div className="kpi-subtext">Live drone telemetry linked</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Confirmed Received</span>
            <CheckCircle2 size={18} color="var(--color-nhs-green)" />
          </div>
          <div className="kpi-value">{requests.filter(r => r.status === 'DELIVERY_CONFIRMED').length}</div>
          <div className="kpi-subtext">Cold-chain custody logged</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">STAT Emergency Priority</span>
            <AlertTriangle size={18} color="var(--color-nhs-red)" />
          </div>
          <div className="kpi-value">{requests.filter(r => r.priority === 'STAT_EMERGENCY').length}</div>
          <div className="kpi-subtext">30-min SLA threshold</div>
        </div>
      </div>

      {/* Requests Table */}
      <div className="data-table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Request ID</th>
              <th>Medical Payload</th>
              <th>Quantity</th>
              <th>Priority</th>
              <th>Supplier Matched</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {requests.map((req) => (
              <React.Fragment key={req.id}>
                <tr>
                  <td>
                    <div style={{ fontWeight: 600, color: 'var(--color-text)' }}>
                      {req.requestCode || `REQ-${req.id}`}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
                      {new Date(req.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{req.medicalItemName || 'Critical Blood/Serum'}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Cold Chain: 2°C - 6°C</div>
                  </td>
                  <td>
                    <span style={{ fontWeight: 700, fontSize: '0.9375rem' }}>{req.quantity}</span> units
                  </td>
                  <td>
                    <StatusBadge status={req.priority} size="small" />
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{req.supplyingHospitalName || 'Auto-Matching via AI...'}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>Zone: South (Bommasandra)</div>
                  </td>
                  <td>
                    <StatusBadge status={req.status} />
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                      {/* Payment Action */}
                      {req.status === 'SUPPLIER_ACCEPTED' && !req.paid && (
                        <button
                          onClick={() => {
                            setSelectedRequestForPayment(req);
                            setShowPaymentModal(true);
                          }}
                          className="btn btn-primary"
                          style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem' }}
                        >
                          <CreditCard size={14} />
                          <span>Pay Logistics Fee</span>
                        </button>
                      )}

                      {/* Confirm Receipt Action */}
                      {(req.status === 'IN_TRANSIT' || req.status === 'DELIVERED') && (
                        <button
                          onClick={() => handleConfirmDelivery(req.id)}
                          className="btn btn-secondary"
                          style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem', color: 'var(--color-nhs-green)' }}
                        >
                          <CheckCircle2 size={14} />
                          <span>Confirm Received</span>
                        </button>
                      )}

                      {req.status === 'DELIVERY_CONFIRMED' && (
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-nhs-green)', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                          <CheckCircle2 size={14} /> Closed
                        </span>
                      )}
                    </div>
                  </td>
                </tr>

                {/* Inline Explainable AI Panel */}
                <tr>
                  <td colSpan="7" style={{ padding: '0 1.25rem 1rem', background: '#FAFAFA' }}>
                    <ExplainableAIPanel requestId={req.id} />
                  </td>
                </tr>
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>

      {/* Modal: New Medical Request */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="modal-content" style={{ maxWidth: '580px' }}>
            <div className="modal-header">
              <h2 className="modal-title">Initiate Autonomous Medical Drone Request</h2>
              <button onClick={() => setShowCreateModal(false)} className="btn btn-secondary" style={{ padding: '0.25rem' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateRequest}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Critical Medical Item</label>
                  <select 
                    className="form-control"
                    value={formData.medicalItemId}
                    onChange={(e) => setFormData({ ...formData, medicalItemId: Number(e.target.value) })}
                  >
                    {catalogItems.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div className="form-group">
                    <label className="form-label">Quantity Needed (Units)</label>
                    <input 
                      type="number" 
                      min="1" 
                      max="10" 
                      className="form-control"
                      value={formData.quantity}
                      onChange={(e) => setFormData({ ...formData, quantity: e.target.value })}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Clinical Urgency Priority</label>
                    <select 
                      className="form-control"
                      value={formData.priority}
                      onChange={(e) => setFormData({ ...formData, priority: e.target.value })}
                    >
                      <option value="STAT_EMERGENCY">STAT Emergency (30m Target)</option>
                      <option value="URGENT">Urgent (2h Delivery)</option>
                      <option value="ROUTINE">Routine Replenishment</option>
                    </select>
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Registered requesting facility</label>
                  <input 
                    type="text" 
                    readOnly 
                    className="form-control"
                    style={{ background: '#F3F4F6' }}
                    value={formData.destinationName}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Operational notes (do not enter patient information)</label>
                  <textarea 
                    rows="3" 
                    className="form-control"
                    value={formData.clinicalNotes}
                    placeholder="Optional logistics notes"
                    onChange={(e) => setFormData({ ...formData, clinicalNotes: e.target.value })}
                  />
                </div>

                <div style={{ padding: '0.625rem', background: '#EBF3FC', borderRadius: '6px', fontSize: '0.75rem', color: '#004085' }}>
                  When submitted, the request is evaluated against registered facilities and available inventory.
                </div>
              </div>

              <div className="modal-footer">
                <button type="button" onClick={() => setShowCreateModal(false)} className="btn btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!user?.hospitalId || catalogItems.length === 0}>
                  <span>Create Request</span>
                  <Send size={16} />
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Logistics Payment */}
      {showPaymentModal && selectedRequestForPayment && (
        <div className="modal-backdrop">
          <div className="modal-content" style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <h2 className="modal-title">Authorize Drone Flight Logistics Fee</h2>
              <button onClick={() => setShowPaymentModal(false)} className="btn btn-secondary" style={{ padding: '0.25rem' }}>
                <X size={18} />
              </button>
            </div>

            <div className="modal-body">
              <div style={{ textAlign: 'center', margin: '0.5rem 0 1.25rem' }}>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)' }}>Total Logistics Fee</div>
                <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--color-nhs-blue)' }}>₹ 2,500.00</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                  Covers corridor clearance, battery swap, and cold-chain sensor monitoring
                </div>
              </div>

              <div style={{ background: '#F9FAFB', padding: '0.75rem', borderRadius: '6px', fontSize: '0.8125rem', marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.375rem' }}>
                  <span style={{ color: 'var(--color-text-secondary)' }}>Request Code:</span>
                  <b>{selectedRequestForPayment.requestCode}</b>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.375rem' }}>
                  <span style={{ color: 'var(--color-text-secondary)' }}>Supplying Depot:</span>
                  <b>{selectedRequestForPayment.supplyingHospitalName}</b>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--color-text-secondary)' }}>Payment Gateway:</span>
                  <b>Razorpay / Instant Institutional Settlement</b>
                </div>
              </div>
            </div>

            <div className="modal-footer">
              <button type="button" onClick={() => setShowPaymentModal(false)} className="btn btn-secondary">
                Cancel
              </button>
              <button 
                type="button" 
                onClick={handlePayment} 
                disabled={paymentProcessing}
                className="btn btn-primary"
              >
                <CreditCard size={16} />
                <span>{paymentProcessing ? 'Opening Razorpay…' : 'Pay with Razorpay'}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RequesterDashboard;
