# VideoQuiz: $0 cloud deployment

Use **Render Free + Supabase Free**. Do not add a card, upgrade plans, enable billing, or select paid storage. If either account requires card verification, stop: deployment is blocked under the $0/no-card requirement. No public deployment has been verified yet.

## 1. Create accounts

Sign up at https://render.com and https://supabase.com using GitHub sign-in. Choose Free plans. You must complete authentication yourself. Do not send passwords, API keys or database connection strings in chat.

## 2. Supabase Free project

1. Create a project in a Free organization. Choose a nearby region and generate a strong database password; save it privately.
2. In Storage, create a bucket named `videoquiz-private`. Keep **Public bucket OFF**. Allow `application/octet-stream` if restricting MIME types. Each object is at most 1 MiB; the app reconstructs MP4 videos from these private chunks, preserving the 256 MiB video limit.
3. In the project's Connect dialog, select **Session pooler** (port **5432**, IPv4-compatible). Copy the hostname and username separately. Do not select transaction pooling/port 6543.
4. In project API settings, locate the project URL and the legacy **service_role** key (server-only secret, not the anon key). Put these only in Render secret variables.
5. The application creates tables in `videoquiz_private`, separate from the public Data API schema. Never add this schema to exposed schemas. Do not add anonymous storage policies or make the bucket public. The app checks bucket privacy on startup.

## 3. Render Free service

1. Choose New > Blueprint and connect `lacko765/VideoQuiz-Web`, branch main. Review `render.yaml`: there is exactly one **plan: free** web service and no paid disk/database.
2. If Blueprint creation requests billing, cancel. Try New > Web Service, connect the same repository, choose Docker and **Free** manually. If that also requests a card, stop.
3. Enter these Environment variables in Render, never in GitHub files:

| Variable | Value |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod,cloud |
| GEMINI_MODEL | gemini-3.5-flash-lite |
| GEMINI_API_KEY | Your free-tier Gemini API key |
| VIDEOQUIZ_DB_HOST | Session pooler hostname ending in .pooler.supabase.com |
| VIDEOQUIZ_DB_USER | Session pooler username, typically postgres.PROJECTREF |
| VIDEOQUIZ_DB_PASSWORD | Supabase database password |
| SUPABASE_URL | https://PROJECTREF.supabase.co |
| SUPABASE_SERVICE_ROLE_KEY | Legacy service_role secret key |

4. Use `/healthz` as the health check. Render supplies PORT and a public `https://...onrender.com` address. No local certificate or TLS password is used. The app handles forwarded HTTPS headers; expose the container only through the trusted platform proxy.
5. Deploy. For automatic updates, enable autodeploy after checks pass on main. The Blueprint requests this setting. GitHub Actions builds/tests Java 17 and 21 plus Docker. If not supported by your dashboard, select the equivalent autodeploy option manually.
6. Open the actual Render URL and complete the acceptance checks below before sharing it. Creating configuration files alone is not successful deployment.

## Free-plan limitations

Render sleeps after 15 minutes without inbound traffic and can take about a minute to wake. It supplies 750 free instance hours/month; bandwidth/build quotas also apply. With no payment method, excess usage suspends service/builds rather than buying more. Do not add artificial traffic to avoid sleep. This is a university demo, not guaranteed always-on hosting.

Supabase Free includes 500 MB database, 1 GB file storage and limited egress (currently 5 GB); inactive projects can pause after one week. Individual objects have a 50 MB maximum; our 1 MiB chunks stay below it. Chunking does not increase the total allowance. VideoQuiz reserves at most 800 MiB for uploaded videos, leaving headroom. Interrupted database commits can leave unreferenced objects; monitor storage and do not delete objects referenced by the chunks table. Provider limits can still stop uploads before the application quota. Restore paused projects before a presentation.

Gemini free-tier quotas/model access vary by project and can reject requests. Keep billing disabled on the Google project. Retry Analysis preserves the video but cannot override provider quotas. Nothing guarantees free AI availability at all times.

## Persistence and security

Accounts, hashed passwords, videos' metadata, evidence/questions and quiz attempts are saved in SQL. Video bytes are private objects. Local container files are a disposable cache rebuilt from those objects. Upload progress is acknowledged only after object storage and a metadata transaction. SQL revision checks reject conflicting writes; they do not provide multi-instance scaling. Run one service/replica. Brief old/new deployment overlap can require a retry. Global admission/rate limits are in-memory per process and not a billing cap.

A restart invalidates login sessions; sign in again. Interrupted analyses become retryable after the 12-minute stale-job deadline plus up to one minute for recovery. Database outages return safe errors; reconnection is checked each minute. Existing local data is not automatically uploaded. Cloud starts with new accounts; the original NetBeans data remains on your PC. Never upload local backups, secrets or localhost.p12 to the host.

Existing limits remain: 256 MiB/MP4, 1 MiB/chunk, recorded YouTube videos up to 30 minutes, 30 videos/user, 500 attempts/user. Public demo defaults add 100 accounts, six analyses/user/hour, 30 total/hour, two workers and four analysis slots. Keep the audience small and use short videos. No automatic deletion of personal data is implemented.

Keep separate private backups of the `videoquiz_private` database schema **and** the private bucket; one without the other is incomplete. Free plans do not supply the same backup guarantees as paid plans. Before cleanup, compare stored object keys with `videoquiz_private.chunks`. Never delete a project/bucket to restart the app. To stop public access, suspend the Render service. To update, push tested code to main. To roll back, use Render's deployment history without deleting Supabase data. Rotate secrets in provider dashboards and redeploy if compromised.

## Acceptance checks after deployment

- Public HTTPS opens from another computer; no certificate warning.
- Register, log out, sign in; one user cannot see another user's videos.
- Upload a small MP4 and a larger video if needed; playback and seeking work.
- Analyze real content with Gemini, including two supported YouTube durations.
- Retry after a recoverable failure without reuploading.
- Questions follow actual timestamps, Next does not seek, answers score once, results/history remain.
- Restart the service and verify accounts, videos and history; resume an interrupted upload.
- Verify secrets are absent from browser source/network responses and public repository.

## Troubleshooting

Build failure: inspect GitHub Actions and Render build logs. Database error: check Session pooler host/user/password and port 5432; a paused project must be resumed. TLS verification must remain enabled. Private bucket error: use the correct project, legacy service_role key and a non-public bucket. 503 storage errors: check Supabase quota/connectivity and wait for reconnect. Memory failure on Free: use short videos and a small demo audience; do not silently upgrade to paid. HTTPS loop: confirm prod,cloud profiles and the platform proxy. Never paste secrets into logs/support messages.

## بالعربية

أنشئ حسابين مجانيين في Render وSupabase باستخدام GitHub. لا تضف بطاقة ولا تختَر خطة مدفوعة. إذا طُلبت بطاقة فتوقف وأخبرني.

في Supabase أنشئ مشروعاً مجانياً وحاوية خاصة باسم videoquiz-private. انسخ إعدادات Session pooler ومفتاح service_role إلى متغيرات Render السرية فقط. في Render اربط المستودع واختر Docker والخطة Free، ثم أدخل المتغيرات الموجودة في الجدول. افتح رابط HTTPS الذي يظهر بعد نجاح النشر واختبر التسجيل والفيديو والأسئلة والنتائج وإعادة التشغيل.

الخدمة المجانية تنام عند عدم الاستخدام ولها حدود تخزين ونقل بيانات. لا تعدّ الرابط جاهزاً قبل الاختبار الفعلي. تشغيل NetBeans المحلي وبياناته لا يتغيران.

Sources checked 9 November 2026: [Render Free](https://render.com/docs/free), [Supabase pricing](https://supabase.com/pricing), [file limits](https://supabase.com/docs/guides/storage/uploads/file-limits), [database connections](https://supabase.com/docs/guides/database/connecting-to-postgres).
