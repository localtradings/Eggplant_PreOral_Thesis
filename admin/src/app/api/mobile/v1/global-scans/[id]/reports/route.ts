import { NextResponse } from "next/server";
import { apiError, authorizeMobile, mobileRateSubject, parseJson } from "@/lib/mobile-api";
import {
  UUID_PATTERN,
  validateContentReport,
} from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const auth = await authorizeMobile(request, true);
  if ("response" in auth) return auth.response;
  const { id } = await context.params;
  const validation = validateContentReport(await parseJson<unknown>(request));
  if (!UUID_PATTERN.test(id) || !validation.ok) {
    return apiError("Invalid report.", 400, "invalid_report");
  }

  let rateSubject: string;
  try {
    rateSubject = mobileRateSubject(request);
  } catch {
    return apiError("Report protection is temporarily unavailable.", 503, "rate_limit_unavailable");
  }
  const { data: outcome, error } = await getAdminClient().rpc(
    "report_scan_contribution",
    {
      p_reporter_id: auth.user.id,
      p_contribution_id: id,
      p_reason: validation.value.reason,
      p_details: validation.value.details,
      p_rate_subject: rateSubject,
    },
  );
  if (error || typeof outcome !== "string") {
    return apiError("Could not submit the report.", 500, "report_failed");
  }
  if (outcome === "unavailable") return apiError("This Global Scan is no longer available.", 404, "scan_not_found");
  if (outcome === "self_report") return apiError("You cannot report your own shared scan.", 400, "self_report");
  if (outcome === "quota") return apiError("Daily report limit reached.", 429, "report_limit");
  if (outcome === "rate_duplicate") return apiError("This network has already reported this scan.", 409, "report_duplicate_network");
  return NextResponse.json(
    { accepted: outcome === "accepted", duplicate: outcome === "duplicate" },
    { status: outcome === "accepted" ? 202 : 200 },
  );
}
