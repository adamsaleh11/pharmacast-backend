# Frontend Authentication & User API Contract

This document specifies how the frontend should interact with the backend for authentication, user management, and CRUD operations.

---

## 1. Authentication Flow

### 1.1 Sign Up (New User)

The complete signup flow requires TWO calls:

**Step 1: Supabase Auth Signup**
```typescript
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(
  'https://ebrxagoygjtnpzlnxtmr.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
)

// Call Supabase Auth
const { data, error } = await supabase.auth.signUp({
  email: 'user@example.com',
  password: 'password123',
  options: {
    data: {
      organization_name: 'My Pharmacy',
      location_name: 'Main Branch',
      location_address: '123 Main St, Ottawa, ON'
    }
  }
})

// If error, show error message
// If no session: user needs to confirm email
// If has session: proceed to Step 2
```

**Step 2: Bootstrap (create profile in database)**
```typescript
// Only call this AFTER Step 1 returns a session
if (data.session) {
  const response = await fetch('http://localhost:8080/auth/bootstrap', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.session.access_token}`
    },
    body: JSON.stringify({
      organization_name: 'My Pharmacy',
      location_name: 'Main Branch',
      location_address: '123 Main St, Ottawa, ON'
    })
  })

  if (response.ok) {
    const { organization_id, location_id, user_id } = await response.json()
    // Success - user can now access dashboard
  }
}
```

### 1.2 Sign In (Existing User)

```typescript
const { data, error } = await supabase.auth.signInWithPassword({
  email: 'user@example.com',
  password: 'password123'
})

// After sign in, user can access protected endpoints
// No bootstrap needed - user already exists in database
```

### 1.3 Sign Out

```typescript
await supabase.auth.signOut()
// Call backend logout (optional - backend is stateless)
await fetch('http://localhost:8080/auth/logout', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${accessToken}` }
})
```

---

## 2. API Endpoints

### Base URL
```
http://localhost:8080
```

### Authentication Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|--------------|-------------|
| GET | `/auth/me` | Yes | Get current user info |
| POST | `/auth/bootstrap` | Yes | Create profile for new user |
| POST | `/auth/logout` | No | Acknowledge logout (stateless) |

### Protected Endpoints (require valid JWT)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/drugs` | List all drugs |
| POST | `/drugs` | Create drug |
| GET | `/drugs/{id}` | Get drug by ID |
| PUT | `/drugs/{id}` | Update drug |
| DELETE | `/drugs/{id}` | Delete drug |
| GET | `/forecasts` | List forecasts |
| POST | `/forecasts` | Generate forecast |
| GET | `/notifications` | List notifications |
| GET | `/purchase-orders` | List purchase orders |

---

## 3. User Profile (GET /auth/me)

### Request
```
GET /auth/me
Authorization: Bearer {access_token}
```

### Success Response (200)
```json
{
  "id": "user-uuid",
  "email": "user@example.com",
  "role": "owner",
  "organization_id": "org-uuid",
  "locations": [
    {
      "id": "location-uuid",
      "name": "Main Branch",
      "address": "123 Main St, Ottawa, ON"
    }
  ]
}
```

### Error Responses

| Status | Error | Action |
|--------|-------|--------|
| 401 | AUTHENTICATION_REQUIRED | User must sign in |
| 403 | USER_PROFILE_NOT_BOOTSTRAPPED | User needs to complete onboarding |

---

## 4. User Bootstrap (POST /auth/bootstrap)

**IMPORTANT:** Only call this ONCE per user after initial signup.

### Request
```
POST /auth/bootstrap
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "organization_name": "My Pharmacy",
  "location_name": "Main Branch",
  "location_address": "123 Main St, Ottawa, ON"
}
```

### Success Response (200)
```json
{
  "organization_id": "org-uuid",
  "location_id": "location-uuid",
  "user_id": "user-uuid"
}
```

### Error Responses

| Status | Error | Cause |
|--------|-------|-------|
| 401 | AUTHENTICATION_REQUIRED | Invalid/missing JWT token |
| 400 | Various | Invalid request body |

---

## 5. Required JWT Claims

The JWT token from Supabase must contain:
- `sub`: User UUID (required)
- `email`: User email (optional but recommended)
- `iss`: Must be `https://ebrxagoygjtnpzlnxtmr.supabase.co/auth/v1`
- `exp`: Must not be expired

---

## 6. Frontend Session Handling

### Store Token
```typescript
// After Supabase sign in/sign up:
const session = supabase.auth.session()
const accessToken = session.access_token

// Store in memory or httpOnly cookie (NOT localStorage for sensitive apps)
```

### Use Token for API Calls
```typescript
const response = await fetch('http://localhost:8080/auth/me', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
})
```

### Handle Token Expiry
```typescript
if (response.status === 401) {
  // Token expired or invalid
  await supabase.auth.signOut()
  // Redirect to login
}
```

---

## 7. Error Handling Examples

```typescript
async function callProtectedEndpoint(accessToken: string) {
  const response = await fetch('http://localhost:8080/auth/me', {
    headers: {
      'Authorization': `Bearer ${accessToken}`
    }
  })

  if (response.status === 401) {
    const error = await response.json()
    if (error.error === 'AUTHENTICATION_REQUIRED') {
      // Redirect to login
      throw new Error('Please sign in again')
    }
  }

  if (!response.ok) {
    const error = await response.json()
    throw new Error(error.message || 'Request failed')
  }

  return response.json()
}
```

---

## 8. Complete Example: Onboarding Flow

```typescript
async function handleSignup(email, password, organizationName, locationName, locationAddress) {
  const supabase = createClient(
    'https://ebrxagoygjtnpzlnxtmr.supabase.co',
    'ANON_KEY'
  )

  // Step 1: Sign up with Supabase
  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      data: {
        organization_name: organizationName,
        location_name: locationName,
        location_address: locationAddress
      }
    }
  })

  if (error) {
    throw new Error(error.message)
  }

  // Step 2: If no session, user must confirm email
  if (!data.session) {
    return { needsConfirmation: true }
  }

  // Step 3: Bootstrap the user in our database
  const bootstrapResponse = await fetch('http://localhost:8080/auth/bootstrap', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.session.access_token}`
    },
    body: JSON.stringify({
      organization_name: organizationName,
      location_name: locationName,
      location_address: locationAddress
    })
  })

  if (!bootstrapResponse.ok) {
    throw new Error('Failed to create profile')
  }

  const profile = await bootstrapResponse.json()
  return { success: true, profile }
}
```

---

## 9. Environment Configuration

Frontend needs these environment variables:

```env
NEXT_PUBLIC_SUPABASE_URL=https://ebrxagoygjtnpzlnxtmr.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
NEXT_PUBLIC_API_URL=http://localhost:8080
```

---

## 10. Debug Logging

Add these logs to help diagnose issues:

```typescript
console.log('[Auth] signUp attempt:', email)
console.log('[Auth] signUp result:', { hasSession: !!data.session, error: error?.message })

if (data.session) {
  console.log('[Auth] Bootstrap with token:', data.session.access_token.substring(0, 20) + '...')

  const bootstrapResponse = await fetch('/auth/bootstrap', ...)
  console.log('[Auth] Bootstrap response:', bootstrapResponse.status, await bootstrapResponse.json())
}
```