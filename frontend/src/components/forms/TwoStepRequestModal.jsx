import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { requestService, hospitalService, inventoryService, catalogService } from '../../services/api';
import { 
  X, 
  ArrowRight, 
  ArrowLeft, 
  CheckCircle2, 
  AlertCircle, 
  Package, 
  Clock, 
  Scale, 
  Building2, 
  Send 
} from 'lucide-react';

const getDefaultRequiredBy = () => {
  const localDate = new Date(Date.now() + 2 * 60 * 60 * 1000);
  localDate.setMinutes(localDate.getMinutes() - localDate.getTimezoneOffset());
  return localDate.toISOString().slice(0, 16);
};

export const TwoStepRequestModal = ({ isOpen, onClose, onRequestCreated }) => {
  const { user } = useAuth();
  const [step, setStep] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [submissionSuccess, setSubmissionSuccess] = useState(null);
  const [submitError, setSubmitError] = useState('');
  const [errors, setErrors] = useState({});
  const [centers, setCenters] = useState([]);
  const [items, setItems] = useState([]);
  const [supplierCandidates, setSupplierCandidates] = useState([]);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [loadingDirectory, setLoadingDirectory] = useState(false);
  const [directoryError, setDirectoryError] = useState('');

  // Form State matching Section 8
  const [formData, setFormData] = useState({
    requestHospitalId: user?.hospitalId ? String(user.hospitalId) : '',
    receiverHospitalId: '',
    medicalItemId: '',
    quantity: '1',
    weight: '1',
    urgency: 'HIGH', // CRITICAL, HIGH, NORMAL
    requiredBy: getDefaultRequiredBy(),
    clinicalNotes: '',
  });

  useEffect(() => {
    if (!isOpen) return;
    setLoadingDirectory(true);
    setDirectoryError('');
    Promise.all([hospitalService.getAll(true), catalogService.getAll()])
      .then(([centerData, itemData]) => {
        setCenters(centerData || []);
        setItems((itemData || []).filter(item => item.active !== false));
      })
      .catch(() => {
        setCenters([]);
        setItems([]);
        setDirectoryError('The facility and medical item lists could not be loaded. Check the backend and MySQL connection.');
      })
      .finally(() => setLoadingDirectory(false));
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !formData.medicalItemId || !formData.quantity) {
      setSupplierCandidates([]);
      return;
    }
    let current = true;
    setLoadingCandidates(true);
    inventoryService.getAvailable(formData.medicalItemId, Number(formData.quantity))
      .then(rows => {
        if (current) setSupplierCandidates((rows || []).filter(row => String(row.hospitalId) !== String(formData.requestHospitalId)));
      })
      .catch(() => { if (current) setSupplierCandidates([]); })
      .finally(() => { if (current) setLoadingCandidates(false); });
    return () => { current = false; };
  }, [isOpen, formData.medicalItemId, formData.quantity, formData.requestHospitalId]);

  if (!isOpen) return null;

  const requestHospital = centers.find(h => String(h.id) === String(formData.requestHospitalId));
  const receiverHospital = centers.find(h => String(h.id) === String(formData.receiverHospitalId));
  const selectedItem = items.find(item => String(item.id) === String(formData.medicalItemId));

  const validateStep1 = () => {
    const errs = {};
    if (!requestHospital) errs.requestHospitalId = user?.role === 'ROLE_ADMIN'
      ? 'Choose an approved requesting facility'
      : 'Your account must be linked to an approved medical center';
    if (!selectedItem) errs.medicalItemId = 'Choose a medical item';
    if (formData.receiverHospitalId && !supplierCandidates.some(row => String(row.hospitalId) === String(formData.receiverHospitalId))) {
      errs.receiverHospitalId = 'That facility no longer has enough eligible stock. Refresh the inventory check or leave the supplier unassigned.';
    }
    if (receiverHospital && formData.requestHospitalId === formData.receiverHospitalId) {
      errs.receiverHospitalId = 'Request and receiver hospitals cannot be identical';
    }
    if (!formData.quantity || Number(formData.quantity) <= 0) {
      errs.quantity = 'Valid quantity is required';
    }
    if (!formData.weight || Number(formData.weight) <= 0) {
      errs.weight = 'Weight in kg is required';
    }
    if (!formData.requiredBy) errs.requiredBy = 'Required by timestamp is required';

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleNext = (e) => {
    e.preventDefault();
    if (validateStep1()) {
      setStep(2);
    }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const payload = {
        medicalItemId: Number(formData.medicalItemId),
        medicalType: selectedItem.category,
        quantity: Number(formData.quantity),
        weightKg: Number(formData.weight),
        urgency: formData.urgency,
        requestingHospitalId: Number(formData.requestHospitalId),
        supplyingHospitalId: receiverHospital ? Number(formData.receiverHospitalId) : null,
        pickupLat: receiverHospital?.latitude ?? null,
        pickupLng: receiverHospital?.longitude ?? null,
        destLat: requestHospital.latitude,
        destLng: requestHospital.longitude,
        notes: formData.clinicalNotes,
        requiredBy: formData.requiredBy,
      };

      const result = await requestService.create(payload);
      setSubmissionSuccess(result);
      if (onRequestCreated) onRequestCreated(result);
    } catch (err) {
      const responseMessage = err.response?.data?.message || err.response?.data?.error;
      const statusMessage = err.response?.status
        ? `The server could not save this request (HTTP ${err.response.status}). Check the backend logs and try again.`
        : 'Could not reach the backend. Check that the Spring Boot service is running and try again.';
      setSubmitError(responseMessage || statusMessage);
    } finally {
      setSubmitting(false);
    }
  };

  const resetAndClose = () => {
    setStep(1);
    setSubmissionSuccess(null);
    setErrors({});
    setSubmitError('');
    onClose();
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '600px' }}>
        {submitError && <div role="alert" className="form-error" style={{ margin: '12px 20px' }}>{submitError}</div>}
        {/* Header */}
        <div className="modal-header">
          <div>
            <h2 className="modal-title">Create Medical Request</h2>
            <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
              Autonomous Air Corridor Logistics • Bengaluru Network
            </div>
          </div>
          <button 
            onClick={resetAndClose} 
            className="btn btn-secondary btn-sm"
            style={{ padding: '4px', border: 'none' }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Success View */}
        {submissionSuccess ? (
          <div className="modal-body" style={{ textAlign: 'center', padding: '36px 24px' }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              background: 'var(--color-success-bg)',
              color: 'var(--color-success)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
            }}>
              <CheckCircle2 size={28} />
            </div>
            <h3 style={{ fontSize: '18px', fontWeight: 700, color: 'var(--color-primary-navy)' }}>
              Medical Request Submitted
            </h3>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', margin: '6px 0 20px' }}>
              {submissionSuccess.supplyingHospitalId
                ? 'Your request was saved for the selected supplier to review against its current inventory.'
                : 'Your request was saved as open. Eligible supplier facilities can respond after recording enough unexpired stock. The pickup point is set when a supplier accepts.'}
            </p>

            <div style={{
              background: '#F8FAFC',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-sm)',
              padding: '16px',
              maxWidth: '380px',
              margin: '0 auto 24px',
              textAlign: 'left',
              fontSize: '13px',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span style={{ color: 'var(--color-text-secondary)' }}>Request ID:</span>
                <span style={{ fontWeight: 700, color: 'var(--color-primary-navy)' }}>
                  {submissionSuccess.requestCode || `MR-${submissionSuccess.id}`}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span style={{ color: 'var(--color-text-secondary)' }}>Current State:</span>
                <span style={{ fontWeight: 600, color: 'var(--color-primary-blue)' }}>
                  {submissionSuccess.status || 'AI Processing'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-text-secondary)' }}>Corridor:</span>
                <span style={{ fontWeight: 500 }}>
                  {receiverHospital ? `${receiverHospital.zoneName || 'Bengaluru'} ➔ ${requestHospital.zoneName || 'Bengaluru'}` : 'Supplier will be matched after submission'}
                </span>
              </div>
            </div>

            <button onClick={resetAndClose} className="btn btn-primary" style={{ minWidth: '140px' }}>
              Done
            </button>
          </div>
        ) : (
          <>
            {/* Step Navigation Progress */}
            <div style={{ padding: '16px 24px 0' }}>
              <div className="stepper-header" style={{ marginBottom: 0 }}>
                <div className={`step-indicator ${step === 1 ? 'active' : ''}`}>
                  <span className="step-number">1</span>
                  <span>REQUEST PARAMETERS</span>
                </div>
                <ArrowRight size={14} color="var(--color-text-muted)" />
                <div className={`step-indicator ${step === 2 ? 'active' : ''}`}>
                  <span className="step-number">2</span>
                  <span>REVIEW REQUEST</span>
                </div>
              </div>
            </div>

            {/* STEP 1: Form Inputs */}
            {step === 1 && (
              <form onSubmit={handleNext}>
                <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {directoryError && <div role="alert" className="form-error">{directoryError}</div>}
                  {!loadingDirectory && !directoryError && centers.length === 0 && (
                    <div role="alert" style={{ padding: '12px 14px', border: '1px solid #F2D08A', borderRadius: '8px', background: '#FFFAEB', fontSize: '12px', lineHeight: 1.5 }}>
                      <strong>No approved Bengaluru facilities are available.</strong>{' '}
                      {user?.role === 'ROLE_ADMIN'
                        ? <>Review the existing facility records in <Link to="/hospitals">Hospital Network</Link>, or register and approve a verified facility there.</>
                        : 'Ask your system administrator to approve your facility in Hospital Network before registering or creating requests.'}
                    </div>
                  )}
                  {/* Hospitals Selection */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Request Hospital (Destination) <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      {user?.role === 'ROLE_ADMIN' ? (
                        <select
                          className="form-control"
                          value={formData.requestHospitalId}
                          onChange={event => setFormData({ ...formData, requestHospitalId: event.target.value })}
                        >
                          <option value="">Select a requesting facility</option>
                          {centers.map(center => <option key={center.id} value={center.id}>{center.name}</option>)}
                        </select>
                      ) : (
                        <input className="form-control" readOnly value={requestHospital?.name || 'No approved facility linked to your account'} />
                      )}
                      {errors.requestHospitalId && (
                        <div style={{ color: 'var(--color-error)', fontSize: '11px', marginTop: '4px' }}>
                          {errors.requestHospitalId}
                        </div>
                      )}
                    </div>

                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Supplier with available stock <span style={{ color: 'var(--color-text-secondary)', fontWeight: 400 }}>(optional)</span>
                      </label>
                      <select
                        className="form-control"
                        value={formData.receiverHospitalId}
                        onChange={(e) => setFormData({ ...formData, receiverHospitalId: e.target.value })}
                      >
                        <option value="">Let any eligible supplier respond</option>
                        {supplierCandidates.map(stock => (
                          <option key={stock.hospitalId} value={stock.hospitalId}>
                            {stock.hospitalName} · {stock.availableQuantity} available
                          </option>
                        ))}
                      </select>
                      <small>
                        {loadingCandidates
                          ? 'Checking approved facilities for matching stock…'
                          : !formData.medicalItemId
                            ? 'Choose a medical item and quantity to check current supplier inventory.'
                            : supplierCandidates.length
                              ? 'Choose a listed supplier, or leave this blank to send an open request to eligible suppliers.'
                              : 'No facility has enough eligible stock recorded yet. You can still submit an open request; it will appear to suppliers after they record matching stock.'}
                      </small>
                      {errors.receiverHospitalId && (
                        <div style={{ color: 'var(--color-error)', fontSize: '11px', marginTop: '4px' }}>
                          {errors.receiverHospitalId}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Medical Material & Urgency */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Medical Material <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      <select
                        className="form-control"
                        value={formData.medicalItemId}
                        onChange={(e) => setFormData({ ...formData, medicalItemId: e.target.value, receiverHospitalId: '' })}
                      >
                        <option value="">Select an item</option>
                        {items.map(item => <option key={item.id} value={item.id}>{item.name} · {item.category}</option>)}
                      </select>
                      {errors.medicalItemId && <div role="alert" style={{ color: 'var(--color-error)', fontSize: 11 }}>{errors.medicalItemId}</div>}
                    </div>

                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Urgency Level <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      <div style={{ display: 'flex', gap: '12px', marginTop: '4px' }}>
                        {['CRITICAL', 'HIGH', 'NORMAL'].map((urg) => (
                          <label key={urg} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', cursor: 'pointer' }}>
                            <input
                              type="radio"
                              name="urgency"
                              value={urg}
                              checked={formData.urgency === urg}
                              onChange={(e) => setFormData({ ...formData, urgency: e.target.value })}
                            />
                            <span>{urg}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Quantity, Weight, Required By */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1.2fr', gap: '14px' }}>
                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Quantity <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      <input
                        type="number"
                        min="1"
                        max="20"
                        className="form-control"
                        value={formData.quantity}
                        onChange={(e) => setFormData({ ...formData, quantity: e.target.value })}
                      />
                      {errors.quantity && <div style={{ color: 'var(--color-error)', fontSize: '11px', marginTop: '4px' }}>{errors.quantity}</div>}
                    </div>

                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Weight (kg) <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        min="0.1"
                        max="8.0"
                        className="form-control"
                        value={formData.weight}
                        onChange={(e) => setFormData({ ...formData, weight: e.target.value })}
                      />
                      {errors.weight && <div style={{ color: 'var(--color-error)', fontSize: '11px', marginTop: '4px' }}>{errors.weight}</div>}
                    </div>

                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">
                        Required By <span style={{ color: 'var(--color-error)' }}>*</span>
                      </label>
                      <input
                        type="datetime-local"
                        className="form-control"
                        value={formData.requiredBy}
                        onChange={(e) => setFormData({ ...formData, requiredBy: e.target.value })}
                      />
                      {errors.requiredBy && <div style={{ color: 'var(--color-error)', fontSize: '11px', marginTop: '4px' }}>{errors.requiredBy}</div>}
                    </div>
                  </div>

                  {/* Clinical Notes */}
                  <div className="form-group" style={{ margin: 0 }}>
                    <label className="form-label">Clinical Justification & Helipad Notes</label>
                    <textarea
                      rows="2"
                      className="form-control"
                      value={formData.clinicalNotes}
                      onChange={(e) => setFormData({ ...formData, clinicalNotes: e.target.value })}
                      placeholder="Add triage context or landing bay clearance..."
                    />
                  </div>
                </div>

                <div className="modal-footer">
                  <button type="button" onClick={resetAndClose} className="btn btn-secondary">
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary">
                    <span>Continue</span>
                    <ArrowRight size={14} />
                  </button>
                </div>
              </form>
            )}

            {/* STEP 2: Review & Confirmation */}
            {step === 2 && (
              <div>
                <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {/* Flight Route Header */}
                  <div style={{
                    padding: '16px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'var(--color-pale-blue)',
                    border: '1px solid #C4DAEC',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}>
                    <div>
                      <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-primary-navy)' }}>
                        DISPATCH ORIGIN (SUPPLIER)
                      </div>
                      <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--color-text)' }}>
                        {receiverHospital?.name || 'Supplier to be matched'}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                        {receiverHospital
                          ? `${receiverHospital.zoneName || 'Bengaluru'} Zone • ${receiverHospital.address}`
                          : 'Eligible facilities can respond from their recorded inventory.'}
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '0 12px' }}>
                      <ArrowRight size={20} color="var(--color-primary-blue)" />
                      <span style={{ fontSize: '10px', color: 'var(--color-primary-blue)', fontWeight: 600, marginTop: '2px' }}>
                        {receiverHospital ? 'AIR CORRIDOR' : 'AFTER MATCH'}
                      </span>
                    </div>

                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-primary-navy)' }}>
                        DESTINATION HELIPAD
                      </div>
                      <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--color-text)' }}>
                        {requestHospital.name}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                        {requestHospital.zoneName || 'Bengaluru'} Zone • {requestHospital.address}
                      </div>
                    </div>
                  </div>

                  {/* Summary Breakdown Grid */}
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '12px',
                    fontSize: '12px',
                    background: '#FFFFFF',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '16px',
                  }}>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Medical Type:</span>
                      <div style={{ fontWeight: 600, color: 'var(--color-text)', marginTop: '2px' }}>
                        {selectedItem?.name}
                      </div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Quantity:</span>
                      <div style={{ fontWeight: 600, color: 'var(--color-text)', marginTop: '2px' }}>
                        {formData.quantity} units
                      </div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Weight:</span>
                      <div style={{ fontWeight: 600, color: 'var(--color-text)', marginTop: '2px' }}>
                        {formData.weight} kg
                      </div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Urgency:</span>
                      <div style={{ fontWeight: 700, color: formData.urgency === 'CRITICAL' ? 'var(--color-error)' : 'var(--color-warning)', marginTop: '2px' }}>
                        {formData.urgency}
                      </div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Zone Corridor:</span>
                      <div style={{ fontWeight: 600, color: 'var(--color-primary-navy)', marginTop: '2px' }}>
                        {requestHospital.zoneName || 'Bengaluru'}
                      </div>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-text-secondary)' }}>Required By:</span>
                      <div style={{ fontWeight: 600, color: 'var(--color-text)', marginTop: '2px' }}>
                        {new Date(formData.requiredBy).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </div>
                  </div>

                  {/* Safety & Protocol Notice */}
                  <div style={{
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-sm)',
                    background: '#F8FAFC',
                    border: '1px solid var(--color-border-subtle)',
                    fontSize: '11px',
                    color: 'var(--color-text-secondary)',
                    lineHeight: 1.4,
                  }}>
                    <strong style={{ color: 'var(--color-primary-navy)' }}>Autonomous Safety Broker:</strong> The flight route is validated after a supplier accepts. Cold-chain conditions must be confirmed by the supplier; this application does not yet receive storage sensor telemetry.
                  </div>
                </div>

                <div className="modal-footer">
                  <button 
                    type="button" 
                    onClick={() => setStep(1)} 
                    className="btn btn-secondary"
                    disabled={submitting}
                  >
                    <ArrowLeft size={14} />
                    <span>Back</span>
                  </button>
                  <button 
                    type="button" 
                    onClick={handleSubmit} 
                    className="btn btn-primary"
                    disabled={submitting}
                  >
                    <Send size={14} />
                    <span>{submitting ? 'Submitting...' : 'Submit Request'}</span>
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default TwoStepRequestModal;
