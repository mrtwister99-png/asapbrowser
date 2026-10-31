-- ASAP Browser: tabulka pro hodnoceni a hlaseni chyb z aplikace.
-- Spusteni: Supabase -> SQL Editor -> New query -> vloz cely soubor -> Run.

create table if not exists public.feedback (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  type        text        not null check (type in ('review', 'bug')),
  rating      smallint    not null default 0 check (rating between 0 and 5),
  message     text        not null check (char_length(message) between 5 and 2000),
  contact     text        check (contact is null or char_length(contact) <= 200),
  app_version text,
  android     text,
  device      text,
  ip_hash     text,                          -- jen otisk adresy (SHA-256), slouzi k omezeni spamu
  approved    boolean     not null default false   -- pro pripadne verejne recenze (zatim nepouzito)
);

-- Zapnuta ochrana radku BEZ jedine policy = klic "anon" z aplikace nema do tabulky zadny pristup.
-- Zapisuje a cte jen Edge Function submit-feedback pres servisni klic (ten obchazi RLS) a ty v dashboardu.
alter table public.feedback enable row level security;

-- Servisni role (Edge Function) potrebuje prava na tabulku explicitne, kdyz je v projektu vypnute
-- "Automatically expose new tables". Role anon a authenticated zamerne zadna prava nemaji.
grant select, insert on public.feedback to service_role;
grant usage, select on all sequences in schema public to service_role;

create index if not exists feedback_ip_time on public.feedback (ip_hash, created_at);
