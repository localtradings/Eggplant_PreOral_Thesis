import { createHmac } from "node:crypto";

export function requestRateSubject(
  request: Request,
  secret: string | undefined,
  vercelDeployment = process.env.VERCEL === "1",
) {
  if (!secret || secret.length < 32) {
    throw new Error("Rate-limit hashing is not configured.");
  }
  // Vercel overwrites x-vercel-forwarded-for at the edge. Do not fall back to
  // the client-spoofable generic header in production deployments.
  const forwarded = vercelDeployment
    ? request.headers.get("x-vercel-forwarded-for")
    : request.headers.get("x-vercel-forwarded-for")
      ?? request.headers.get("x-forwarded-for");
  const address = forwarded?.split(",", 1)[0]?.trim();
  if (!address || address.length > 128) {
    throw new Error("A trusted client address is unavailable.");
  }
  return createHmac("sha256", secret).update(address).digest("hex");
}
