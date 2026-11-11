# VideoQuiz: inspection and implementation checklist

Inspected 8 November 2026. Source: E:\VideoQuiz; comparison: E:\VideoQuiz_Updated.
Read both pages of the local VideoQuiz_Requirements_v3.pdf before implementation.
Original backed up under ../backup/VideoQuiz (28 files). Neither E: folder will be overwritten.

## Architecture and findings

The main project is a Java 17 Ant/NetBeans Swing application, with Gson 2.13.2.
All three Java files were read. VideoQuiz launches VideoQuizDashboard; VideoQuizGUI is an older NetBeans form.
The selected file never enters the AI request. generateTestQuestion always requests a photosynthesis question.
The video preview is a JLabel. No media clock, Next button, aggregate score, authentication, persistent attempts or HTTPS server exists.
The updated copy has identical entry point and form files. Its dashboard adds five questions, answer protection,
scoring, restart and actual YouTube file_data input. Local videos still produce topic-only quizzes and open externally.
Its My Quizzes button can reset answered state or index beyond the final question, and history is not persistent.

Decision: migrate to Spring Boot + Maven + plain HTML/CSS/JavaScript. Preserve the old project as a separate backup.
Reuse the Java HttpClient/Gson/GEMINI_API_KEY pattern and four-option quiz model; adapt feedback/navigation semantics.
Use Gemini's video file pipeline to inspect audio and visual content directly, extracting timestamped evidence before generating questions.
Do not substitute topic prompts or fabricated questions if analysis fails. Limit URL support to canonical public YouTube links.
Store media, analysis and answers privately with authenticated ownership checks. Browser receives answer keys only after submission.

## Checklist established before code changes

| PDF item | Original state | Implementation target / verification |
|---|---|---|
| 4 (overall) | Prototype | End-to-end interactive web workflow |
| 4.1 | File chooser only | Persistent, resumable local uploads; playback test |
| 4.2 | Unused prefix validation | Strict YouTube ID validation, video input and embed; live test |
| 4.3 | Missing player | Visible HTML5/YouTube player and scheduled question cards |
| 4.4 | Single-question color feedback | Server-validated answer and explanation; duplicate submission test |
| 4.5 | Missing | Derived score, completion and performance report |
| 4.6 | Missing | Persistent per-user history and review, new attempt preserves old answers |
| 5 (overall) | Topic-only AI demo | Validated content-to-quiz pipeline |
| 5.1 | File extension / URL prefix only | Limits, signature/container validation and strict URL parser |
| 5.2 | Not video based | Upload actual video to Gemini; process public YouTube video input |
| 5.3 | Missing | Persist timestamped evidence/key points from first AI pass |
| 5.4 | Fixed subject | Generate questions exclusively from extracted evidence; validate schema |
| 5.5 | Missing | Evidence-end timestamps, sorted questions; 100ms scheduling checks |
| 5.6 | Generate again only | Retry failed job using retained media; reject concurrent duplicates |
| 6 (overall) | Unverified | Evidence-based quality report, no inferred passes |
| 6.1 | No playback | Sign in/create account; choose file; Analyze and play (3 main actions) |
| 6.2 | Missing | Queue every due question without interrupting playback; instrument lateness |
| 6.3 | Unbounded requests | Client validation, 4s ordinary-request timeout, 1s job polling |
| 6.4 | Missing | Password hashing, sessions, CSRF, ownership, TLS-only configuration |
| 6.5 | Desktop only | Responsive 360–1920px; browser/version matrix needs actual testing |
| 6.6 | Missing | 10s external-wait notice, durable upload chunks independent of AI |

Live Gemini quality, exact content timestamps, HTTPS trust, media playback and the six-browser matrix must be
reported as unverified unless actually exercised. Automated fixtures are test data only, never a production AI fallback.
