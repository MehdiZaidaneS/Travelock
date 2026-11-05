# Supabase starter connection check

1. Add your project settings to the root `local.properties`, preserving `sdk.dir`:

   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_PUBLISHABLE_KEY=your-publishable-key
   ```

   This file is ignored by Git. Every teammate needs their own local settings.
   Use the publishable key, never a secret or service-role key. The publishable
   key is included in the built Android app; database access is controlled by RLS.

2. Review and run `starter.sql` once in your project's Supabase SQL Editor.
   It creates a publicly readable demo `todos` table with two rows. Client roles
   have no write access. If a `todos` table already exists, inspect it first;
   this script deliberately does not replace an existing table.
3. Verify the demo rows in Supabase with `select * from public.todos;`.
   The client and demo model are available in the Android project, but are not
   wired into `MainActivity`. App-to-database verification is still pending.

The starter installs only the Postgrest database module and an Android Ktor engine.
Authentication, the main Travelock models, and their tables are separate work.
Database SQL and app integration belong in shared Git; personal Codex setup and
reference assets remain local.

Reference: https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
