import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { NotFoundPage } from './routes/NotFoundPage';
import { InstancePage } from './routes/instance/InstancePage';
import { InstancesPage } from './routes/instances/InstancesPage';
import { WorkflowPage } from './routes/workflow/WorkflowPage';
import { WorkflowsPage } from './routes/workflows/WorkflowsPage';

export function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/workflows" replace />} />
        <Route path="/workflows" element={<WorkflowsPage />} />
        <Route path="/workflows/:name" element={<WorkflowPage />} />
        <Route path="/instances" element={<InstancesPage />} />
        <Route path="/instances/:id" element={<InstancePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  );
}
