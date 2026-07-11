import { NextResponse } from "next/server";
import { apiError, authorizeMobile } from "@/lib/mobile-api";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(request: Request) {
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;
  const { data, error } = await getAdminClient()
    .from("disease_requests")
    .select("id,client_request_id,requested_name,notes,status,admin_note,created_at,updated_at,disease_request_photos(position,object_path)")
    .eq("owner_id", auth.user.id)
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return apiError("Could not load disease requests.", 500, "request_status_failed");
  return NextResponse.json({ items: data ?? [] });
}
