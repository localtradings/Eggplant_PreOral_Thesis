import { describe, expect, it } from "vitest";
import { requestRateSubject } from "./request-security";

const secret = "a".repeat(64);

describe("requestRateSubject", () => {
  it("is stable without retaining the raw address", () => {
    const request = new Request("https://example.test", {
      headers: { "x-vercel-forwarded-for": "203.0.113.8" },
    });
    const subject = requestRateSubject(request, secret);
    expect(subject).toMatch(/^[a-f0-9]{64}$/);
    expect(subject).toBe(requestRateSubject(request, secret));
    expect(subject).not.toContain("203.0.113.8");
  });

  it("prefers Vercel's trusted header over a forwarded fallback", () => {
    const trusted = new Request("https://example.test", {
      headers: {
        "x-vercel-forwarded-for": "203.0.113.8",
        "x-forwarded-for": "198.51.100.4",
      },
    });
    const direct = new Request("https://example.test", {
      headers: { "x-vercel-forwarded-for": "203.0.113.8" },
    });
    expect(requestRateSubject(trusted, secret)).toBe(requestRateSubject(direct, secret));
  });

  it("fails closed without a trusted address or hashing secret", () => {
    expect(() => requestRateSubject(new Request("https://example.test"), secret)).toThrow();
    expect(() => requestRateSubject(new Request("https://example.test", {
      headers: { "x-vercel-forwarded-for": "203.0.113.8" },
    }), undefined)).toThrow();
  });

  it("rejects a spoofable generic forwarded header on Vercel", () => {
    const request = new Request("https://example.test", {
      headers: { "x-forwarded-for": "203.0.113.8" },
    });
    expect(() => requestRateSubject(request, secret, true)).toThrow();
  });
});
