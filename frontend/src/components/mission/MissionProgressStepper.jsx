import React from 'react';
import { CheckCircle2, Circle, Clock, Send, AlertTriangle } from 'lucide-react';

const MILESTONES = [
  { id: 'CREATED', label: 'Request Created' },
  { id: 'AI_ASSESSMENT', label: 'AI Assessment' },
  { id: 'RESOURCE_ASSIGNED', label: 'Resource Assigned' },
  { id: 'SAFETY_CHECK', label: 'Safety Check' },
  { id: 'CENTRAL_HUB_REVIEW', label: 'Central Hub Review' },
  { id: 'DISPATCHED', label: 'Dispatched' },
  { id: 'IN_TRANSIT', label: 'In Transit' },
  { id: 'ARRIVED', label: 'Arrived' },
  { id: 'DELIVERY_CONFIRMED', label: 'Delivery Confirmed' },
  { id: 'COMPLETED', label: 'Completed' },
];

export const MissionProgressStepper = ({ currentStatus = 'IN_TRANSIT' }) => {
  // Determine index in milestone flow
  const getActiveIndex = () => {
    switch (currentStatus.toUpperCase()) {
      case 'DRAFT':
      case 'PENDING': return 0;
      case 'AI_PROCESSING': return 1;
      case 'RESOURCE_ASSIGNED':
      case 'WAITING_SUPPLIER_RESPONSE':
      case 'SUPPLIER_ACCEPTED':
      case 'PAYMENT_PENDING':
      case 'PAYMENT_FAILED':
      case 'ALLOCATED': return 2;
      case 'READY_FOR_DISPATCH': return 3;
      case 'CENTRAL_HUB_REVIEW':
      case 'DISPATCH_DELAYED':
      case 'RECOVERY_REQUIRED': return 4;
      case 'DISPATCHED': return 5;
      case 'IN_TRANSIT':
      case 'NEAR_RECEIVER': return 6;
      case 'ARRIVED':
      case 'DELIVERED': return 7;
      case 'DELIVERY_CONFIRMED': return 8;
      case 'COMPLETED': return 9;
      default: return 5;
    }
  };

  const activeIdx = getActiveIndex();

  return (
    <div style={{
      background: 'var(--color-surface)',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-md)',
      padding: '20px',
      boxShadow: 'var(--shadow-subtle)',
    }}>
      <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--color-primary-navy)', letterSpacing: '0.04em', textTransform: 'uppercase', marginBottom: '16px' }}>
        Mission Progress Milestones
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
        {/* Connecting track line */}
        <div style={{
          position: 'absolute',
          top: '12px',
          left: '20px',
          right: '20px',
          height: '2px',
          background: 'var(--color-border-subtle)',
          zIndex: 1,
        }} />

        {/* Filled progress bar */}
        <div style={{
          position: 'absolute',
          top: '12px',
          left: '20px',
          width: `${(activeIdx / (MILESTONES.length - 1)) * 95}%`,
          height: '2px',
          background: 'var(--color-primary-blue)',
          zIndex: 2,
          transition: 'width 0.3s ease',
        }} />

        {MILESTONES.map((step, idx) => {
          const isCompleted = idx < activeIdx;
          const isCurrent = idx === activeIdx;

          return (
            <div key={step.id} style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              zIndex: 3,
              position: 'relative',
              textAlign: 'center',
            }}>
              <div style={{
                width: '26px',
                height: '26px',
                borderRadius: '50%',
                background: isCompleted ? 'var(--color-success)' : isCurrent ? 'var(--color-primary-blue)' : '#FFFFFF',
                border: `2px solid ${isCompleted ? 'var(--color-success)' : isCurrent ? 'var(--color-primary-blue)' : 'var(--color-border)'}`,
                color: isCompleted || isCurrent ? '#FFFFFF' : 'var(--color-text-muted)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: isCurrent ? '0 0 0 4px rgba(47, 111, 237, 0.15)' : 'none',
              }}>
                {isCompleted ? (
                  <CheckCircle2 size={14} />
                ) : isCurrent ? (
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#FFFFFF' }} />
                ) : (
                  <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#CCD4DE' }} />
                )}
              </div>

              <span style={{
                fontSize: '11px',
                fontWeight: isCurrent ? 700 : isCompleted ? 600 : 500,
                color: isCurrent ? 'var(--color-primary-navy)' : isCompleted ? 'var(--color-text)' : 'var(--color-text-muted)',
                marginTop: '8px',
                maxWidth: '75px',
                lineHeight: 1.2,
              }}>
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default MissionProgressStepper;
