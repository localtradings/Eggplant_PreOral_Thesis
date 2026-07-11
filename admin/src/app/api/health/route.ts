import { NextResponse } from "next/server";

export function GET() {
  return NextResponse.json({ status: "ok", service: "eggplant-disease-admin", writesConfigured: Boolean(process.env.SUPABASE_SECRET_KEY) });
}
