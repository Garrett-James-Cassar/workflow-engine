// ApiPort: the only API surface components see. One adapter per backend:
// http-adapter.ts speaks contract/openapi.yaml (live, and mock/demo through
// MSW). A facade over the real engine is a new adapter, nothing else.
import type {
  AuditEntry, AuditListQuery, CompleteTaskRequest, Diagram, EventAccepted, EventRequest, Incident, IncidentExportQuery, IncidentListQuery, Instance,
  ImportPlan, ImportResult, InstanceEvent, InstanceExportQuery, InstanceListQuery, InstanceStats, Me, Page, Person, PortableDoc, ReassignRequest,
  StartRequest, Task, TaskListQuery, Wait, WaitListQuery, Workflow, WorkflowListQuery, WorkflowSummary
} from './types';

export type ImportScope = 'workflows' | 'bundle';

export interface ApiPort {
  listWorkflows(query: WorkflowListQuery): Promise<Page<WorkflowSummary>>;
  getWorkflow(name: string, version?: number): Promise<Workflow>;
  workflowDiagram(name: string, version?: number): Promise<Diagram>;
  exportWorkflows(): Promise<PortableDoc[]>;
  exportWorkflow(key: string): Promise<PortableDoc>;
  /** The Config kinds that can be imported, by plural. `bundle` is every kind at once (the promotion screen). */
  previewImport(scope: ImportScope, docs: unknown): Promise<ImportPlan>;
  importDocs(scope: ImportScope, docs: unknown): Promise<ImportResult>;
  exportBundle(): Promise<PortableDoc[]>;
  me(): Promise<Me>;
  startInstance(name: string, body: StartRequest): Promise<Instance>;
  listInstances(query: InstanceListQuery): Promise<Page<Instance>>;
  instanceStats(): Promise<InstanceStats>;
  getInstance(id: string): Promise<Instance>;
  instanceEvents(id: string): Promise<InstanceEvent[]>;
  exportInstances(query: InstanceExportQuery): Promise<PortableDoc[]>;
  exportInstance(id: string): Promise<PortableDoc>;
  retry(id: string): Promise<Instance>;
  restart(id: string, step: string): Promise<Instance>;
  fireTimeout(id: string, step: string): Promise<Instance>;
  listTasks(query: TaskListQuery): Promise<Page<Task>>;
  completeTask(id: string, body: CompleteTaskRequest): Promise<Instance>;
  reassignTask(id: string, body: ReassignRequest): Promise<Instance>;
  listWaits(query: WaitListQuery): Promise<Page<Wait>>;
  sendEvent(name: string, body: EventRequest): Promise<EventAccepted>;
  listIncidents(query: IncidentListQuery): Promise<Page<Incident>>;
  exportIncidents(query: IncidentExportQuery): Promise<PortableDoc[]>;
  listAudit(query: AuditListQuery): Promise<Page<AuditEntry>>;
  exportAudit(): Promise<PortableDoc[]>;
  exportWaits(): Promise<PortableDoc[]>;
  people(): Promise<Person[]>;
}
