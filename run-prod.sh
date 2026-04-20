#!/bin/bash
cd "$(dirname "$0")"

# Kill any existing Spring Boot process on port 8080
echo "Stopping any existing Spring Boot processes..."
pkill -f "spring-boot:run" 2>/dev/null || true
lsof -ti:8080 | xargs kill -9 2>/dev/null || true

# Wait for port to be released
sleep 2

# Set environment variables
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL="jdbc:postgresql://aws-1-ca-central-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0"
export DATABASE_USERNAME="postgres.ebrxagoygjtnpzlnxtmr"
export DATABASE_PASSWORD="Bryant.Ronaldo13!"
export SUPABASE_JWKS_URI="https://ebrxagoygjtnpzlnxtmr.supabase.co/auth/v1/.well-known/jwks.json"
export SUPABASE_JWT_ISSUER="https://ebrxagoygjtnpzlnxtmr.supabase.co/auth/v1"
export SUPABASE_JWT_AUDIENCE="authenticated"

echo "Starting Spring Boot with prod profile..."
mvn spring-boot:run