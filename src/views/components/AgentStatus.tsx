/**
 * @file AgentStatus.tsx
 * @agent TAG-03 (Belief Intension) & TAG-11 (Client Perception)
 * @description Visualizes the activity of a specific agent.
 */

import React from 'react';
import { TaskStatus } from '../../models/domain/Task';

interface AgentStatusProps {
  agentTag: string;
  status: TaskStatus;
}

export const AgentStatus: React.FC<AgentStatusProps> = ({ agentTag, status }) => {
  const getStatusMessage = () => {
    if (status === TaskStatus.DONE) return "Work verified and integrated.";
    if (status === TaskStatus.IN_PROGRESS) return "Agent is actively processing...";
    return "Waiting for resource allocation.";
  };

  const isWorking = status === TaskStatus.IN_PROGRESS;

  return (
    <div style={styles.container}>
      <div style={styles.statusRow}>
        <div style={{
          ...styles.indicator,
          backgroundColor: isWorking ? '#28a745' : (status === TaskStatus.DONE ? '#007bff' : '#ccc'),
          boxShadow: isWorking ? '0 0 8px #28a745' : 'none'
        }} />
        <span style={styles.message}>{getStatusMessage()}</span>
      </div>
      {isWorking && (
        <div style={styles.progressBar}>
          <div style={styles.progressFill} />
        </div>
      )}
    </div>
  );
};

const styles = {
  container: {
    padding: '10px 0',
    borderTop: '1px solid #f1f3f5',
  },
  statusRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  indicator: {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    transition: 'all 0.3s ease',
  },
  message: {
    fontSize: '0.85rem',
    fontStyle: 'italic' as const,
    color: '#6c757d',
  },
  progressBar: {
    marginTop: '10px',
    height: '4px',
    backgroundColor: '#e9ecef',
    borderRadius: '2px',
    overflow: 'hidden' as const,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#28a745',
    width: '30%', // Initial simulated progress
    animation: 'progressMove 2s infinite ease-in-out',
  },
};

/**
 * Injects the animation keyframes for the progress bar.
 */
const injectStyles = () => {
  if (typeof document === 'undefined') return;
  const styleId = 'agent-status-animation';
  if (document.getElementById(styleId)) return;

  const style = document.createElement('style');
  style.id = styleId;
  style.innerHTML = `
    @keyframes progressMove {
      0% { transform: translateX(-100%); }
      50% { transform: translateX(0%); }
      100% { transform: translateX(230%); }
    }
  `;
  document.head.appendChild(style);
};

injectStyles();
