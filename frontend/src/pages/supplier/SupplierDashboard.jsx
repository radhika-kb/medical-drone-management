import CurrentWeatherCard from '../../components/common/CurrentWeatherCard';
import React, { useState, useEffect } from 'react';
import AppLayout from '../../components/layout/AppLayout';
import { useAuth } from '../../context/AuthContext';
import { inventoryService, supplyService, requestService, catalogService } from '../../services/api';
import { wsService } from '../../services/websocket';
import StatusBadge from '../../components/common/StatusBadge';
import { 
  Boxes, 
  CheckCircle2, 
  XCircle, 
  AlertCircle, 
  Plus, 
  Radio, 
  RefreshCw,
  Package,
  X
} from 'lucide-react';

export const SupplierDashboard = () => {
  const { user } = useAuth();
  const hospitalId = user?.hospitalId;
  const hospitalName = user?.hospitalName || 'Your registered medical center';

  const [inventory, setInventory] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [matchedRequests, setMatchedRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showStockModal, setShowStockModal] = useState(false);
  const [selectedItemForStock, setSelectedItemForStock] = useState(null);
  const [newStockQty, setNewStockQty] = useState('');
  const [expiryDate, setExpiryDate] = useState('');

  // Rejection modal
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [selectedRequestForReject, setSelectedRequestForReject] = useState(null);
  const [rejectReason, setRejectReason] = useState('Stock committed to internal ICU');

  const loadData = async () => {
    try {
      setLoading(true);
      // Try backend fetch
      if (!hospitalId) throw new Error('Account is not linked to a medical center');
      const [invData, reqData, catalogData] = await Promise.all([
        inventoryService.getByHospital(hospitalId).catch(() => []),
        requestService.getSupplierInbox().catch(() => []),
        catalogService.getAll().catch(() => []),
      ]);
      setInventory(invData || []);
      setMatchedRequests(reqData || []);
      setCatalog((catalogData || []).filter(item => item.active !== false));
    } catch (e) {
      console.error('Unable to load this facility inventory or eligible requests:', e);
      setInventory([]);
      setMatchedRequests([]);
      setCatalog([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    const refreshInterval = setInterval(loadData, 30000);

    // Invitations are sent only to the supplier currently at the top of the ranked queue.
    const unsub = hospitalId
      ? wsService.subscribe(`/topic/suppliers/${hospitalId}`, (newReq) => {
          setMatchedRequests(prev => [newReq, ...prev.filter(request => request.id !== newReq.id)]);
        })
      : () => {};

    return () => {
      clearInterval(refreshInterval);
      unsub();
    };
  }, [hospitalId]);



  // Accept request
  const handleAcceptRequest = async (req) => {
    try {
      await supplyService.respond({
        requestId: req.id,
        decision: 'ACCEPTED',
        offeredQuantity: req.quantity,
      });
      await loadData();
    } catch (e) {
      window.alert(e.response?.data?.message || 'Acceptance failed. No stock was reserved.');
      return;
    }
  };

  // Reject request
  const handleRejectRequest = async () => {
    if (!selectedRequestForReject) return;
    try {
      await supplyService.respond({
        requestId: selectedRequestForReject.id,
        decision: 'REJECTED',
        offeredQuantity: selectedRequestForReject.quantity,
        rejectionReason: rejectReason,
      });
      await loadData();
    } catch (e) {
      window.alert(e.response?.data?.message || 'Rejection could not be saved.');
      return;
    }

    setMatchedRequests(prev => prev.filter(r => r.id !== selectedRequestForReject.id));
    setShowRejectModal(false);
  };

  // Update Stock
  const handleSaveStock = async () => {
    if (!selectedItemForStock || newStockQty === '' || !Number.isInteger(Number(newStockQty)) || Number(newStockQty) < 0) return;
    const qty = parseInt(newStockQty, 10);
    const requiresExpiry = ['BLOOD', 'VACCINE', 'ANTIVENOM', 'EMERGENCY_MEDICINE'].includes(selectedItemForStock.medicalItemCategory);
    if (qty > 0 && requiresExpiry && !expiryDate) {
      window.alert('Enter an expiry date before listing this item as available inventory.');
      return;
    }
    try {
      await inventoryService.updateStock(hospitalId, selectedItemForStock.medicalItemId, {
        availableQuantity: qty,
        expiryDate: expiryDate || null,
      });
      await loadData();
    } catch (e) {
      window.alert(e.response?.data?.message || 'Inventory update failed.');
      return;
    }
    setShowStockModal(false);
  };

  return (
    <AppLayout pageTitle="SUPPLIER INVENTORY" breadcrumb={`${hospitalName} • Bengaluru Medical Network`}>
      <CurrentWeatherCard />
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>Inventory and incoming requests</h1>
          <p style={{ margin: '0.25rem 0 0', color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            {hospitalName}
          </p>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => { setSelectedItemForStock(null); setNewStockQty(''); setExpiryDate(''); setShowStockModal(true); }} className="btn btn-primary">
            <Plus size={16} /><span>Add inventory item</span>
          </button>
          <button onClick={loadData} className="btn btn-secondary"><RefreshCw size={16} /><span>Refresh</span></button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Total Units Available</span>
            <Boxes size={18} color="var(--color-nhs-blue)" />
          </div>
          <div className="kpi-value">{inventory.reduce((acc, curr) => acc + curr.availableQuantity, 0)}</div>
          <div className="kpi-subtext">Across {inventory.length} recorded items</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Units Locked (Reserved)</span>
            <Package size={18} color="var(--color-nhs-aqua)" />
          </div>
          <div className="kpi-value">{inventory.reduce((acc, curr) => acc + curr.reservedQuantity, 0)}</div>
          <div className="kpi-subtext">Recorded inventory reservations</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Inbound Match Requests</span>
            <Radio size={18} color="var(--color-nhs-red)" />
          </div>
          <div className="kpi-value">{matchedRequests.filter(r => r.status === 'WAITING_SUPPLIER_RESPONSE').length}</div>
          <div className="kpi-subtext">Awaiting your response</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Storage telemetry</span>
            <AlertCircle size={18} color="var(--color-text-secondary)" />
          </div>
          <div className="kpi-value" style={{ fontSize: '1.25rem' }}>Not reported</div>
          <div className="kpi-subtext">No sensor readings are connected</div>
        </div>
      </div>

      {/* Inbound Matched Requests Queue */}
      <div id="incoming-requests" style={{ marginBottom: '2rem', scrollMarginTop: '90px' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>
          Incoming requests matching this facility's recorded inventory
        </h2>

        {matchedRequests.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', background: '#FFFFFF', border: '1px solid var(--color-border)', borderRadius: '8px', color: 'var(--color-text-secondary)' }}>
            No eligible incoming requests are available for the inventory currently recorded at this facility.
          </div>
        ) : (
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Request ID</th>
                  <th>Medical Item Requested</th>
                  <th>Quantity</th>
                  <th>Requesting Hospital</th>
                  <th>Urgency</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {matchedRequests.map((req) => (
                  <tr key={req.id}>
                    <td>
                      <span style={{ fontWeight: 600 }}>{req.requestCode}</span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{req.medicalItemName}</div>
                    </td>
                    <td>
                      <b style={{ fontSize: '1rem', color: 'var(--color-nhs-blue)' }}>{req.quantity}</b> units
                    </td>
                    <td>
                      <div style={{ fontWeight: 500 }}>{req.requestingHospitalName || '—'}</div>
                    </td>
                    <td>
                      <StatusBadge status={req.urgency} size="small" />
                    </td>
                    <td>
                      {['PENDING', 'SUPPLIER_REJECTED', 'WAITING_SUPPLIER_RESPONSE'].includes(req.status) ? (
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button
                            onClick={() => handleAcceptRequest(req)}
                            className="btn btn-primary"
                            style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem' }}
                          >
                            <CheckCircle2 size={14} />
                            <span>Accept request</span>
                          </button>
                          <button
                            onClick={() => {
                              setSelectedRequestForReject(req);
                              setShowRejectModal(true);
                            }}
                            className="btn btn-secondary"
                            style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem', color: 'var(--color-nhs-red)' }}
                          >
                            <XCircle size={14} />
                            <span>Decline</span>
                          </button>
                        </div>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-nhs-green)', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                          <CheckCircle2 size={14} /> Accepted • In Dispatch Bay
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Depots Inventory Table */}
      <div id="facility-inventory" style={{ scrollMarginTop: '90px' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--color-text)' }}>Facility inventory</h2>

        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Medical item</th>
                <th>Category</th>
                <th>Available Units</th>
                <th>Reserved (Locked)</th>
                <th>Expiry date</th>
                <th>Stock Management</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{item.medicalItemName}</div>
                  </td>
                  <td>
                    <span className="status-badge badge-neutral" style={{ fontSize: '0.7rem' }}>
                      {(item.medicalItemCategory || '—').replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td>
                    <span style={{
                      fontWeight: 700,
                      fontSize: '1rem',
                      color: item.availableQuantity < 10 ? 'var(--color-nhs-red)' : 'var(--color-nhs-green)'
                    }}>
                      {item.availableQuantity}
                    </span> units
                  </td>
                  <td>
                    <span style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>
                      {item.reservedQuantity}
                    </span> units
                  </td>
                  <td>
                    <div style={{ fontSize: '0.8125rem', color: 'var(--color-text-muted)' }}>{item.expiryDate || 'Not recorded'}</div>
                  </td>
                  <td>
                    <button
                      onClick={() => {
                        setSelectedItemForStock(item);
                        setNewStockQty(item.availableQuantity);
                        setExpiryDate(item.expiryDate || '');
                        setShowStockModal(true);
                      }}
                      className="btn btn-secondary"
                      style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                    >
                      <span>Adjust Stock</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {inventory.length === 0 && <p style={{ padding: 20, color: 'var(--color-text-secondary)' }}>No stock has been entered for this facility. Add an item only after confirming its current physical quantity.</p>}
      </div>

      {/* Modal: Adjust Stock */}
      {showStockModal && (
        <div className="modal-backdrop">
          <div className="modal-content" style={{ maxWidth: '400px' }}>
            <div className="modal-header">
              <h2 className="modal-title">{selectedItemForStock ? 'Update inventory quantity' : 'Add inventory item'}</h2>
              <button onClick={() => setShowStockModal(false)} className="btn btn-secondary" style={{ padding: '0.25rem' }}>
                <X size={18} />
              </button>
            </div>
            <div className="modal-body">
              {selectedItemForStock ? <p style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)', marginBottom: '1rem' }}>{selectedItemForStock.medicalItemName}</p> : <div className="form-group">
                <label className="form-label">Medical item</label>
                <select className="form-control" value="" onChange={event => {
                  const item = catalog.find(option => String(option.id) === event.target.value);
                  if (item) setSelectedItemForStock({ medicalItemId: item.id, medicalItemName: item.name, medicalItemCategory: item.category });
                }}>
                  <option value="">Select from the catalog</option>
                  {catalog.filter(option => !inventory.some(stock => String(stock.medicalItemId) === String(option.id))).map(option => (
                    <option key={option.id} value={option.id}>{option.name} · {option.category}</option>
                  ))}
                </select>
              </div>}
              <div className="form-group">
                <label className="form-label">Available Physical Quantity</label>
                <input
                  type="number"
                  min="0"
                  className="form-control"
                  value={newStockQty}
                  onChange={(e) => setNewStockQty(e.target.value)}
                />
              </div>
              {['BLOOD', 'VACCINE', 'ANTIVENOM', 'EMERGENCY_MEDICINE'].includes(selectedItemForStock?.medicalItemCategory) && (
                <div className="form-group">
                  <label className="form-label">Expiry date</label>
                  <input type="date" required className="form-control" value={expiryDate} onChange={event => setExpiryDate(event.target.value)} />
                  <small>Required for this item before the stock can be offered to requesters.</small>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button onClick={() => setShowStockModal(false)} className="btn btn-secondary">Cancel</button>
              <button onClick={handleSaveStock} className="btn btn-primary" disabled={!selectedItemForStock || newStockQty === ''}>Save quantity</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Decline Order Reason */}
      {showRejectModal && selectedRequestForReject && (
        <div className="modal-backdrop">
          <div className="modal-content" style={{ maxWidth: '420px' }}>
            <div className="modal-header">
              <h2 className="modal-title">Decline Supply Order</h2>
              <button onClick={() => setShowRejectModal(false)} className="btn btn-secondary" style={{ padding: '0.25rem' }}>
                <X size={18} />
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">Decline Reason for AI Broker</label>
                <select 
                  className="form-control"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                >
                  <option value="Stock committed to internal ICU">Stock committed to internal emergency ICU</option>
                  <option value="Cold chain maintenance in progress">Cold chain maintenance in progress</option>
                  <option value="Batch recall inspection">Batch recall inspection</option>
                  <option value="Hospital helipad currently obstructed">Hospital helipad currently obstructed</option>
                </select>
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
                Declining makes the request available to other eligible facilities with matching recorded inventory.
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setShowRejectModal(false)} className="btn btn-secondary">Cancel</button>
              <button onClick={handleRejectRequest} className="btn btn-primary" style={{ background: 'var(--color-nhs-red)' }}>
                Confirm Decline
              </button>
            </div>
          </div>
        </div>
      )}
    </AppLayout>
  );
};

export default SupplierDashboard;
