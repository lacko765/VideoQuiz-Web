# Test results

Date: 8 November 2026. Execution host: Windows, installed Temurin JDK 26.0.2 (compiled with `--release 17`),
Node.js 24.22.0, original Gson 2.13.2 JAR. Full runtime testing should use the recommended JDK 17 or 21.

| Check | Result | Evidence / limitation |
|---|---|---|
| Read local PDF and every original Java source | PASS | Both PDF pages, all 3 main source files, all 3 comparison source files inspected; two comparison files byte-identical |
| Create original backup before code changes | PASS | All 28 original files copied; SHA-256 comparison report included |
| Java core compilation | PASS | `javac --release 17`, all `edu.videoquiz.core` sources plus offline tests |
| Java core test suite | PASS | 48 assertions, 0 failed (`tests/CoreChecks.java`) |
| JavaScript syntax | PASS | `node --check src/main/resources/static/app.js` |
| JavaScript scheduling/navigation suite | PASS | 10 tests, 0 failed (`tests/clock.test.cjs`) |
| Full Maven project build | BLOCKED / NOT PASSED | Spring Boot parent could not be downloaded: `Permission denied: getsockopt` from Maven Central; dependency resolution failed before compilation |
| Spring HTTP/security integration tests | NOT RUN | 12 tests supplied in `ApiTests.java`; blocked by unresolved dependencies |
| Live Gemini connectivity | BLOCKED / NOT PASSED | `LiveProbe` found the environment key but HTTPS connection failed with `ConnectException`; no key or response body printed |
| Real local-video AI analysis | NOT RUN | External connection blocked; no synthetic fixture counted as AI output |
| Real YouTube analysis/playback | NOT RUN | External connection blocked and app not built |
| Question relevance/answer correctness | NOT VERIFIED | Requires actual generated output and source-video review |
| Semantic timestamp accuracy | NOT VERIFIED | Numeric range/mapping tests pass; actual content timing not checked |
| Upload/resume over HTTP | NOT RUN | Endpoint implementation and tests supplied; app cannot start without dependencies |
| Video playback and media byte ranges | NOT RUN | Range/security integration test supplied; no live media decode tested |
| Login/logout/CSRF/HTTPS deployment | NOT RUN | Core ownership tested; full security chain and trusted certificate not exercised |
| UI/viewport/browser compatibility | BLOCKED / NOT VERIFIED | Browser tool rejected local-file preview. No screenshots or six-browser acceptance results exist |
| Normal-playback ≤2s display target | PARTIAL: simulation PASS | Simulated 100ms polling at 0.5x, 1x, 2x; no actual browser measurement |
| ≤5s error reporting | NOT VERIFIED | 4s API timeouts and 1s polling implemented; actual wall-clock measurement pending |
| >10s outage status and retention | PARTIAL | Restart recovery/retained metadata tested; real timed outage and upload interruption pending |

## Java test coverage (48 checks)

- Four accepted YouTube URL variants and ten invalid/unsupported URL cases.
- Evidence extraction shape, empty content, negative/reversed/out-of-bounds timing.
- Evidence-derived timestamps; invalid answer/source indices, fractional source index, duplicate/missing options,
  empty questions and repeated question/evidence rejection.
- Correct/incorrect scores; immutable first submission; invalid question/option rejection.
- Video/answer persistence across repository reload, cross-user access isolation, per-user history and path traversal rejection.
- Account persistence, duplicate/invalid registration, interrupted analysis recovery.
- Structural MP4 duration extraction; invalid/truncated container rejection.
- Absent API key detection, invalid model path rejection, and refusal to fabricate questions when no key is configured.

## JavaScript test coverage (10 tests)

Before/exact timestamp behavior; display all due questions; no duplicates; backward/forward seeks; next unanswered
selection; completion; invalid media time; simulated delay bound at multiple playback rates.

The MP4 and quiz fixtures are synthetic test data. They prove parser/logic behavior only, not media playback,
AI semantic quality, production authentication or a successful end-to-end application run.

No test that was blocked or not executed is marked passed. See `ACCEPTANCE_TESTS.md` for the remaining steps.

Additional environment note: one repeat through the portable test script emitted a JDK 26 ZIP-filesystem
`AccessDeniedException` while closing the copied Gson JAR, despite a zero compiler exit code and passing test execution.
A subsequent direct compilation using the identical original `E:\VideoQuiz\lib\gson-2.13.2.jar` completed cleanly.
The script now treats that diagnostic as a failure rather than relying only on exit code, and accepts `-GsonPath`
for environments with file-access restrictions. This does not change the full Maven-build blocker above.
