"use client";

import { createBrowserClient } from "@supabase/ssr";
import { CheckCircle2, Loader2, Mail } from "lucide-react";
import { useState } from "react";

export function LoginForm() {
  const [email, setEmail] = useState("");
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [message, setMessage] = useState("");
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setState("sending");
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
    if (!url || !key) { setMessage("Admin authentication is not configured."); setState("error"); return; }
    const supabase = createBrowserClient(url, key);
    try {
      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: { shouldCreateUser: false, emailRedirectTo: `${window.location.origin}/auth/callback` },
      });
      if (error) { setMessage("We could not send the magic link. Verify the address and try again."); setState("error"); } else { setState("sent"); }
    } catch {
      setMessage("We could not send the magic link. Verify the address and try again.");
      setState("error");
    }
  }
  return <form onSubmit={submit} className="mt-7 space-y-4">
    <label className="block"><span className="mb-2 block text-sm font-semibold">Admin email</span><div className="flex items-center gap-2 rounded-xl border border-[#ded9e8] bg-white px-3 focus-within:ring-3 focus-within:ring-[#512b91]/20"><Mail size={18} className="text-[#777286]"/><input required type="email" autoComplete="email" value={email} onChange={(e)=>setEmail(e.target.value)} className="h-12 min-w-0 flex-1 outline-none" placeholder="admin@example.com"/></div></label>
    <button disabled={state === "sending" || state === "sent"} className="focus-ring flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#512b91] font-semibold text-white shadow-[0_10px_24px_rgba(81,43,145,.22)] transition-transform hover:-translate-y-0.5 disabled:opacity-60">{state === "sending" ? <Loader2 className="animate-spin" size={18}/> : state === "sent" ? <CheckCircle2 size={18}/> : <Mail size={18}/>} {state === "sent" ? "Check your inbox" : "Send magic link"}</button>
    <div aria-live="polite" aria-atomic="true">{state === "error" && <p role="alert" className="text-sm text-[#b52f42]">{message}</p>}{state === "sent" && <p className="text-sm font-semibold text-[#247936]">A sign-in link was requested. Check your inbox.</p>}</div>
    <p className="text-xs leading-5 text-[#777286]">Only allowlisted administrators can sign in. No password is stored by this dashboard.</p>
  </form>;
}
