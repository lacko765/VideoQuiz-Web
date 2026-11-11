# Existing live deployment — November 2026 update

The owner has deployed VideoQuiz at https://videoquiz-web.onrender.com using the existing Render Free and Supabase Free projects. **Do not recreate them.** See [the upgrade guide](docs/UPGRADE_2026-11-11.md) for branch review, tests and safe redeployment. Older setup/status statements below describe the initial delivery and are historical.

# VideoQuiz Web — Easy Setup

Java 17 / Spring Boot / Maven application for real Gemini video analysis, timed questions, authenticated uploads, scoring and history. This updates the existing web project; the original E:\VideoQuiz is not modified.

## Start here

Read [HOW_TO_RUN_IN_NETBEANS.md](HOW_TO_RUN_IN_NETBEANS.md) for Arabic and English instructions.

1. Extract the delivery ZIP to a writable folder.
2. Install JDK 17 or 21 and Apache NetBeans if needed.
3. Double-click **setup-videoquiz.bat**. Enter your own Gemini key and a 12+ character HTTPS password at the hidden prompts.
4. Open this folder in NetBeans and click **Run Project**, or double-click **start-videoquiz.bat**.
5. After `Started Application`, open **https://localhost:8443** (or your chosen port).

Setup locates Java and Maven, downloads Maven/dependencies if required, creates a local certificate, saves Windows-user-encrypted credentials, checks the port and builds/tests the project. It does not install Java, certificate trust or system-wide environment variables. A self-signed certificate may require your manual browser decision or university IT support. No paid AI subscription is required by the app; free-tier access and quota are controlled by Google.

## Playback and analysis

- Local H.264 MP4: up to 256 MiB / 30 minutes. AAC audio recommended.
- Links: public recorded YouTube videos up to 30 minutes. Inaccessible and non-embeddable content produces an error.
- Analysis receives actual uploaded bytes or the actual YouTube reference. There is no canned/random quiz fallback.
- YouTube analysis waits for player metadata. Gemini's guessed duration is not used. MP4 duration comes from the container.
- Evidence times accept seconds, MM:SS and HH:MM:SS. Invalid intervals are rejected, never clamped. Invalid evidence/questions may be regenerated once per stage from the source.
- Timer updates during analysis. When questions are ready, a new attempt restarts the video so the quiz can be taken in order.
- Questions appear automatically at their timestamps. **Next question never seeks or pauses**. It dismisses answered cards and focuses the next question only if already due.
- Manual forward seeking queues due questions once. Rewinding does not duplicate answered questions. Answers remain in results/history.
- Retry uses the saved input. Repeated POSTs during processing reconnect to the existing job. Lost POST responses trigger status recovery. Jobs have a 12-minute deadline; interrupted jobs become retryable after restart.
- Errors distinguish credentials, quota, unavailable model/video, connection problems and invalid content. Retry cannot bypass provider quota.

## Configuration

Setup creates `config/local-settings.json`, `config/secrets.xml` and `config/setup-complete.json`. Secrets use Windows DPAPI for the current Windows user on this computer. Never share these files. The launcher decrypts credentials only into its process environment; no plaintext secret configuration or Java command-line password is created.

Default model: **gemini-3.5-flash-lite**. It passed live generation with the test key. During this delivery, gemini-2.5-flash appeared in Google's model list but returned HTTP 404 on generation. Each teammate can change the model/key through setup. Check [Google pricing](https://ai.google.dev/gemini-api/docs/pricing) and [model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite) for current free-tier access. The app does not enable billing.

Advanced environment-driven startup remains supported with `mvn spring-boot:run` or `java -jar target/videoquiz-1.0.0.jar`, from the project root:

| Variable | Purpose/default |
|---|---|
| GEMINI_API_KEY | Gemini key; server-side only |
| GEMINI_MODEL | Defaults to gemini-3.5-flash-lite |
| VIDEOQUIZ_TLS_PASSWORD | PKCS12 password |
| VIDEOQUIZ_TLS_KEYSTORE | file:./config/localhost.p12 |
| VIDEOQUIZ_DATA_DIR | ./data |
| VIDEOQUIZ_BIND | 127.0.0.1 |
| VIDEOQUIZ_PORT | 8443 |

The Easy Setup launcher uses its saved configuration. HTTPS has no HTTP development bypass.

## Architecture and preserved features

`Api` serves authenticated uploads, links, analysis, attempts, answers and media byte ranges. `Jobs` owns asynchronous analysis. `Gemini` extracts real video evidence and generates questions. `Validation` enforces timestamps, answer options and scoring. `Store` persists per-user JSON/media with atomic JSON replacement where supported. `Security` configures BCrypt/session authentication, CSRF and HTTPS. The frontend uses HTML/CSS/JavaScript with HTML5 and YouTube players.

Completed upload chunks survive failures. SHA-256 fingerprints prevent mixing files during resume. The first submitted answer is immutable and scored on the server. Answer keys/evidence are withheld until submission. Previous attempts and explicit source revisit remain available.

Use one application instance per data directory. Limits: 30 videos / 1 GiB per account, 500 attempts, 1 MiB chunks and four active/queued analysis slots. Protect and privately back up data. At-rest media encryption, password reset and account deletion are not implemented.

## Verification and distribution

See [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md), [docs/COMPLIANCE.md](docs/COMPLIANCE.md), and [docs/MODIFIED_FILES.md](docs/MODIFIED_FILES.md) for actual evidence and limitations.

- `mvn verify`: Java compilation, HTTP/security, retry lifecycle and timestamp tests.
- `scripts/test-offline.ps1`: Java core checks and JavaScript tests (Node.js required only for these optional tests).
- `tests/browser-live.cjs`: opt-in real browser/Gemini tests requiring Playwright and an isolated test server; consumes real quota. Its temporary browser context accepts the self-signed test certificate; normal browser trust is not claimed.
- `scripts/package-easysetup.ps1`: allowlisted clean ZIP creation, excluding secrets, data, backups, cached tools and build output.

Send **VideoQuiz-Web-EasySetup.zip**, not a ZIP of your working folder. No prebuilt JAR is distributed; setup builds it.
# Public website deployment

For the $0 Render Free + Supabase Free setup, see [DEPLOYMENT.md](DEPLOYMENT.md). No credit card or paid plan is authorized. Cloud storage is separate from the existing NetBeans data. A public URL is available only after account setup, deployment and live verification.

