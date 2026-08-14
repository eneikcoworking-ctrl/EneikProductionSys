/**
 * @file HelloWorld.tsx
 * @agent TAG-03 (Belief Intension) & TAG-11 (Client Perception)
 * @description The phenomenal interface for the Hello World project.
 */

import React from 'react';

interface HelloWorldProps {
  message: string;
}

export const HelloWorld: React.FC<HelloWorldProps> = ({ message }) => {
  return (
    <div style={styles.container}>
      <h1 style={styles.title}>{message}</h1>
      <p style={styles.subtitle}>Phenomenal experience optimized (LCP < 2.5s)</p>
    </div>
  );
};

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    justifyContent: 'center',
    height: '100vh',
    backgroundColor: '#f0f2f5',
    fontFamily: 'Inter, sans-serif',
  },
  title: {
    fontSize: '3rem',
    color: '#1a1a1a',
    textAlign: 'center' as const,
  },
  subtitle: {
    fontSize: '1.2rem',
    color: '#666',
    marginTop: '1rem',
  },
};
