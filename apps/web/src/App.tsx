import { Navigate, Route, Routes } from 'react-router-dom';
import { AccessGate } from './components/AccessGate';
import { Layout } from './components/Layout';
import { ConfigurationPage } from './routes/configuration/ConfigurationPage';
import { NotFoundPage } from './routes/NotFoundPage';
import { EventsPage } from './routes/events/EventsPage';
import { AuditPage } from './routes/audit/AuditPage';
import { IncidentsPage } from './routes/incidents/IncidentsPage';
import { InstancePage } from './routes/instance/InstancePage';
import { InstancesPage } from './routes/instances/InstancesPage';
import { TasksPage } from './routes/tasks/TasksPage';
import { WorkflowPage } from './routes/workflow/WorkflowPage';
import { WorkflowsPage } from './routes/workflows/WorkflowsPage';

export function App() {
  return (
    <Layout>
      <AccessGate>
      <Routes>
        <Route path="/" element={<Navigate to="/workflows" replace />} />
        <Route path="/workflows" element={<WorkflowsPage />} />
        <Route path="/workflows/:name" element={<WorkflowPage />} />
        <Route path="/tasks" element={<TasksPage />} />
        <Route path="/events" element={<EventsPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/audit" element={<AuditPage />} />
        <Route path="/instances" element={<InstancesPage />} />
        <Route path="/instances/:id" element={<InstancePage />} />
        <Route path="/configuration" element={<ConfigurationPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </AccessGate>
    </Layout>
  );
}
