// ?scenario=empty|error|slow: make every view's empty, error and loading
// states reachable on purpose. Read once when the mock starts.
import { problem, type MockResponse } from './http';

export type Scenario = 'normal' | 'empty' | 'error' | 'slow';

export function parseScenario(search: string): Scenario {
  const v = new URLSearchParams(search).get('scenario');
  return v === 'empty' || v === 'error' || v === 'slow' ? v : 'normal';
}

/** What every call answers in the error scenario. */
export const outage = (traceId: string): MockResponse =>
  problem({ traceId }, 503, 'The workflow engine did not answer within 10 s.');

/** Simulated latency in ms: 150–400 normally, 4 s in the slow scenario. */
export const latency = (scenario: Scenario, r: number): number => (scenario === 'slow' ? 4000 : 150 + Math.round(r * 250));
