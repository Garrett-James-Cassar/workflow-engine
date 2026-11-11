// Starts the mock backend in this tab. Imported lazily, so a live build never ships it.
import { setupWorker } from 'msw/browser';
import { handlers, resetStore, store } from './handlers';

export async function startMock(): Promise<void> {
  resetStore();
  await setupWorker(...handlers).start({
    quiet: true,
    // An API call the mocks do not answer must never fall through to the static server (a remote viewer would see a 404):
    // it is recorded for the spec and fails loudly. Everything else (assets, fonts, the worker script) passes.
    onUnhandledRequest: (request, print) => {
      if (!new URL(request.url).pathname.includes('/api/')) return;
      ((window as unknown as { __unhandledApi?: string[] }).__unhandledApi ??= []).push(`${request.method} ${request.url}`);
      print.error();
    }
  });
  (window as unknown as { __workflowEngine?: unknown }).__workflowEngine = { store, resetStore };
}

export { resetStore };
