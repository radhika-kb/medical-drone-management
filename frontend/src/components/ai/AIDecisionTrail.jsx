import React from 'react';
import { Sparkles } from 'lucide-react';

export const AIDecisionTrail = ({ decision = null, compact = false }) => {
  const entries = Array.isArray(decision)
    ? decision
    : (decision?.decisionTrail || decision?.decisions || (decision ? [decision] : []));

  return (
    <div className="medair-card" style={{ padding: compact ? 16 : 20 }}>
      <div className="medair-card-header" style={{ marginBottom: 12 }}>
        <div className="medair-card-title"><Sparkles size={16} color="var(--color-ai)" /><span>RECORDED AI DECISIONS</span></div>
        <span style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>Decision support; human review remains required.</span>
      </div>
      {entries.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--color-text-secondary)', fontSize: 13 }}>No stored AI decision records are available.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {entries.map((entry, index) => (
            <article key={entry.id || index} style={{ padding: 10, border: '1px solid var(--color-border)', borderRadius: 6 }}>
              <strong>{entry.agentName || entry.agent || 'AI agent'} · {entry.decision || entry.action || 'Recorded decision'}</strong>
              {entry.score != null && <span> · Score {Number(entry.score).toFixed(2)}</span>}
              {(entry.reason || entry.explanation) && <div style={{ marginTop: 4, fontSize: 12 }}>{entry.reason || entry.explanation}</div>}
            </article>
          ))}
        </div>
      )}
    </div>
  );
};

export default AIDecisionTrail;
