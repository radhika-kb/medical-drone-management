import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { requestService, missionService, paymentService } from '../services/api';
import AppLayout from '../components/layout/AppLayout';
import StatusBadge from '../components/common/StatusBadge';
import MissionProgressStepper from '../components/mission/MissionProgressStepper';
import TwoStepRequestModal from '../components/forms/TwoStepRequestModal';
import { 
  Package, 
  Search, 
  Filter, 
  PlusCircle, 
  RefreshCw, 
  Building2, 
  Clock, 
  CheckCircle2, 
  XCircle, 
  Send, 
  CreditCard, 
  Eye, 
  X,
  FileText,
  AlertCircle
} from 'lucide-react';

export const Requests = () => {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [priorityFilter, setPriorityFilter] = useState('ALL');
  const [zoneFilter, setZoneFilter] = useState('ALL');
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [actionProcessing, setActionProcessing] = useState(false);
  const [actionMessage, setActionMessage] = useState(null);
  const [paymentProcessing, setPaymentProcessing] = useState(false);

  const loadRequests = async () => {
    try {
      setLoading(true);
      const data = await requestService.getAll();
      setRequests(data || []);
    } catch (e) {
      console.error('Unable to load requests:', e);
      setRequests([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRequests();
  }, []);

  // Actions
  const handleAcceptRequest = async (id) => {
    setActionMessage({ type: 'error', text: 'Supplier responses must be submitted from the supplying hospital account.' });
  };

  const handlePrepareReview = async (requestId) => {
    setActionProcessing(true);
    setActionMessage(null);
    try {
      const result = await missionService.prepareReview(requestId);
      const mission = result.mission || result;
      setRequests(prev => prev.map(r => r.id === requestId ? { ...r, status: 'CENTRAL_HUB_REVIEW' } : r));
      if (selectedRequest?.id === requestId) {
        setSelectedRequest(prev => ({ ...prev, status: 'CENTRAL_HUB_REVIEW' }));
      }
      setActionMessage({ type: 'success', text: `AI recommendation ${mission.missionCode || mission.id} is ready for Central Hub review. No drone has launched.` });
    } catch (e) {
      setActionMessage({ type: 'error', text: e.response?.data?.message || 'Could not prepare the AI recommendation for Central Hub review.' });
    } finally {
      setActionProcessing(false);
    }
  };

  const handleConfirmDelivery = async (requestId) => {
    setActionProcessing(true);
    setActionMessage(null);
    try {
      await requestService.confirmDelivery(requestId);
      setRequests(prev => prev.map(r => r.id === requestId ? { ...r, status: 'DELIVERY_CONFIRMED' } : r));
      if (selectedRequest?.id === requestId) {
        setSelectedRequest(prev => ({ ...prev, status: 'DELIVERY_CONFIRMED' }));
      }
      setActionMessage({ type: 'success', text: 'Medical receipt confirmed. Mission archived.' });
    } catch (e) {
      setActionMessage({ type: 'error', text: 'Confirmation failed.' });
    } finally {
      setActionProcessing(false);
    }
  };

  const handlePayRequest = async (request) => {
    setPaymentProcessing(true);
    setActionMessage(null);
    try {
      const order = await paymentService.createOrder(request.id);
      if (order.paymentStatus === 'SUCCESS') {
        await loadRequests();
        setActionMessage({ type: 'success', text: 'Payment is already confirmed. This request is ready for dispatch.' });
        return;
      }
      if (!order.razorpayKeyId || !order.gatewayOrderId || !order.amountInPaise) {
        throw new Error('Razorpay test or live credentials are not configured on the backend.');
      }

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
      if (!window.Razorpay) throw new Error('Razorpay Checkout is unavailable in this browser.');

      const checkout = new window.Razorpay({
        key: order.razorpayKeyId,
        amount: order.amountInPaise,
        currency: order.currency,
        name: 'Bengaluru Medical Drone Network',
        description: `Medical delivery ${request.requestCode}`,
        order_id: order.gatewayOrderId,
        handler: async (response) => {
          try {
            const verified = await paymentService.verify({
              paymentReference: order.paymentReference,
              gatewayOrderId: response.razorpay_order_id,
              gatewayPaymentId: response.razorpay_payment_id,
              gatewaySignature: response.razorpay_signature,
            });
            await loadRequests();
            setSelectedRequest(prev => prev?.id === request.id ? { ...prev, status: 'READY_FOR_DISPATCH' } : prev);
            setActionMessage({ type: 'success', text: `Payment ${verified.paymentStatus} confirmed by the backend. Request is ready for dispatch.` });
          } catch (error) {
            setActionMessage({ type: 'error', text: error.response?.data?.message || error.message || 'Backend payment verification failed. The request remains unpaid.' });
          } finally {
            setPaymentProcessing(false);
          }
        },
        modal: {
          ondismiss: () => setPaymentProcessing(false),
        },
        theme: { color: '#123B5D' },
      });
      checkout.on('payment.failed', (event) => {
        setPaymentProcessing(false);
        setActionMessage({ type: 'error', text: event.error?.description || 'Razorpay reported that payment did not complete.' });
      });
      checkout.open();
    } catch (error) {
      setPaymentProcessing(false);
      setActionMessage({ type: 'error', text: error.response?.data?.message || error.message || 'Could not start Razorpay checkout.' });
    }
  };

  const filteredRequests = requests.filter(req => {
    const code = req.requestCode || `REQ-${req.id}`;
    const item = req.medicalItemName || '';
    const hospital = req.hospitalName || '';
    const matchesSearch = 
      code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.toLowerCase().includes(searchTerm.toLowerCase()) ||
      hospital.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus = statusFilter === 'ALL' || req.status === statusFilter;
    const matchesPriority = priorityFilter === 'ALL' || req.priority === priorityFilter;
    const matchesZone = zoneFilter === 'ALL' || req.zone === zoneFilter;

    return matchesSearch && matchesStatus && matchesPriority && matchesZone;
  });

  return (
    <AppLayout
      pageTitle="MEDICAL REQUESTS"
      breadcrumb="Central Operations • Logistics • Requests Registry"
    >
      {/* Top Header & Actions */}
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
            Medical Requests & Dispatch Registry
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            Authoritative tracking of all inter-hospital medical air supply transfers
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={loadRequests} className="btn btn-secondary">
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Refresh</span>
          </button>
          <button onClick={() => setIsCreateOpen(true)} className="btn btn-primary">
            <PlusCircle size={16} />
            <span>Create Medical Request</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar (Section 10) */}
      <div className="card" style={{ padding: '16px', marginBottom: '20px' }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(240px, 1.5fr) repeat(3, minmax(140px, 1fr)) auto',
          gap: '12px',
          alignItems: 'center',
        }}>
          {/* Search */}
          <div style={{ position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--color-text-secondary)' }} />
            <input
              type="text"
              placeholder="Search by ID, item, or hospital..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="form-control"
              style={{ paddingLeft: '32px' }}
            />
          </div>

          {/* Status Filter */}
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Statuses</option>
            <option value="PENDING">Pending</option>
            <option value="WAITING_SUPPLIER_RESPONSE">Awaiting Supplier</option>
            <option value="SUPPLIER_ACCEPTED">Supplier Accepted</option>
            <option value="NO_SUPPLIER_AVAILABLE">No Supplier Available</option>
            <option value="PAYMENT_PENDING">Payment Pending</option>
            <option value="PAYMENT_FAILED">Payment Failed</option>
            <option value="RESOURCE_ASSIGNED">Resource Assigned</option>
            <option value="READY_FOR_DISPATCH">Ready for Dispatch</option>
            <option value="CENTRAL_HUB_REVIEW">Central Hub Review</option>
            <option value="DISPATCH_DELAYED">Dispatch Delayed</option>
            <option value="DISPATCHED">Dispatched</option>
            <option value="IN_TRANSIT">In Transit</option>
            <option value="DELIVERED">Delivered</option>
            <option value="DELIVERY_CONFIRMED">Delivery Confirmed</option>
          </select>

          {/* Priority Filter */}
          <select
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Priorities</option>
            <option value="STAT_EMERGENCY">Critical (STAT)</option>
            <option value="CRITICAL">Critical</option>
            <option value="URGENT">Urgent</option>
            <option value="ROUTINE">Routine</option>
          </select>

          {/* Zone Filter */}
          <select
            value={zoneFilter}
            onChange={(e) => setZoneFilter(e.target.value)}
            className="form-control"
          >
            <option value="ALL">All Bengaluru Zones</option>
            <option value="North">North Zone</option>
            <option value="East">East Zone</option>
            <option value="South">South Zone</option>
            <option value="West">West Zone</option>
          </select>

          {/* Reset button */}
          {(searchTerm || statusFilter !== 'ALL' || priorityFilter !== 'ALL' || zoneFilter !== 'ALL') && (
            <button
              onClick={() => {
                setSearchTerm('');
                setStatusFilter('ALL');
                setPriorityFilter('ALL');
                setZoneFilter('ALL');
              }}
              className="btn btn-secondary"
              style={{ fontSize: '12px' }}
            >
              Reset
            </button>
          )}
        </div>

        <div style={{ marginTop: '10px', fontSize: '12px', color: 'var(--color-text-secondary)', display: 'flex', justifyContent: 'space-between' }}>
          <span>Showing <strong>{filteredRequests.length}</strong> medical requests</span>
          <span>Authoritative Source: Spring Boot REST API & MySQL</span>
        </div>
      </div>

      {/* Requests Table */}
      <div className="card" style={{ padding: '0', overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Request ID</th>
                <th>Medical Payload</th>
                <th>Requesting Hospital</th>
                <th>Supplying Depot</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Created</th>
                <th style={{ textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredRequests.map((req) => (
                <tr key={req.id}>
                  <td style={{ fontWeight: 700, color: 'var(--color-primary-navy)' }}>
                    {req.requestCode || `REQ-${req.id}`}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: 'var(--color-text)' }}>
                      {req.medicalItemName || 'Emergency Supply'}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      Qty: {req.quantity} • Weight: {req.weightKg || '2.0'} kg
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{req.hospitalName || 'Requester facility unavailable'}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      Zone: {req.zone || '—'}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{req.supplyingHospitalName || 'No supplier assigned'}</div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                      Zone: {req.supplyZone || '—'}
                    </div>
                  </td>
                  <td>
                    <StatusBadge status={req.priority} size="sm" />
                  </td>
                  <td>
                    <StatusBadge status={req.status} size="sm" />
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                    {req.createdAt}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      onClick={() => setSelectedRequest(req)}
                      className="btn btn-secondary"
                      style={{ fontSize: '12px', padding: '4px 10px' }}
                    >
                      <Eye size={13} />
                      <span>Inspect</span>
                    </button>
                  </td>
                </tr>
              ))}

              {filteredRequests.length === 0 && (
                <tr>
                  <td colSpan="8" style={{ textAlign: 'center', padding: '36px', color: 'var(--color-text-secondary)' }}>
                    No medical requests match the current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Inspect Detail Drawer / Modal */}
      {selectedRequest && (
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
            display: 'flex',
            flexDirection: 'column',
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '18px 24px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <h2 style={{ fontSize: '16px', fontWeight: 800, margin: 0, color: 'var(--color-primary-navy)' }}>
                    {selectedRequest.requestCode || `REQ-${selectedRequest.id}`}
                  </h2>
                  <StatusBadge status={selectedRequest.priority} size="sm" />
                </div>
                <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                  Authoritative Request Lifecycle Details
                </div>
              </div>

              <button
                onClick={() => setSelectedRequest(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-text-secondary)' }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Action Banner if any */}
              {actionMessage && (
                <div style={{
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '13px',
                  background: actionMessage.type === 'success' ? 'rgba(22, 128, 60, 0.1)' : 'rgba(198, 40, 40, 0.1)',
                  color: actionMessage.type === 'success' ? 'var(--color-success)' : 'var(--color-error)',
                  border: `1px solid ${actionMessage.type === 'success' ? 'rgba(22, 128, 60, 0.3)' : 'rgba(198, 40, 40, 0.3)'}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}>
                  {actionMessage.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                  <span>{actionMessage.text}</span>
                </div>
              )}

              {/* Progress Milestones Stepper (Section 10) */}
              <div style={{
                background: 'var(--color-background)',
                padding: '16px',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border-subtle)',
              }}>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--color-primary-navy)', marginBottom: '12px' }}>
                  MISSION LIFECYCLE MILESTONES
                </div>
                <MissionProgressStepper currentStatus={selectedRequest.status} />
              </div>

              {/* Request Specifications */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <div style={{ background: 'var(--color-surface)', padding: '12px', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>Requester Facility</div>
                  <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)', marginTop: '2px' }}>
                    {selectedRequest.hospitalName}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Zone: {selectedRequest.zone || 'North'}</div>
                </div>

                <div style={{ background: 'var(--color-surface)', padding: '12px', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>Supplying Depot</div>
                  <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-primary-navy)', marginTop: '2px' }}>
                    {selectedRequest.supplyingHospitalName}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>Zone: {selectedRequest.supplyZone || 'South'}</div>
                </div>
              </div>

              {/* Item Details */}
              <div style={{ background: 'var(--color-surface)', padding: '14px', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>Requested Medical Material</div>
                <div style={{ fontWeight: 700, fontSize: '14px', marginTop: '4px' }}>
                  {selectedRequest.medicalItemName}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                  Quantity: {selectedRequest.quantity} units • Max Transport Weight: {selectedRequest.weightKg || '2.0'} kg
                </div>
                <div style={{ marginTop: '8px', fontSize: '12px', color: 'var(--color-text-secondary)', background: 'var(--color-background)', padding: '8px', borderRadius: '4px' }}>
                  <strong>Clinical Justification: </strong>{selectedRequest.clinicalNotes || 'Emergency clinical requirement.'}
                </div>
              </div>

              {/* Operational Action Buttons by State */}
              <div style={{
                borderTop: '1px solid var(--color-border)',
                paddingTop: '16px',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '10px',
              }}>
                {['SUPPLIER_ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_FAILED'].includes(selectedRequest.status)
                  && ['ROLE_ADMIN', 'ROLE_REQUESTING_HOSPITAL'].includes(user?.role)
                  && (user?.role === 'ROLE_ADMIN' || user?.hospitalId === selectedRequest.requestingHospitalId) && (
                  <button
                    onClick={() => handlePayRequest(selectedRequest)}
                    disabled={paymentProcessing}
                    className="btn btn-primary"
                  >
                    <CreditCard size={16} />
                    <span>{paymentProcessing ? 'Opening Razorpay…' : selectedRequest.status === 'PAYMENT_PENDING' ? 'Continue Payment' : 'Pay with Razorpay'}</span>
                  </button>
                )}

                {selectedRequest.status === 'READY_FOR_DISPATCH'
                  && ['ROLE_ADMIN', 'ROLE_CENTRAL_HUB'].includes(user?.role) && (
                  <button
                    onClick={() => handlePrepareReview(selectedRequest.id)}
                    disabled={actionProcessing}
                    className="btn btn-primary"
                  >
                    <Send size={16} />
                    <span>Prepare AI Recommendation for Hub</span>
                  </button>
                )}

                {(selectedRequest.status === 'IN_TRANSIT' || selectedRequest.status === 'ARRIVED')
                  && (user?.role === 'ROLE_ADMIN' || user?.role === 'ROLE_CENTRAL_HUB'
                    || user?.role === 'ROLE_REQUESTING_HOSPITAL') && (
                  <button
                    onClick={() => handleConfirmDelivery(selectedRequest.id)}
                    disabled={actionProcessing}
                    className="btn btn-primary"
                  >
                    <CheckCircle2 size={16} />
                    <span>Confirm Receipt & Handover</span>
                  </button>
                )}

                <button
                  onClick={() => setSelectedRequest(null)}
                  className="btn btn-secondary"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 2-Step Request Creation Modal */}
      <TwoStepRequestModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onRequestCreated={() => {
          setIsCreateOpen(false);
          loadRequests();
        }}
      />
    </AppLayout>
  );
};

export default Requests;
