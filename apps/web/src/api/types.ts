// Domain types the UI uses, all aliases of the generated contract schema
// (contract/openapi.yaml → generated/schema.d.ts). Never hand-written shapes.
import type { components, operations } from './generated/schema';

type S = components['schemas'];

export type WorkflowSummary = S['WorkflowSummary'];
export type Workflow = S['Workflow'];
export type Graph = S['Graph'];
export type GraphNode = S['GraphNode'];
export type GraphEdge = S['GraphEdge'];
export type Diagram = S['Diagram'];
export type Instance = S['Instance'];
export type InstanceStatus = S['InstanceStatus'];
export type InstanceStats = S['InstanceStats'];
export type InstanceEvent = S['InstanceEvent'];
export type InstanceWait = S['InstanceWait'];
export type Task = S['Task'];
export type FormField = S['FormField'];
export type Wait = S['Wait'];
export type Incident = S['Incident'];
export type AuditEntry = S['AuditEntry'];
export type Person = S['Person'];
export type PortableDoc = S['PortableDoc'];
export type ImportPlan = S['ImportPlan'];
export type ImportPlanEntry = S['ImportPlanEntry'];
export type ImportResult = S['ImportResult'];
export type Me = S['Me'];
export type StartRequest = S['StartRequest'];
export type CompleteTaskRequest = S['CompleteTaskRequest'];
export type ReassignRequest = S['ReassignRequest'];
export type EventRequest = S['EventRequest'];
export type EventAccepted = S['EventAccepted'];
export type FieldError = S['FieldError'];
export type ProblemBody = S['Problem'];

export type Page<T> = { items: T[]; total: number };

type Query<Op extends keyof operations> = NonNullable<operations[Op]['parameters']['query']>;
export type WorkflowListQuery = Query<'listWorkflows'>;
export type InstanceListQuery = Query<'listInstances'>;
export type TaskListQuery = Query<'listTasks'>;
export type WaitListQuery = Query<'listWaits'>;
export type IncidentListQuery = Query<'listIncidents'>;
export type AuditListQuery = Query<'listAudit'>;
export type InstanceExportQuery = Query<'exportInstances'>;
export type IncidentExportQuery = Query<'exportIncidents'>;
