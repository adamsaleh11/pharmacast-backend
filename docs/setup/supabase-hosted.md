# Hosted Supabase Setup

This backend is designed to use a hosted Supabase project so you can inspect Auth users, database tables, SQL, logs, and API activity from the Supabase Dashboard.

## 1. Create the Supabase Project

1. Go to `https://supabase.com/dashboard`.
2. Create a new project.
3. Choose the Canada region if it is available for your plan and account.
4. Save the database password in a password manager.
5. Wait until the project finishes provisioning.

## 2. Copy Project Values

From the Supabase Dashboard:

1. Go to **Project Settings -> API**.
2. Copy:
   - Project URL, like `https://<project-ref>.supabase.co`
   - Publishable or anon key for the frontend
3. Go to **Project Settings -> Database -> Connection string**.
4. Copy the database host, port, database name, user, and password.

For Spring Boot, use these environment variables:

```sh
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<supabase-db-host>:<port>/<database-name>?sslmode=require&prepareThreshold=0
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
SUPABASE_JWKS_URI=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_JWT_ISSUER=https://<project-ref>.supabase.co/auth/v1
SUPABASE_JWT_AUDIENCE=authenticated
```

If Supabase gives you a pooled connection string, the username often looks like `postgres.<project-ref>`. If it gives you a direct connection string, the username is often `postgres`. Use exactly what the Supabase Dashboard shows.

For Supabase pooled connections, keep `prepareThreshold=0` in the JDBC URL or rely on the backend's `spring.datasource.hikari.data-source-properties.prepareThreshold=0` setting. This disables PostgreSQL server-side prepared statements, which avoids `prepared statement "S_1" already exists` errors through the pooler.

## 3. Apply Backend Migrations

The backend uses Flyway. The simplest beginner path is:

1. Set the environment variables from section 2.
2. Start the backend with the prod profile:

```sh
mvn spring-boot:run
```

On startup, Flyway applies:

- `V1__foundation_schema.sql`
- `V2__auth_bootstrap.sql`

Then open the Supabase Dashboard:

1. Go to **Table Editor**.
2. Confirm these tables exist:
   - `organizations`
   - `locations`
   - `app_users`
   - `notification_settings`
3. Go to **SQL Editor** and confirm this function exists:
   - `bootstrap_first_owner_user`

## 4. Configure Supabase Auth

In the Supabase Dashboard:

1. Go to **Authentication -> Providers**.
2. Enable **Email**.
3. For local development, either disable email confirmations or configure the email redirect URLs first.
4. Go to **Authentication -> URL Configuration**.
5. Set your site URL and redirect URLs for the frontend.

Suggested local frontend URLs:

```text
http://localhost:3000
http://127.0.0.1:3000
```

## 5. Frontend Signup Metadata Contract

The frontend should collect these fields during signup/onboarding:

```json
{
  "organization_name": "Main Pharmacy",
  "location_name": "Main Pharmacy - Bank",
  "location_address": "100 Bank St, Ottawa, ON"
}
```

The frontend should:

1. Sign the user up through Supabase Auth.
2. Get the Supabase session access token.
3. Call this backend:

```http
POST /auth/bootstrap
Authorization: Bearer <supabase_access_token>
Content-Type: application/json

{
  "organization_name": "Main Pharmacy",
  "location_name": "Main Pharmacy - Bank",
  "location_address": "100 Bank St, Ottawa, ON"
}
```

Then the frontend can call:

```http
GET /auth/me
Authorization: Bearer <supabase_access_token>
```

## 6. Manual Smoke Test Without Frontend

Set these shell variables:

```sh
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable-or-anon-key>
BACKEND_URL=http://localhost:8080
TEST_EMAIL=owner@example.com
TEST_PASSWORD='Use-a-real-test-password-123'
```

Create a Supabase Auth user:

```sh
curl -s -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}"
```

Sign in and copy the `access_token` from the response:

```sh
curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}"
```

Bootstrap the PharmaForecast tenant:

```sh
ACCESS_TOKEN=<paste-access-token>

curl -i -X POST "$BACKEND_URL/auth/bootstrap" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "organization_name": "Main Pharmacy",
    "location_name": "Main Pharmacy - Bank",
    "location_address": "100 Bank St, Ottawa, ON"
  }'
```

Check the current user:

```sh
curl -i "$BACKEND_URL/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## 7. What You Should See in Supabase UI

After bootstrap succeeds, open the Supabase Dashboard:

1. **Authentication -> Users**
   - You should see the signed-up email.
2. **Table Editor -> organizations**
   - You should see one organization.
3. **Table Editor -> locations**
   - You should see one location for that organization.
4. **Table Editor -> app_users**
   - You should see one row.
   - `id` must match the Supabase Auth user id.
   - `role` must be `owner`.
5. **Table Editor -> notification_settings**
   - You should see one row for the organization.

## 8. Important Security Notes

- Do not put the Supabase service role key in the frontend.
- Do not use Supabase user metadata for authorization.
- Spring Boot validates JWT identity but loads `organization_id` and `role` from `app_users`.
- `patient_id` must never be sent to LLM services, logs, exports, prompts, chat context, or purchase orders.
