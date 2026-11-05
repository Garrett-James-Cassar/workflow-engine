// AG Grid infinite row model ↔ the contract's offset/limit/sort/order paging.
import type { IDatasource, IGetRowsParams } from 'ag-grid-community';
import type { Page } from '../../api/types';

export type PageQuery = { offset: number; limit: number; sort?: string; order?: 'asc' | 'desc' };
export type FetchPage<T> = (q: PageQuery) => Promise<Page<T>>;
export type GridStatus = { state: 'loading' | 'ready' | 'empty' | 'error'; error?: unknown };

export function toPageQuery(p: Pick<IGetRowsParams, 'startRow' | 'endRow' | 'sortModel'>): PageQuery {
  const s = p.sortModel[0];
  return { offset: p.startRow, limit: p.endRow - p.startRow, ...(s ? { sort: s.colId, order: s.sort } : {}) };
}

export function infiniteDatasource<T>(fetchPage: FetchPage<T>, onStatus: (s: GridStatus) => void): IDatasource {
  return {
    getRows(p) {
      fetchPage(toPageQuery(p)).then(
        (page) => {
          p.successCallback(page.items, page.total);
          if (p.startRow === 0) onStatus({ state: page.total === 0 ? 'empty' : 'ready' });
        },
        (error: unknown) => {
          p.failCallback();
          onStatus({ state: 'error', error });
        }
      );
    }
  };
}
