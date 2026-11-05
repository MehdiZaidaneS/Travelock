-- Initial schema for a new database; these tables already exist in the shared project.
-- Create locations before destinations because destinations reference locations.
create table public.locations (
  location_id uuid not null default gen_random_uuid (),
  name text not null,
  description text not null,
  latitude double precision not null,
  longitude double precision not null,
  image text not null,
  constraint locations_pkey primary key (location_id)
) TABLESPACE pg_default;


create table public.destinations (
  destination_id uuid not null default gen_random_uuid (),
  location_id uuid not null,
  name text not null,
  description text not null,
  discount real not null default '0'::real,
  latitude double precision not null,
  longitude double precision not null,
  image text not null,
  qr_code text not null,
  constraint destinations_pkey primary key (destination_id),
  constraint destinations_location_id_fkey foreign KEY (location_id) references locations (location_id),
  constraint destinations_discount_range check (
    (
      (discount >= (0)::double precision)
      and (discount <= (100)::double precision)
    )
  )
) TABLESPACE pg_default;

-- Enable row-level security for both catalogue tables.
alter table public.locations enable row level security;
alter table public.destinations enable row level security;

-- Visitors and signed-in users can read catalogue data, but cannot modify it.
grant usage on schema public to anon, authenticated;
revoke all on public.locations, public.destinations from anon, authenticated;
grant select on public.locations, public.destinations to anon, authenticated;

-- Allow everyone to browse all locations.
create policy "Anyone can read locations"
on public.locations
for select
to anon, authenticated
using (true);

-- Allow everyone to browse all destinations.
create policy "Anyone can read destinations"
on public.destinations
for select
to anon, authenticated
using (true);
