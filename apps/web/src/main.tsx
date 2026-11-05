import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import '@fontsource/fraunces/400.css';
import '@fontsource/fraunces/500.css';
import '@fontsource/hanken-grotesk/400.css';
import '@fontsource/hanken-grotesk/500.css';
import '@fontsource/hanken-grotesk/600.css';
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/500.css';
import './index.css';
import { App } from './App';
import { ApiProvider } from './api';
import { ToastProvider } from './components/ui/toast';
import { MODE } from './lib/mode';
import { registerRemount, registerReset } from './demo/host';

let root: Root | null = null;
let generation = 0;

function render(): void {
  generation += 1;
  root?.unmount();
  root = createRoot(document.getElementById('root')!);
  root.render(
    <StrictMode>
      <ApiProvider><ToastProvider><BrowserRouter key={generation}><App /></BrowserRouter></ToastProvider></ApiProvider>
    </StrictMode>
  );
}

async function boot(): Promise<void> {
  if (MODE !== 'live') {
    const mock = await import('./mocks/browser');
    await mock.startMock();
    registerReset(mock.resetStore);
  }
  registerRemount(render);
  render();
}

void boot();
