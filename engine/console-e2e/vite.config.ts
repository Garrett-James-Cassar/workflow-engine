import { defineConfig } from 'vite';

// Builds the Demo player into the console's static folder. The console loads it only when
// GET /demo/status answers 200 (dev and demo, never live). Stable file names so the committed output diffs.
export default defineConfig({
  build: {
    outDir: '../app/src/main/resources/static/console-demo',
    emptyOutDir: true,
    target: 'es2022',
    minify: true,
    rollupOptions: {
      input: 'src/demo.ts',
      output: { entryFileNames: 'demo.js', chunkFileNames: '[name].js', assetFileNames: '[name][extname]' }
    }
  }
});
