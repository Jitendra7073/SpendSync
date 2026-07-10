'use client';

import { useEffect, useState } from 'react';

interface HealthPayload {
  status: 'ok' | 'degraded';
  timestamp: string;
  environment: string;
  uptimeSeconds: number;
  db: { connected: boolean; latencyMs: number };
  memory: { rssMB: number; heapUsedMB: number; heapTotalMB: number };
}

type FetchState =
  | { phase: 'loading' }
  | { phase: 'ok'; data: HealthPayload }
  | { phase: 'unreachable' };

const REFRESH_MS = 5000;

function formatUptime(totalSeconds: number): string {
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const parts = [];
  if (days) parts.push(`${days}d`);
  if (hours) parts.push(`${hours}h`);
  if (minutes) parts.push(`${minutes}m`);
  parts.push(`${seconds}s`);
  return parts.join(' ');
}

export default function HealthDashboard() {
  const [state, setState] = useState<FetchState>({ phase: 'loading' });
  const [lastChecked, setLastChecked] = useState<Date | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const res = await fetch('/health', { cache: 'no-store' });
        if (!res.ok) throw new Error('non-2xx');
        const data: HealthPayload = await res.json();
        if (!cancelled) {
          setState({ phase: 'ok', data });
          setLastChecked(new Date());
        }
      } catch {
        if (!cancelled) {
          setState({ phase: 'unreachable' });
          setLastChecked(new Date());
        }
      }
    }

    poll();
    const id = setInterval(poll, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const overall: 'ok' | 'degraded' | 'unreachable' =
    state.phase === 'ok' ? state.data.status : state.phase === 'unreachable' ? 'unreachable' : 'ok';

  return (
    <main className="page">
      <div className="header">
        <h1>SpendSync Platform Status</h1>
        <p className="subtitle">Backend health monitor — refreshes every 5s</p>
      </div>

      <div className={`overall-badge overall-${overall}`}>
        <span className="dot" />
        {overall === 'ok' && 'All systems operational'}
        {overall === 'degraded' && 'Degraded — see details below'}
        {overall === 'unreachable' && 'Backend unreachable'}
      </div>

      {state.phase === 'loading' && <p className="muted">Checking status…</p>}

      {state.phase === 'unreachable' && (
        <div className="card card-error">
          <h2>API</h2>
          <p>Could not reach the backend. It may be down or still starting up.</p>
        </div>
      )}

      {state.phase === 'ok' && (
        <div className="grid">
          <div className="card">
            <h2>API</h2>
            <p className="metric ok">Reachable</p>
            <p className="detail">Environment: {state.data.environment}</p>
          </div>

          <div className={`card ${state.data.db.connected ? '' : 'card-error'}`}>
            <h2>Database</h2>
            <p className={`metric ${state.data.db.connected ? 'ok' : 'error'}`}>
              {state.data.db.connected ? 'Connected' : 'Disconnected'}
            </p>
            <p className="detail">Latency: {state.data.db.latencyMs}ms</p>
          </div>

          <div className="card">
            <h2>Uptime</h2>
            <p className="metric">{formatUptime(state.data.uptimeSeconds)}</p>
            <p className="detail">Since last restart</p>
          </div>

          <div className="card">
            <h2>Memory</h2>
            <p className="metric">{state.data.memory.rssMB} MB</p>
            <p className="detail">
              Heap {state.data.memory.heapUsedMB} / {state.data.memory.heapTotalMB} MB
            </p>
          </div>
        </div>
      )}

      {lastChecked && (
        <p className="last-checked">Last checked: {lastChecked.toLocaleTimeString()}</p>
      )}
    </main>
  );
}
