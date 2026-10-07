import React, { useState } from 'react';
import { ChevronDown, ChevronUp, Sparkles } from 'lucide-react';

export const ExplainableAIPanel = ({ decisionType = 'AI decision', decisionData = null }) => {
  const [expanded, setExpanded] = useState(false);
  if (!decisionData) return null;

  const reason = decisionData.reason || decisionData.explanation || decisionData.rationale;
  const score = decisionData.score ?? decisionData.confidenceScore;
  const details = decisionData.factors || decisionData.decisionTrail || [];

  return (
    <section style={{ border: '1px solid var(--color-border)', borderRadius: 8, background: 'var(--color-surface)', marginTop: 12, overflow: 'hidden' }}>
      <button type="button" onClick={() => setExpanded(value => !value)} aria-expanded={expanded} style={{ width: '100%', border: 0, padding: '10px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'transparent', cursor: 'pointer' }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Sparkles size={16} />Recorded {decisionType} explanation{score != null ? ` · Score ${Number(score).toFixed(2)}` : ''}</span>
        {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {expanded && (
        <div style={{ padding: 14, fontSize: 13 }}>
          {reason && <p>{reason}</p>}
          {details.map((factor, index) => (
            <div key={factor.id || index} style={{ padding: 8, borderTop: '1px solid var(--color-border)' }}>
              <strong>{factor.name || factor.agent || factor.agentName || 'Decision factor'}</strong>
              <div>{factor.reason || factor.description || factor.decision || 'No additional explanation recorded.'}</div>
            </div>
          ))}
          {!reason && details.length === 0 && <p>No explanation fields were returned by the backend.</p>}
        </div>
      )}
    </section>
  );
};

export default ExplainableAIPanel;
