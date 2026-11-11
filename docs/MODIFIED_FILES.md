# File changes

Neither `E:\VideoQuiz` nor `E:\VideoQuiz_Updated` was modified. The original's 28 files were backed up and
verified by SHA-256. All implementation files below are **new files in the separate VideoQuiz-Web project**.

| File | Purpose |
|---|---|
| `pom.xml` | Java 17 Maven/Spring Boot project, security/web/test dependencies |
| `.gitignore` | Excludes runtime data, credentials, certificates and build output |
| `README.md` | NetBeans, Maven, API key, HTTPS, format and operational instructions |
| `src/main/java/edu/videoquiz/Application.java` | Spring entry point, repository and AI configuration |
| `src/main/java/edu/videoquiz/Security.java` | Login/session, BCrypt, CSRF, HTTPS and browser security headers |
| `src/main/java/edu/videoquiz/Api.java` | Authenticated video/upload/analysis/attempt/media endpoints |
| `src/main/java/edu/videoquiz/Jobs.java` | Bounded asynchronous AI jobs, progress, failure/retry handling |
| `src/main/java/edu/videoquiz/Errors.java` | Safe explanatory API errors |
| `src/main/java/edu/videoquiz/RequestLimits.java` | Request-size and login/registration rate limits |
| `src/main/java/edu/videoquiz/core/Models.java` | Videos, evidence, questions, attempts, accounts, feedback |
| `src/main/java/edu/videoquiz/core/Store.java` | Private durable storage, ownership and restart recovery |
| `src/main/java/edu/videoquiz/core/Validation.java` | URL/AI validation, evidence mapping and immutable answer scoring |
| `src/main/java/edu/videoquiz/core/Mp4.java` | Bounded MP4 container/codec/duration inspection |
| `src/main/java/edu/videoquiz/core/Gemini.java` | Actual video upload, multimodal evidence and grounded question generation |
| `src/main/resources/application.properties` | TLS-only server and environment-variable configuration |
| `src/main/resources/static/index.html` | Authentication, home, player/quiz, results, history screens |
| `src/main/resources/static/styles.css` | Responsive layout and visual styling |
| `src/main/resources/static/app.js` | Upload/resume, player, polling, quiz navigation, feedback and review |
| `src/main/resources/static/quiz-clock.js` | Testable due-question and next-question logic |
| `src/test/java/edu/videoquiz/ApiTests.java` | 12 supplied Spring HTTP/security integration tests (not run) |
| `tests/CoreChecks.java` | 48 offline Java assertions with isolated synthetic fixtures |
| `tests/clock.test.cjs` | 10 Node scheduling/navigation tests |
| `tests/LiveProbe.java` | Credential-safe Gemini connectivity probe |
| `scripts/test-offline.ps1` | Repeatable offline compilation and tests |
| `scripts/create-local-certificate.ps1` | Local PKCS12 generation; does not install trust |
| `lib/gson-2.13.2.jar` | Unchanged copy of original bundled dependency for offline tests |
| `docs/VideoQuiz_Requirements_v3.pdf` | Unchanged copy of the authoritative local requirements PDF |
| `docs/BEFORE_IMPLEMENTATION.md` | Architecture findings and pre-implementation checklist |
| `docs/COMPLIANCE.md` | Every PDF section 4/5/6 requirement with implementation/verification status |
| `docs/TEST_RESULTS.md` | Actual passes, blockers and unverified behavior |
| `docs/ACCEPTANCE_TESTS.md` | Remaining live tests and browser matrix |
| `docs/BACKUP_VERIFICATION.csv` | Original/backup SHA-256 comparison |
| `docs/MODIFIED_FILES.md` | This manifest |

The packaged `Original-VideoQuiz-Backup` directory contains the unmodified original project, including its existing
NetBeans metadata, libraries and original build output. The upgraded project excludes generated build output and private runtime data.
