// Starts the mock backend in this tab. Imported lazily, so a live build never ships it.
import { setupWorker } from 'msw/browser';
import { handlers, resetStore, store } from './handlers';

export async function startMock(): Promise<void> {
  resetStore();
  await setupWorker(...handlers).start({ onUnhandledRequest: 'bypass', quiet: true });
  (window as unknown as { __workflowEngine?: unknown }).__workflowEngine = { store, resetStore };
}

export { resetStore };
