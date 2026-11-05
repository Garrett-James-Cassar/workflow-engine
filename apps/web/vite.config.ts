import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import istanbul from 'vite-plugin-istanbul';

// Port 5183 is the workflow studio's fixed fleet dev port (fleet/standards/ports.json).
// `vite build --mode demo` writes the static demo build (dist/ with index.html and
// mockServiceWorker.js): the mock and the Demo run in the viewer's tab, no backend.
// In live mode /api is proxied as-is to VITE_PROXY_TARGET.
const coverage = process.env.CYPRESS_COVERAGE === 'true';
const profile = (mode: string): string => process.env.VITE_MODE ?? (mode === 'demo' || mode === 'live' ? mode : 'mock');

export default defineConfig(({ mode }) => {
  const vite = profile(mode);
  return {
    define: { 'import.meta.env.VITE_MODE': JSON.stringify(vite) },
    plugins: [
      react(),
      ...(coverage
        ? [istanbul({ include: 'src/**/*', exclude: ['node_modules', 'src/mocks/**', 'src/demo/vendor/**', 'src/api/generated/**'], extension: ['.ts', '.tsx'], forceBuildInstrument: true })]
        : [])
    ],
    server: {
      port: 5183,
      strictPort: true,
      // The mock reads the engine's exported diagrams and graphs from ../../docs/workflows.
      fs: { allow: ['../..'] },
      proxy: vite === 'live' ? { '/api': process.env.VITE_PROXY_TARGET ?? 'http://localhost:47831' } : undefined
    },
    // allowedHosts: the demo build is also served to remote viewers (make share) under an ngrok hostname.
    preview: { port: 5183, strictPort: true, allowedHosts: true },
    build: { sourcemap: coverage }
  };
});
