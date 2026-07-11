import { AdminShell } from "@/components/admin-shell";
import { Dashboard } from "@/components/dashboard";
import { requireAdmin } from "@/lib/auth";
import { getDashboardData } from "@/lib/dashboard-data";

export const dynamic = "force-dynamic";

export default async function OverviewPage() {
  const admin = await requireAdmin();
  const data = await getDashboardData();
  return <AdminShell active="/overview" role={admin.role}><Dashboard data={data}/></AdminShell>;
}
