// The audit log (sec.6): read and export only. There is no route that changes or deletes a row.
import type { AuditEntry } from '../api/types';
import { intParam, ok, problem, type Ctx, type MockResponse, type Route } from './http';
import { auditDoc } from './portable';
import { allOf, between, eq, page } from './query';
import { permissionsOf } from './rbac';

const mayRead = (ctx: Ctx, permission: string) => permissionsOf(ctx.user).includes(permission);

function filter(q: URLSearchParams) {
  return allOf<AuditEntry>([eq(q.get('actor'), (a) => a.actor), eq(q.get('action'), (a) => a.action), eq(q.get('resource'), (a) => a.resource), between(q.get('from'), q.get('to'), (a) => a.at)]);
}

function listAudit(ctx: Ctx): MockResponse {
  if (!mayRead(ctx, 'audit.read')) return problem(ctx, 403, `${ctx.user || 'This caller'} may not read the audit log.`, undefined, 'audit.read is needed');
  const q = ctx.query;
  return ok(page(ctx.store.audit().filter(filter(q)), intParam(q, 'offset', 0), Math.min(500, intParam(q, 'limit', 100))));
}

function exportAudit(ctx: Ctx): MockResponse {
  if (!mayRead(ctx, 'audit.export')) return problem(ctx, 403, `${ctx.user || 'This caller'} may not export the audit log.`, undefined, 'audit.export is needed');
  return ok(ctx.store.audit().map(auditDoc));
}

export const auditRoutes: Route[] = [
  { method: 'GET', path: '/audit', handle: listAudit },
  { method: 'GET', path: '/audit/export', handle: exportAudit }
];
