# Requirements compliance — sections 4, 5 and 6

Source: the complete two-page `E:\VideoQuiz\VideoQuiz_Requirements_v3.pdf`, copied alongside this report.
Date: 8 November 2026. This is a source implementation assessment, **not a claim of full compliance**.

**Implemented** means the code path exists. **Partial verification** means specific offline behavior passed tests.
**Blocked** means the required live build, service or browser test could not run. No blocked item is counted as a pass.

| PDF ID | Requirement | Implementation / evidence | Verification and remaining gap |
|---|---|---|---|
| 4 | Key interactive-learning functions | Spring web application, player and quiz UI | Overall acceptance blocked; not complete |
| 4.1 | Upload a local video | `Api` upload session, 1 MiB chunks, SHA-256 integrity, retained media; file picker | Container tests passed; HTTP upload and real playback not verified |
| 4.2 | Supported video link | Exact HTTPS YouTube host/ID parser, canonical Gemini input, YouTube iframe | URL parser passed valid/invalid cases; live YouTube analysis/playback blocked |
| 4.3 | Play and answer at specified times | Visible HTML5/YouTube player; 100ms clock; all due questions queued independently of answer state | Pure scheduling tests passed; actual playback and browser timing not verified |
| 4.4 | Feedback after submission | Server-owned answer key, correctness and explanation; client renders feedback | Correct/wrong/repeated-answer core tests passed; HTTP/UI not verified |
| 4.5 | Final score/performance summary | Score derived from stored answers; percent, total and completion UI | Core scoring passed; actual result screen not verified |
| 4.6 | Review previous questions/answers | Persistent attempts, history, submitted-answer explanations/evidence and source revisit | Persistence and ownership checks passed; review UI not verified |
| 5 | Video processing and interactive questions | Validation → Gemini file processing → evidence → questions → playback | Overall live workflow blocked; not complete |
| 5.1 | Validate file/URL before processing | Size/extension, MP4 container/codec/duration, SHA-256 integrity, strict URL validation | Offline container and URL checks passed. Parser is not a full media decoder; remote availability is checked by services |
| 5.2 | Analyze actual video content | Real video bytes uploaded using Gemini Files API, wait for ACTIVE; public YouTube file_data input | Implemented; live request blocked. No evidence of successful content analysis in this environment |
| 5.3 | Extract key information | First Gemini pass extracts evidence, summaries and start/end timestamps from audio and visuals | Schema/range validation passed on fixtures; actual semantic extraction not verified |
| 5.4 | Generate relevant questions | Second Gemini pass uses extracted evidence, validates four options and source index; no fallback quiz | Validation passed on fixtures; content relevance/answer accuracy need human review of real video |
| 5.5 | Assign/display timestamps | Question timestamp derived from evidence end; questions sorted, rendered when media clock reaches timestamp | Timestamp derivation/scheduling passed; semantic timestamp accuracy and actual display delay not verified |
| 5.6 | Retry failed analysis | Failed state retains input; retry job; concurrent duplicate runs rejected; restart recovery marks interrupted jobs retryable | Repository restart recovery passed; live outage/retry/queue HTTP behavior not verified |
| 6 | Measurable quality targets | Controls described below | Not fully met/verified until deployment acceptance completes |
| 6.1 | Upload to playback in ≤3 steps | Three main tasks: account access, choose file, Analyze & play; preview begins on third action | Designed, not user-tested. Strict physical clicks, first-time certificate setup or browser autoplay restrictions may exceed target |
| 6.2 | Display within 2 seconds | 100ms foreground polling plus all-due queue, no waiting for previous answer; `videoQuizTiming()` instrumentation | Simulation passed at 0.5x/1x/2x; real browser timing not measured. Hidden-tab throttling is outside normal foreground playback |
| 6.3 | Explain input/processing failure within 5 seconds | Immediate client validation; 4s ordinary API timeout; 1s status polling; sanitized server errors | Implemented; wall-clock end-to-end guarantee not measured. AI analysis remains asynchronous; waiting is not labeled failure |
| 6.4 | Authentication and HTTPS for data | BCrypt, session auth, CSRF, owner isolation, HttpOnly/Secure/Strict cookies, TLS-only configuration and media endpoint | Ownership core tests passed. Spring security/login/CSRF/range tests supplied but blocked. No trusted TLS deployment provisioned or verified |
| 6.5 | Latest two Chrome/Safari/Edge; widths 360–1920 | Responsive CSS, standard JS/HTML media, mobile stacking and visible sticky player | **Not verified** on any target browser/version/width. No Safari environment; local preview was blocked by browser policy |
| 6.6 | Clear status after >10s external outage; retain partial upload | Stage timer exposes slow/unreachable notice after 10s; 1s polling; completed chunks flushed independently of AI; resume checksum | Implemented; real timed outage and interrupted-upload HTTP tests not run. Completed chunks retained; incomplete in-flight chunk may need resending |

## Supported scope and explicit limitations

- Local uploads are H.264 MP4 only, ≤256 MiB and ≤30 minutes; AAC audio recommended. No AVI/MKV/MOV transcoder is included.
- Links are public YouTube only. Arbitrary URL fetching is not implemented, avoiding server-side requests to private/internal addresses.
- Gemini support, quota, permissions, model availability, video accessibility and YouTube embed policy are external dependencies.
- The extraction pipeline is direct multimodal analysis, not a separate speech-to-text service. It covers audio and visual evidence.
- AI schemas and ranges are checked, but code cannot prove semantic correctness. Real timestamp and answer verification remains necessary.
- Authenticated per-user storage is single-server. No password reset, account deletion/admin UI, distributed job queue, or at-rest encryption is provided.
- The default HTTPS certificate location must be configured before startup. No HTTP fallback is provided.

## Architecture references

The integration follows Google's documented file-upload/ACTIVE-state and public YouTube video-input interfaces:
https://ai.google.dev/api/files and https://ai.google.dev/gemini-api/docs/generate-content/video-understanding.
Spring's Java/Maven baseline is documented at https://docs.spring.io/spring-boot/3.5/system-requirements.html.
References support API choices, not test-pass claims.
