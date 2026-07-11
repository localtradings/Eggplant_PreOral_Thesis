import "server-only";
import { redirect } from "next/navigation";
import { getAdminClient } from "@/lib/supabase/admin";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export type AdminRole = "owner" | "admin" | "reviewer";

export async function requireAdmin(allowedRoles?: readonly AdminRole[]) {
  const supabase = await createSupabaseServerClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) redirect("/login");
  const { data: member, error: memberError } = await getAdminClient()
    .from("admin_members")
    .select("role")
    .eq("user_id", user.id)
    .maybeSingle();
  if (memberError) {
    throw new Error("Admin authorization is temporarily unavailable.");
  }
  if (!member) redirect("/login?error=not_authorized");
  const role = member.role as AdminRole;
  if (allowedRoles && !allowedRoles.includes(role)) {
    redirect("/overview?error=forbidden");
  }
  return { user, role };
}

export async function verifyMobileUser(request: Request) {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) return null;
  const token = authorization.slice(7);
  if (token.length < 20 || token.length > 8_192 || /\s/.test(token)) return null;
  const { data: { user }, error } = await getAdminClient().auth.getUser(token);
  if (error || !user || user.is_anonymous !== true) return null;
  return user;
}

export async function cloudWritesEnabled() {
  const { data, error } = await getAdminClient()
    .from("app_config")
    .select("cloud_writes_enabled")
    .eq("id", true)
    .maybeSingle();
  return !error && data?.cloud_writes_enabled === true;
}
