// Picks the ApiPort adapter for this profile and hands it to components.
import { createContext, useContext, type ReactNode } from 'react';
import { API_BASE_URL } from '../lib/mode';
import { createHttpAdapter } from './http-adapter';
import type { ApiPort } from './port';

// mock/demo: same-origin /api/v1, answered by MSW. live: VITE_API_BASE_URL.
const defaultApi: ApiPort = createHttpAdapter(API_BASE_URL);
const ApiContext = createContext<ApiPort>(defaultApi);

export function ApiProvider({ api = defaultApi, children }: { api?: ApiPort; children: ReactNode }) {
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>;
}

export const useApi = (): ApiPort => useContext(ApiContext);
export type { ApiPort } from './port';
export { ApiProblem } from './problem';
