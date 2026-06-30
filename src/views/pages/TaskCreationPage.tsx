/**
 * @file TaskCreationPage.tsx
 * @agent TAG-11 (Client Perception)
 * @description Page for creating and decomposing tasks for the Agency.
 */

import React, { useState } from 'react';
import { DecompositionController } from '../../controllers/tasks/DecompositionController';
import { ITask, TaskStatus } from '../../models/domain/Task';
import { AgentStatus } from '../components/AgentStatus';

export const TaskCreationPage: React.FC = () => {
  const [input, setInput] = useState('');
  const [tasks, setTasks] = useState<ITask[]>([]);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleDecompose = () => {
    if (!input.trim()) return;

    setIsProcessing(true);
    // Simulate a slight delay for "thought process"
    setTimeout(() => {
      const decomposedTasks = DecompositionController.decompose(input);
      setTasks(decomposedTasks);
      setIsProcessing(false);

      // Simulate agent work starting
      startSimulatedWork(decomposedTasks);
    }, 800);
  };

  const startSimulatedWork = (taskList: ITask[]) => {
    taskList.forEach((task, index) => {
      setTimeout(() => {
        setTasks(prev => prev.map(t =>
          t.id === task.id ? { ...t, status: TaskStatus.IN_PROGRESS } : t
        ));

        // Complete some tasks faster than others
        const completionTime = 2000 + (index * 1500);
        setTimeout(() => {
          setTasks(prev => prev.map(t =>
            t.id === task.id ? { ...t, status: TaskStatus.DONE } : t
          ));
        }, completionTime);
      }, 500 * index);
    });
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h1 style={styles.title}>BRAND OS Agent Command Center</h1>
        <p style={styles.subtitle}>Input your requirement to initiate agentic decomposition</p>
      </header>

      <main style={styles.main}>
        <div style={styles.inputSection}>
          <textarea
            style={styles.textarea}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="e.g., Create a new window for task management with a secure backend and database storage..."
          />
          <button
            style={{...styles.button, opacity: isProcessing ? 0.6 : 1}}
            onClick={handleDecompose}
            disabled={isProcessing}
          >
            {isProcessing ? 'DECOMPOSING...' : 'DECOMPOSE & START AGENTS'}
          </button>
        </div>

        {tasks.length > 0 && (
          <div style={styles.taskSection}>
            <h2 style={styles.sectionTitle}>Operational Task Board</h2>
            <div style={styles.taskGrid}>
              {tasks.map(task => (
                <div key={task.id} style={styles.taskCard}>
                  <div style={styles.taskHeader}>
                    <span style={styles.agentTag}>{task.agentTag}</span>
                    <span style={{...styles.statusBadge, ...getStatusStyle(task.status)}}>
                      {task.status}
                    </span>
                  </div>
                  <p style={styles.taskDesc}>{task.description}</p>
                  <AgentStatus agentTag={task.agentTag} status={task.status} />
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

const getStatusStyle = (status: TaskStatus) => {
  switch (status) {
    case TaskStatus.DONE: return { backgroundColor: '#d4edda', color: '#155724' };
    case TaskStatus.IN_PROGRESS: return { backgroundColor: '#fff3cd', color: '#856404' };
    default: return { backgroundColor: '#e2e3e5', color: '#383d41' };
  }
};

const styles = {
  container: {
    padding: '40px',
    backgroundColor: '#f8f9fa',
    minHeight: '100vh',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  header: {
    textAlign: 'center' as const,
    marginBottom: '40px',
  },
  title: {
    fontSize: '2.5rem',
    margin: '0 0 10px 0',
    color: '#212529',
  },
  subtitle: {
    fontSize: '1.1rem',
    color: '#6c757d',
  },
  main: {
    maxWidth: '1000px',
    margin: '0 auto',
  },
  inputSection: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '20px',
    marginBottom: '60px',
  },
  textarea: {
    height: '150px',
    padding: '15px',
    fontSize: '1.1rem',
    borderRadius: '8px',
    border: '1px solid #ced4da',
    resize: 'none' as const,
    boxShadow: 'inset 0 1px 2px rgba(0,0,0,.075)',
  },
  button: {
    padding: '15px 30px',
    fontSize: '1.2rem',
    fontWeight: 'bold',
    backgroundColor: '#007bff',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  },
  taskSection: {
    borderTop: '1px solid #dee2e6',
    paddingTop: '40px',
  },
  sectionTitle: {
    marginBottom: '30px',
    textAlign: 'center' as const,
  },
  taskGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
    gap: '20px',
  },
  taskCard: {
    backgroundColor: 'white',
    padding: '20px',
    borderRadius: '10px',
    boxShadow: '0 4px 6px rgba(0,0,0,0.05)',
    border: '1px solid #e9ecef',
  },
  taskHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '15px',
  },
  agentTag: {
    fontSize: '0.85rem',
    fontWeight: 'bold',
    color: '#0056b3',
    backgroundColor: '#e7f1ff',
    padding: '4px 8px',
    borderRadius: '4px',
  },
  statusBadge: {
    fontSize: '0.75rem',
    fontWeight: 'bold',
    padding: '4px 8px',
    borderRadius: '12px',
    textTransform: 'uppercase' as const,
  },
  taskDesc: {
    fontSize: '1rem',
    color: '#495057',
    lineHeight: '1.5',
    marginBottom: '20px',
  },
};
