# VideoQuiz – Video Deletion and Gemini Reliability Patch

Prepared against GitHub `main` commit `0eb5ac4023bd36f277d02f705c1ff1b09f9b615c` (11 November 2026). **Not deployed.**

## New features

- Delete an owned video through `DELETE /api/videos/{id}`, guarded by authentication and CSRF. A confirmation dialog warns that **all associated quiz attempts, answers, scores and explanations will be permanently deleted**. Other videos/attempts survive. Active `PROCESSING` videos cannot be deleted.
- Display per-account video usage in **My Quizzes**. `GET /api/storage` returns `{usedVideos,maxVideos,usedBytes,maxBytes}` without leaking other accounts.
- Cloud deletion uses a transaction to remove metadata and queue all related private media chunks in a new `videoquiz_private.deletion_queue` table. Supabase Storage objects are deleted using Storage HTTP API (bulk DELETE `object/{bucket}`, `prefixes` JSON). Failures stay queued for the scheduled recovery task; no manual SQL deletion of `storage.objects`.
- Gemini generation (not file uploads) retries transient 408, 429, 500, 502, 503 and 504 twice with randomized 2s/4s backoff; shows progress while retrying, distinguishes `MAX_TOKENS` and `SAFETY` terminations, and preserves the input on failure. No fallback fake quiz.
- Existing bilingual UI is preserved. Source files are UTF-8.

## Safety and limitations

**Do not merge to `main` until GitHub Actions pass all jobs on a new branch and live end-to-end smoke tests are complete.** The original live website has not been modified.

Deletion is irreversible. Deleting a video also deletes every attempt and its review. Deleted cloud objects may take additional time to disappear from Supabase if its API is temporarily unavailable. `SupabaseObjects.remove` should be verified against the live provider with a disposable test file before enabling deletion for all users.

A persistent `503` response from Google Gemini is a service-side problem that application retries cannot guarantee to solve. Model access and current quota need checking in Google AI Studio, without sharing API keys in chat. Do not retry continuously; uploads remain saved.

## Tests performed here

- Node.js: 25 JS unit tests passed.
- Java core classes (no Spring dependencies): compiled with `javac --release 17`.
- Local Store owner-scoped deletion smoke test: passed.
- **Not run here:** Maven/Spring Boot tests, H2/PostgreSQL integration tests, real Supabase HTTP cleanup, real Gemini requests, and Playwright browser acceptance (these depend on external services/tools). `tests/browser-ui.cjs` has an additional delete regression fixture for CI.

## Installation

1. Create a separate branch from GitHub `main`, not the existing merged feature branch. Use the *changed-files ZIP* to replace matching source files at their exact paths in that branch, or use the complete project ZIP in a separate checkout.
2. Open a Pull Request to `main` and ensure Java 17, Java 21 + PostgreSQL, browser and Docker workflow checks pass. Review `src/main/java/edu/videoquiz/core/CloudStore.java` and `SupabaseObjects.java` carefully.
3. Once verified, merge. Render should automatically rebuild the app on the Free plan. Do not change existing credentials or Supabase bucket, and do not delete historical data manually.
4. On the live site, test: login -> My Quizzes count -> delete an expendable FAILED video -> count decrements -> refresh -> verify deletion -> upload small MP4 -> analyze -> review attempts. Check that deleting a video also removes its attempts. Test retrying one 503 with a fake Gemini fixture in CI before depending on it for a class demo.
