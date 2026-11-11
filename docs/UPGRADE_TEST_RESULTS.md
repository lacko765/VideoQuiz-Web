# Upgrade test results — 11 November 2026

Base: GitHub main `cf86bc190174498d51b9c78e9c8b658066df3071`. All execution used the separate development copy, not the live service's database or original NetBeans data.

| Check | Actual outcome |
|---|---|
| Maven verify / executable Spring Boot JAR | PASS: 40 tests passed, 6 PostgreSQL-specific tests skipped; no failures |
| Java security/API | PASS: real Spring security chain registration, hashed password, rejected bad password, login/logout, ownership, CSRF, media ranges, immutable scoring, separate attempts, incomplete review secrecy, bounded public timeout settings |
| Language generation fixture | PASS: Arabic prompts, real-media reference shape, evidence-derived timestamp; no Gemini network request |
| Cloud repository fixtures | PASS: H2 SQL + fake private object transport; records/media survive empty-cache reopen, saved Arabic language retained, stale writes rejected, retry/reconnection behavior |
| JavaScript unit/regression suites | PASS: 25 tests including Next, timing/seeking, lost response recovery, duplicate requests, delayed loader, timeout cleanup, language translations |
| Offline Java core suite | PASS: 48 checks; synthetic MP4/parser/quiz fixtures, not live AI |
| Actual browser UI with network fixtures | PASS: 12 acceptance groups, zero page errors; details below |
| Visual review | PASS for captured desktop/light, Arabic/dark, Arabic results and 360px Arabic history screenshots; no horizontal overflow at 360, 768, 1920 in both languages |
| Real Render/Supabase/Gemini integration for this upgrade | NOT EXECUTED; no production data or secrets used |
| Read-only production availability probe | INCONCLUSIVE: HTTPS request timed out after 75 seconds from this environment; owner reports site is live |
| Latest two Chrome/Safari/Edge versions | NOT VERIFIED; browser execution used installed Brave/Chromium headless on Windows |
| Docker / GitHub Actions | Configuration supplied, not executed locally; Docker is unavailable, GitHub connector rejected branch creation with 403, and Git CLI had no usable authentication. No remote branch or CI run was created |
| NetBeans green Run button | NOT RETESTED; Maven build passed and local launcher/nbactions configuration is unchanged |

## Browser acceptance groups

`tests/browser-ui.cjs` serves the real application HTML/CSS/JavaScript locally. It replaces only API/YouTube service responses with fixtures and does not consume Gemini quota. Its MP4 preview uses a real 18-second authored H.264 math clip. It verifies:

1. Registration/login form flow against HTTP fixtures (real backend authentication tested separately in Maven).
2. Arabic RTL and dark-mode preferences survive refresh.
3. YouTube API after 15 seconds, player readiness after a further 25 seconds, metadata after a further 15 seconds; no cancellation at 10 seconds, no analysis before valid metadata.
4. Retry after a fixture AI failure keeps the video and generates a ready quiz state.
5. Language switching retains the selected answer and player position; Next does not seek.
6. Incomplete results display all questions but reveal only submitted answer keys.
7. Completed 100% score, all options/explanations, independent second attempt without another analysis request.
8. Dashboard/history and English/Arabic layout at 360, 768 and 1920 pixels without horizontal overflow.
9. Player readiness timeout retains the link; retry reconstructs the failed player.
10. Zero-duration metadata never reaches Gemini; retry succeeds once valid duration arrives.
11. Real MP4 preview, SHA-256 fingerprint, chunk upload and quiz transition using HTTP/AI fixtures.
12. Logout returns to authentication and clears cached account views.

These checks do **not** prove YouTube availability/embedding rights, Gemini semantic accuracy, live rate-limit recovery or hosted persistence. Those require the post-merge smoke test in `UPGRADE_2026-11-11.md`. Browser timing uses a controlled virtual clock for slow-service fixtures, not a live service delay measurement.

## Evidence and reproducibility

- `mvn -B -ntp verify` (JDK 26.0.2 locally, compiler release 17; CI matrix supplied for JDK 17/21).
- `node --test tests/*.test.cjs` (Node 24 locally).
- `scripts/test-offline.ps1 -JavaHome <your JDK>`.
- Install Playwright in a test environment, then `node tests/browser-ui.cjs`; optionally set PLAYWRIGHT_MODULE and BROWSER_EXE for existing local installations.
- Screenshots/report are written to `.tools/ui-artifacts` and excluded from runtime packaging. All test data is explicitly synthetic/authored and disposable.
- Initial sandbox runs hit Windows temporary-directory cleanup restrictions. Authorized reruns completed successfully; blocked runs are not counted as passes.

The existing PDF's 10-second external-service notice remains implemented. Its 5-second error-message target refers to displaying a detected failure, not canceling every slow network request at five seconds. Complete cross-browser/version and live timing acceptance remains outstanding.
