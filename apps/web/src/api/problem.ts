// RFC 7807 problems as a typed error. Every ApiPort call rejects with ApiProblem.
import type { FieldError, ProblemBody } from './types';

export class ApiProblem extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly traceId: string;
  readonly errors: FieldError[];
  readonly reason: string;

  constructor(body: ProblemBody) {
    super(body.detail ?? body.title);
    this.status = body.status;
    this.title = body.title;
    this.detail = body.detail ?? '';
    this.traceId = body.traceId;
    this.errors = body.errors ?? [];
    this.reason = body.reason ?? '';
  }
}

const isProblem = (v: unknown): v is ProblemBody =>
  typeof v === 'object' && v !== null && 'title' in v && 'status' in v;

/** Any failure (problem body, other body, network) as an ApiProblem. */
export function toProblem(body: unknown, status: number, traceId = ''): ApiProblem {
  if (isProblem(body)) return new ApiProblem({ ...body, traceId: body.traceId || traceId });
  return new ApiProblem({
    type: 'about:blank', status, traceId,
    title: status === 0 ? 'Could not reach the workflow engine' : `The workflow engine answered ${status}`,
    detail: status === 0 ? 'The request did not get a response. Check the API is running and reachable.' : 'The response was not a problem document.'
  });
}

export const fieldError = (p: unknown, path: string): string | undefined =>
  p instanceof ApiProblem ? p.errors.find((e) => e.path === path)?.message : undefined;
