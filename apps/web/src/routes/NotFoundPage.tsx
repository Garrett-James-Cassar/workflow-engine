import { Link } from 'react-router-dom';
import { EmptyState } from '../components/states/EmptyState';

export function NotFoundPage() {
  return <EmptyState headline="This page does not exist" text="The address may be mistyped, or the page has moved." action={<Link to="/workflows" className="text-primary underline">Go to workflows</Link>} />;
}
