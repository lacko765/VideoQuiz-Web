# VideoQuiz: Easy Setup / التشغيل السهل

## English — first run

You need Windows, Apache NetBeans with Java/Maven support, an installed **JDK 21 or 17**, internet access, and your own Gemini API key. Node.js is only needed for optional JavaScript tests. Maven is found automatically or downloaded into the project. No administrator access is normally needed.

1. Right-click **VideoQuiz-Web-EasySetup.zip → Extract All**. Open the extracted `VideoQuiz-Web` folder. Do not run files from inside the ZIP. Use a writable folder, not Program Files.
2. Double-click **setup-videoquiz.bat**. Keep its window open. It finds your JDK; if it cannot, a folder picker lets you select the JDK installation folder containing `bin` and `release`. If none is installed, install [JDK 21](https://adoptium.net/temurin/releases/?version=21), then run setup again. Setup does not install Java itself.
3. Press Enter to use port **8443** and model **gemini-3.5-flash-lite**. Obtain your own key from [Google AI Studio](https://aistudio.google.com/apikey). Paste it at the hidden prompt. Choose an HTTPS password of **at least 12 characters**, and repeat it. Hidden input is normal; your key/password will not be shown.
4. Wait for **SETUP SUCCESSFUL**. The first build downloads dependencies and can take several minutes. Setup generates a local HTTPS certificate and encrypts credentials for your Windows account on this computer. No certificate is installed in Windows trust.
5. Open NetBeans → **File → Open Project** → select the extracted `VideoQuiz-Web` folder (the one containing `pom.xml`). Wait for Maven project loading.
6. Select this project and click the green **Run Project** button (F6). Its supplied action runs the startup helper, which checks setup and builds the app using the detected JDK. It should not ask for `${start-class}`.
7. Wait for **Started Application** in Output. Open **https://localhost:8443**. If you selected a different port, use the address printed by the launcher. Create an application account and sign in. This password is separate from the HTTPS password.

Alternative: double-click **start-videoquiz.bat**. It uses the same startup helper as NetBeans. Do not start both at once. To stop, press **Ctrl+C** in that window, or use NetBeans' red **Stop** button in the Output panel. To restart, run the same launcher again.

## First HTTPS visit

The certificate is self-signed, so your browser may display a privacy warning. This is expected for a new local installation and is **not proof of browser-trusted HTTPS**. Verify the address is exactly `https://localhost:8443` (or your chosen port) and that you started this copy. You may manually choose the browser's local-development exception if your policy permits it. Never do this for an unfamiliar internet site. If the browser offers no exception, ask your university IT team to provide an approved certificate/trust setup. Nothing in these scripts installs trust, disables browser security, or changes Windows certificate settings.

The BAT files use an execution-policy override only for their child PowerShell process; they do not change the saved Windows execution policy. If university policy blocks scripts, ask IT rather than disabling that policy.

## Gemini and playback

- Use a project with free-tier quota and no billing if you want to avoid paid usage. The app does not enable billing. Google's availability, quota and pricing can change; check [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing). A listed model does not guarantee your key can generate with it.
- The default model passed a real generation request during this delivery. `gemini-2.5-flash` returned 404 on this machine despite appearing in the model list.
- To change the key/model, stop the app and rerun setup. Choose **n** when asked to keep saved credentials, then enter your new key and the existing HTTPS password. Keep the original HTTPS password while reusing the certificate.
- Upload an H.264 MP4 (AAC audio recommended), up to 256 MiB / 30 minutes, or enter a public recorded YouTube URL. Gemini receives the actual selected content. Do not submit private material you cannot share with Google.
- YouTube must load its duration before analysis. If necessary press Play, wait, then Retry. Private, deleted, live, blocked, or non-embeddable videos are unsupported.
- The timer updates during analysis. Questions appear at their evidence timestamps after analysis. Next dismisses answered cards without moving the video. Manual seeking shows newly due questions once; answered questions stay in results/history.
- Retry reuses the stored video/link. Invalid evidence or questions get at most two generation attempts per stage. Jobs have a 12-minute deadline. For rate limits, wait before retrying; automatic retries do not bypass quota.

## Quick fixes

| Problem | What to do |
|---|---|
| Java not found / unsupported Java | Install JDK 21 or 17, rerun setup, and select its folder if prompted. A JRE alone is insufficient. JDK 26 was also tested here, but 17/21 remain recommended. |
| Maven download/build error | Check internet and university proxy access to Maven Central / Apache downloads. Rerun setup; downloaded dependencies are reused. Read the first ERROR in the window. |
| NetBeans main-class error | Open the correct Maven folder containing the supplied `nbactions.xml`; close/reopen the project if it was open before extraction. Use start-videoquiz.bat while correcting an old custom Run action. |
| Port 8443 already in use | Stop your previous VideoQuiz window normally. If another application needs that port, rerun setup and choose 8444; use the newly printed URL. The scripts never kill other processes. |
| Cannot unlock saved credentials | Run setup under your own Windows account. Encrypted settings cannot be shared between computers/accounts. Extract a fresh delivery ZIP if you copied someone else's working folder. |
| Certificate/password mismatch | Rerun setup with the password used to create this certificate. Existing certificates are never overwritten. If forgotten/expired, keep a private backup and extract a fresh delivery ZIP to generate a new certificate. |
| AI 401/403 | Rerun setup with a valid Gemini key allowed to use the selected model. |
| AI 404 | Choose a currently available video-capable model, or try another accessible video. |
| AI 429 | Wait for quota to reset/check free-tier quota in AI Studio. Repeated Retry clicks cannot fix quota. |
| Preparing/connection lost | Check the progress message. Reopen the saved video from My learning or use Retry. Content remains stored; no re-upload is needed after completed uploads. |

Never send your teammate your working `config`, `data`, `.tools`, or `backups` folders. Send the clean delivery ZIP. Keep your own data/backups private.

## العربية — التشغيل لأول مرة

تحتاج إلى Windows وApache NetBeans مع دعم Java/Maven و**JDK 21 أو 17** واتصال بالإنترنت ومفتاح Gemini خاص بك. لا تحتاج إلى كتابة أوامر PowerShell. يتم اكتشاف Maven أو تنزيله تلقائياً داخل المشروع، ولا تحتاج عادةً إلى صلاحيات مسؤول.

1. اضغط بزر الفأرة الأيمن على **VideoQuiz-Web-EasySetup.zip** واختر **Extract All / استخراج الكل**. افتح مجلد `VideoQuiz-Web` المستخرج، ولا تشغّل الملفات من داخل ZIP.
2. شغّل **setup-videoquiz.bat** بالنقر المزدوج. يكتشف البرنامج JDK تلقائياً. إذا لم يجده، اختر مجلد JDK الذي يحتوي على `bin` و`release`. إذا لم يكن مثبتاً، ثبّت JDK 21 من الرابط أعلاه ثم أعد الإعداد.
3. اضغط Enter لاستخدام المنفذ **8443** والموديل **gemini-3.5-flash-lite**. أنشئ مفتاحك من [Google AI Studio](https://aistudio.google.com/apikey)، ثم الصقه في حقل الإدخال المخفي. أدخل كلمة مرور HTTPS من **12 حرفاً على الأقل** وأعد إدخالها. عدم ظهور النص أثناء الكتابة أمر طبيعي.
4. انتظر رسالة **SETUP SUCCESSFUL**. قد يستغرق تنزيل المكتبات والبناء عدة دقائق. تُحفظ الأسرار مشفرة لحساب Windows الحالي فقط. يتم إنشاء شهادة محلية دون إضافتها إلى شهادات Windows الموثوقة.
5. افتح NetBeans ثم **File → Open Project** واختر مجلد المشروع الذي يحتوي على `pom.xml`.
6. اختر المشروع واضغط زر **Run Project** الأخضر أو F6. انتظر ظهور **Started Application** في نافذة Output.
7. افتح **https://localhost:8443**، ثم أنشئ حساباً للتطبيق وسجّل الدخول. كلمة مرور الحساب منفصلة عن كلمة مرور شهادة HTTPS.

يمكنك أيضاً تشغيل **start-videoquiz.bat** مباشرة. لا تشغّل نسختين في الوقت نفسه. للإيقاف اضغط **Ctrl+C** في نافذة التشغيل، أو زر Stop الأحمر في Output داخل NetBeans. لإعادة التشغيل استخدم نفس الملف أو زر Run.

### تنبيه شهادة HTTPS

الشهادة محلية وموقّعة ذاتياً، لذلك قد يظهر تحذير خصوصية. تأكد أن العنوان هو `localhost` والمنفذ الذي اخترته وأن التطبيق الذي شغّلته هو المقصود. يمكنك اختيار الاستثناء المحلي يدوياً إن كانت سياسة جهازك تسمح بذلك. لا تتجاوز تحذيراً لموقع إنترنت غير معروف. إذا لم يسمح المتصفح، اطلب من دعم الجامعة إعداد شهادة معتمدة. لا تثبّت السكربتات شهادة موثوقة ولا تغيّر أمان Windows أو المتصفح. تجاوز سياسة تشغيل PowerShell يخص عملية الإعداد فقط، ولا يغيّر السياسة المحفوظة للنظام.

### حلول سريعة

- **مشكلة Java:** ثبّت JDK 21 أو 17 (وليس JRE فقط)، ثم أعد setup وحدد مجلده عند الحاجة.
- **مشكلة Maven:** تحقق من الإنترنت أو إعدادات شبكة الجامعة، ثم أعد setup. لا تُحذف التنزيلات السابقة.
- **خطأ `${start-class}`:** افتح مجلد Maven الصحيح وتأكد من وجود `nbactions.xml`. أغلق المشروع وأعد فتحه. ملف start-videoquiz.bat بديل يستخدم نفس المشغّل.
- **المنفذ 8443 مشغول:** أوقف النسخة السابقة بشكل طبيعي، أو أعد setup واختر 8444 واستخدم العنوان الجديد. لن يتم إيقاف أي برنامج آخر تلقائياً.
- **مشكلة كلمة مرور الشهادة:** أدخل كلمة المرور الأصلية. لا يتم استبدال الشهادة الموجودة. إذا نسيتها، احتفظ بنسخة خاصة واستخرج حزمة نظيفة في مجلد جديد.
- **مفتاح Gemini غير صالح:** أوقف التطبيق وأعد setup، واختر عدم الاحتفاظ بالأسرار القديمة ثم أدخل مفتاحك وكلمة مرور الشهادة الأصلية.
- **خطأ 404:** قد يكون الموديل أو الفيديو غير متاح. اختر موديل فيديو متاحاً لحسابك أو فيديو آخر.
- **خطأ 429:** انتظر تجدد الحصة المجانية وتحقق من AI Studio. الضغط المتكرر على Retry لا يتجاوز حدود الخدمة.
- **الفيديو لا يبدأ أو لا تظهر مدته:** استخدم فيديو YouTube عاماً ومسجلاً، اضغط Play وانتظر التحميل ثم Retry. البث المباشر والمحتوى الخاص أو المحظور غير مدعوم.
- **فشل التحليل:** زر Retry يعيد استخدام الفيديو أو الرابط المحفوظ؛ لا تحتاج إلى رفعه من جديد. تظهر رسائل تقدم واضحة، ويتوقف التحليل بعد 12 دقيقة كحد أقصى.
- **مفتاحك والبيانات:** لا تشارك `config` أو `data` أو `.tools` أو `backups`. شارك حزمة ZIP النظيفة فقط. استخدم مشروع Gemini بحصة مجانية دون تفعيل الفوترة إذا أردت تجنب الاستخدام المدفوع.

زر Next لا يغيّر موقع الفيديو، والأسئلة تظهر حسب الوقت، والإجابات السابقة محفوظة في النتائج والسجل.
