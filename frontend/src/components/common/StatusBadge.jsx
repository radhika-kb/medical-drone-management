import React from 'react';
import { 
  Clock, 
  Cpu, 
  CheckCircle2, 
  Send, 
  PackageCheck, 
  AlertTriangle, 
  AlertOctagon, 
  XCircle, 
  RotateCcw,
  Navigation,
  ShieldCheck,
  Radio,
  FileEdit,
  Sparkles
} from 'lucide-react';

const STATUS_MAP = {
  // Mission & Delivery Statuses (Section 19)
  PENDING: { label: 'Pending', className: 'badge-pending', icon: Clock },
  AI_PROCESSING: { label: 'AI Processing', className: 'badge-ai', icon: Sparkles },
  RESOURCE_ASSIGNED: { label: 'Resource Assigned', className: 'badge-assigned', icon: Cpu },
  WAITING_SUPPLIER_RESPONSE: { label: 'Awaiting Supplier', className: 'badge-pending', icon: Radio },
  SUPPLIER_ACCEPTED: { label: 'Supplier Accepted', className: 'badge-assigned', icon: CheckCircle2 },
  SUPPLIER_REJECTED: { label: 'Supplier Rejected', className: 'badge-error', icon: XCircle },
  NO_SUPPLIER_AVAILABLE: { label: 'No Supplier Available', className: 'badge-error', icon: AlertOctagon },
  PAYMENT_PENDING: { label: 'Payment Pending', className: 'badge-warning', icon: Clock },
  PAYMENT_FAILED: { label: 'Payment Failed', className: 'badge-error', icon: XCircle },
  ALLOCATED: { label: 'Resource Allocated', className: 'badge-assigned', icon: Cpu },
  READY_FOR_DISPATCH: { label: 'Ready for Dispatch', className: 'badge-ready', icon: CheckCircle2 },
  CENTRAL_HUB_REVIEW: { label: 'Central Hub Review', className: 'badge-warning', icon: ShieldCheck },
  DISPATCH_DELAYED: { label: 'Dispatch Delayed', className: 'badge-warning', icon: Clock },
  RECOVERY_REQUIRED: { label: 'Recovery Required', className: 'badge-error', icon: AlertOctagon },
  REJECTED: { label: 'Recommendation Rejected', className: 'badge-error', icon: XCircle },
  DISPATCHED: { label: 'Dispatch State', className: 'badge-dispatched', icon: Send },
  IN_TRANSIT: { label: 'Transit State', className: 'badge-transit', icon: Navigation },
  NEAR_RECEIVER: { label: 'Near Receiver State', className: 'badge-near', icon: AlertTriangle },
  ARRIVED: { label: 'Arrival State', className: 'badge-success', icon: PackageCheck },
  DELIVERY_CONFIRMED: { label: 'Completion Recorded', className: 'badge-success', icon: CheckCircle2 },
  DELIVERED: { label: 'Delivery Recorded', className: 'badge-success', icon: CheckCircle2 },
  COMPLETED: { label: 'Mission Complete Record', className: 'badge-success', icon: CheckCircle2 },
  DELAYED: { label: 'Delayed', className: 'badge-warning', icon: AlertTriangle },
  CANCELLED: { label: 'Cancelled', className: 'badge-error', icon: XCircle },
  REASSIGNMENT_REQUIRED: { label: 'Reassignment Required', className: 'badge-error', icon: AlertOctagon },
  FAILED: { label: 'Failed', className: 'badge-error', icon: AlertOctagon },

  // Priorities (Section 8 & 19)
  CRITICAL: { label: 'Critical', className: 'badge-error', icon: AlertOctagon },
  HIGH: { label: 'High', className: 'badge-warning', icon: AlertTriangle },
  MEDIUM: { label: 'Medium', className: 'badge-warning', icon: AlertTriangle },
  LOW: { label: 'Low', className: 'badge-success', icon: CheckCircle2 },
  NORMAL: { label: 'Normal', className: 'badge-pending', icon: Clock },
  ROUTINE: { label: 'Routine', className: 'badge-pending', icon: Clock },
  STAT_EMERGENCY: { label: 'Critical (STAT)', className: 'badge-error', icon: AlertOctagon },
  URGENT: { label: 'Urgent', className: 'badge-warning', icon: AlertTriangle },

  // Drone Fleet Statuses
  AVAILABLE: { label: 'Available', className: 'badge-success', icon: CheckCircle2 },
  RESERVED_FOR_REVIEW: { label: 'Reserved for Review', className: 'badge-warning', icon: Clock },
  IN_FLIGHT: { label: 'Active Unit State', className: 'badge-transit', icon: Send },
  CHARGING: { label: 'Charging', className: 'badge-pending', icon: RotateCcw },
  MAINTENANCE: { label: 'Maintenance', className: 'badge-warning', icon: AlertTriangle },
  ACTIVE: { label: 'Active', className: 'badge-success', icon: CheckCircle2 },
};

export const StatusBadge = ({ status, size = 'md' }) => {
  if (!status) return null;
  const key = String(status).toUpperCase().replace(/\s+/g, '_');
  const config = STATUS_MAP[key] || {
    label: String(status).replace(/_/g, ' '),
    className: 'badge-pending',
    icon: Clock,
  };
  const IconComponent = config.icon;
  const iconSize = size === 'sm' ? 12 : 14;

  return (
    <span className={`status-badge ${config.className}`}>
      {IconComponent && <IconComponent size={iconSize} style={{ flexShrink: 0 }} />}
      <span>{config.label}</span>
    </span>
  );
};

export default StatusBadge;
