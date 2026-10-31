// Supabase Edge Function: submit-feedback
// Prijme hodnoceni / hlaseni chyby z aplikace ASAP Browser, ulozi ho do tabulky `feedback`
// a posle e-mail pres Resend.
//
// Tajne promenne (Supabase -> Edge Functions -> Secrets):
//   RESEND_API_KEY     klic z Resendu (jen tady, NIKDY v aplikaci)
//   FEEDBACK_TO_EMAIL  kam maji chodit zpravy (na free planu Resendu to musi byt e-mail, se kterym jsi se tam registroval)
//   IP_SALT            (nepovinne) libovolny retezec pro otisk adres
// SUPABASE_URL a SUPABASE_SERVICE_ROLE_KEY dodava Supabase automaticky.

import { createClient } from "npm:@supabase/supabase-js@2";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

async function sha256(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

const clip = (v: unknown, n: number) => String(v ?? "").trim().slice(0, n);

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let b: Record<string, unknown>;
  try {
    b = await req.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }

  // kontrola vstupu (aplikace posila totez, ale server nikdy nevěří klientovi)
  const type = b.type === "bug" ? "bug" : "review";
  const rating = Math.round(Number(b.rating) || 0);
  const message = clip(b.message, 2000);
  const contact = clip(b.contact, 200);
  if (message.length < 5) return json({ error: "message_too_short" }, 400);
  if (rating < 0 || rating > 5 || (type === "review" && rating < 1)) return json({ error: "bad_rating" }, 400);

  // omezeni spamu: nejvic 5 zprav za hodinu z jedne adresy (uklada se jen otisk adresy)
  const ip = (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "unknown";
  const ipHash = await sha256(ip + (Deno.env.get("IP_SALT") ?? ""));
  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const since = new Date(Date.now() - 3600_000).toISOString();
  const { count } = await db
    .from("feedback")
    .select("id", { count: "exact", head: true })
    .eq("ip_hash", ipHash)
    .gte("created_at", since);
  if ((count ?? 0) >= 5) return json({ error: "too_many" }, 429);

  const row = {
    type,
    rating,
    message,
    contact: contact || null,
    app_version: clip(b.app_version, 120) || null,
    android: clip(b.android, 120) || null,
    device: clip(b.device, 120) || null,
    ip_hash: ipHash,
  };
  const { error } = await db.from("feedback").insert(row);
  if (error) {
    console.error("insert failed", error);
    return json({ error: "db_error" }, 500);
  }

  // e-mail (kdyz selze, zprava je i tak ulozena v tabulce); stav se vraci v odpovedi: sent / failed_<kod> / error / skipped
  const resendKey = Deno.env.get("RESEND_API_KEY");
  const to = Deno.env.get("FEEDBACK_TO_EMAIL");
  let email = "skipped";
  if (!resendKey || !to) console.warn("RESEND_API_KEY nebo FEEDBACK_TO_EMAIL chybi, e-mail se neposila");
  if (resendKey && to) {
    try {
      const stars = rating > 0 ? "★".repeat(rating) + "☆".repeat(5 - rating) : "bez hodnocení";
      const replyTo = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact) ? contact : undefined;
      const res = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { Authorization: `Bearer ${resendKey}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "ASAP Browser <onboarding@resend.dev>",
          to: [to],
          reply_to: replyTo,
          subject: `[ASAP Browser] ${type === "bug" ? "Chyba" : "Recenze"} ${stars}`,
          html:
            `<p><b>${type === "bug" ? "Hlášení chyby" : "Recenze"}</b> · ${stars}</p>` +
            `<p style="white-space:pre-wrap">${esc(message)}</p><hr>` +
            `<p>Kontakt: ${esc(contact || "—")}<br>Verze: ${esc(row.app_version ?? "—")}<br>` +
            `Android: ${esc(row.android ?? "—")}<br>Telefon: ${esc(row.device ?? "—")}</p>`,
        }),
      });
      if (res.ok) email = "sent";
      else {
        email = "failed_" + res.status;
        console.error("resend failed", res.status, await res.text());
      }
    } catch (e) {
      email = "error";
      console.error("resend error", e);
    }
  }

  return json({ ok: true, email });
});
