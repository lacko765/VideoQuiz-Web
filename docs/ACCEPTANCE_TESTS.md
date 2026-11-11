# Remaining live acceptance tests

Run after `mvn clean verify` succeeds, a trusted HTTPS certificate is configured, and the server can reach Gemini/YouTube.
Record exact OS/browser versions and dates; do not replace the results in TEST_RESULTS.md with assumptions.

## Build and security

1. Use JDK 17 or 21 and run `mvn clean verify`. Record build exit code, Surefire count and failures.
2. Start through NetBeans. Verify HTTPS on the configured hostname and a trusted certificate; verify HTTP does not serve private data.
3. Create two test accounts. Upload as account A. Log out; verify media, video details, attempts and answers return 401.
4. Sign in as B; request A's IDs and byte-range media. Expect 404. Confirm B's history does not include A.
5. Omit/change CSRF token on registration, login, chunk upload and answer POST. Expect rejection.
6. Confirm the API never exposes keys, stack traces, filesystem paths, unsubmitted answer keys or source evidence that reveals answers early.

## Upload, analysis and playback

1. Choose a real short H.264/AAC MP4 with 3–5 known educational facts at recorded timestamps. Keep a manual expected-fact sheet.
2. From a fresh account, count the steps to first playback; record how browser autoplay/audio controls affect the count.
3. Upload and analyze. Confirm actual Gemini requests and successful content processing (not merely a successful connectivity request).
4. Check each extracted fact against audio/visual evidence. Check each question, correct answer and explanation; record mismatches.
5. Compare each assigned timestamp to the moment the evidence finishes. Record semantic timestamp error independently of display delay.
6. Play from the start. Leave an early question unanswered while later timestamps pass; all newly due questions must still appear.
7. Use `videoQuizTiming()` in browser developer tools to export scheduled/display times. Exclude deliberate seek jumps;
   during normal foreground playback every `lateSeconds` should be ≤2 seconds. Repeat at 0.5x, 1x, 2x.
8. Verify video remains visible on 360px mobile width while answering; playback continues unless the user pauses it.
9. Test Next before/after answering, backward/forward seeks, correct/wrong answers, repeated submit clicks, final result and refresh.
10. Review a previous attempt, revisit its evidence moment and create a new attempt. The old answers/score must remain unchanged.

## Links and errors

1. Use a public educational YouTube video which allows embedding and is supported by the selected model. Repeat semantic/playback checks.
2. Test a private/deleted/non-embeddable video. Expect an explanatory service/player error; do not invent substitute questions.
3. Test an arbitrary URL, localhost URL, HTTP URL, wrong extension, renamed non-video file, damaged MP4, oversized file and >30-minute video.
4. Time from known input/processing failure to visible message; target ≤5 seconds. Distinguish pending AI work from a declared failure.
5. Remove key/use invalid key/quota exhaustion in a disposable test configuration. Verify cause-oriented errors and retained inputs.

## Outage and retention

1. Interrupt an upload after multiple acknowledged chunks. Refresh, sign back in if necessary, reselect the exact file and resume.
2. Select a different file with the same name/size; expect SHA-256 mismatch rather than joining incompatible chunks.
3. Make the external service unreachable or deliberately slow in a controlled test environment; observe the >10-second status notice.
4. Restore connectivity and Retry analysis without uploading again. Confirm saved bytes are unchanged.
5. Restart the server during analysis. Confirm the retained job becomes FAILED with a retry explanation and completes when retried.
6. Fail an answer request after the server saved it, then retry. The first saved answer and score must remain unchanged.

## Browser matrix — fill with actual versions

| Browser | Version | 360px | 768px | 1280px | 1920px | Playback / quiz / history | Result |
|---|---|---|---|---|---|---|---|
| Chrome latest | Pending | Pending | Pending | Pending | Pending | Pending | Not run |
| Chrome previous | Pending | Pending | Pending | Pending | Pending | Pending | Not run |
| Safari latest | Pending | Pending | Pending | Pending | Pending | Pending | Not run |
| Safari previous | Pending | Pending | Pending | Pending | Pending | Pending | Not run |
| Edge latest | Pending | Pending | Pending | Pending | Pending | Pending | Not run |
| Edge previous | Pending | Pending | Pending | Pending | Pending | Pending | Not run |

Test desktop Safari on macOS and mobile Safari if mobile support is claimed. Check keyboard navigation, visible focus,
readable error messages, non-overflowing controls and simultaneous player/question visibility. Capture evidence before marking 6.5 passed.
