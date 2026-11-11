# Cloud deployment verification — 9 November 2026

## Implemented

Render Free Blueprint, Docker build, Java 17/21 CI, local/prod/cloud profiles, platform HTTPS forwarding and health endpoint. Local NetBeans storage and certificate settings remain the default. Cloud metadata uses PostgreSQL; media uses private Supabase objects with a disposable local cache. No real data or secrets are included. Backup verified before changes; original E:\VideoQuiz untouched.

## Verified locally

- Maven executable JAR packaging succeeded from an isolated copy (the original app was running and locking the normal output JAR).
- 34 Java tests passed; six PostgreSQL-specific tests were skipped locally because no PostgreSQL server is installed. CI is configured to run those six against PostgreSQL 16.
- 17 JavaScript tests passed, including Next without seeking, timestamp boundaries, manual seeking, retry deduplication and response-loss recovery.
- Production-profile HTTP server tests passed: health endpoint, plain-HTTP redirect, forwarded HTTPS without redirect loops, secure/HttpOnly session cookie, unauthenticated private API denial, required storage/key guards.
- Cloud storage fixture tests passed: account/history/media survival with a new empty cache, interrupted upload retention, ownership checks, stale-write rejection, duplicate chunk rollback, bounded job recovery and database reconnection.
- Supabase HTTP contract fixtures passed: authenticated download path, private-bucket acceptance, public-bucket rejection and redaction of provider error bodies.

The SQL fixture tests use H2; HTTP fixtures do not contact Supabase. They are not live provider validation. Earlier local live Gemini/browser tests covered two YouTube videos and an educational MP4, retry recovery, questions, scoring/history and Next behavior. Those were on the local filesystem implementation, not the new cloud storage backend.

## Pending external verification

No Render or Supabase account has been configured, no public URL exists, and no paid service/billing was enabled. Live Supabase TLS/database/bucket connectivity, Render memory limits/build/runtime, public HTTPS, persistence across actual provider restarts, live Gemini from the hosted service, and full public browser acceptance remain unverified. Docker is not installed on this Windows computer; CI is configured to build the image. GitHub rejected the attempted tree upload with HTTP 403, Resource not accessible by integration. The remote repository was not updated and CI did not run. Repository write access or a manual upload is required.

Computer Use screen capture was unavailable; backend and automated tests were used. The actual NetBeans green Run button was not retested in this cloud phase. Its configuration/scripts were preserved.

## Operational limits

Strictly $0/no card. Signup requesting a card blocks that provider. Free quotas, sleep/pausing, external Gemini availability and temporary outage recovery apply. One service instance, no high-availability guarantee. Cloud does not import existing personal data. Failed upload transactions can leave unreferenced private objects; monitor provider usage. Follow DEPLOYMENT.md before sharing the website.
