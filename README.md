# Osaid File (تطبيق الأندرويد)

تطبيق أندرويد متكامل يجمع بين مميزات **Osaid File** على الآيفون وميزة **التنزيل الخاص بأسيد (Osaid Downloader)** على الهاتف مباشرة:

## المميزات
1. **التنزيل الخاص بأسيد (Osaid Downloader):**
   - محرك `yt-dlp` + `FFmpeg` + `aria2c` مدمج محلياً عبر `youtubedl-android`.
   - يدعم أكثر من **1800 موقع** (YouTube، TikTok، Instagram، Facebook، X/Twitter، Reddit، SoundCloud...).
   - اختيار الجودة (أفضل جودة MP4، 1080p، 720p، 480p، صوت MP3 / M4A).
   - دعم لصق عدة روابط، وتنزيل ودمج الترجمة العربية/الإنجليزية، وتضمين الصورة المصغرة والبيانات، وتخطي الإعلانات (SponsorBlock)، وقص مقطع محدد.
   - استقبال الروابط مباشرة عبر زر **مشاركة (Share)** من أي تطبيق نحو **Osaid File**.
   - تحديث محرك `yt-dlp` من داخل التطبيق بضغطة زر.
2. **استيراد الصور والفيديو إلى المعرض (MediaStore):**
   - يمسح مجلد `Documents/Osaid File` ومجلد التطبيق الوارد.
   - يستورد الصور والفيديو إلى المعرض (`Pictures/Osaid File` و`Movies/Osaid File`) مع منع التكرار ببصمة SHA-256 وحذف الملفات المؤقتة بعد النجاح.
3. **نقل النصوص والحافظة مع الكمبيوتر:**
   - عرض ونسخ وإنشاء وحذف ملفات النصوص، وإرسالها إلى الكمبيوتر عبر مجلد `إلى الكمبيوتر` أو لاسلكياً عبر جسر Wi-Fi.

---

## البناء التلقائي عبر GitHub Actions (APK)
1. أنشئ مستودعاً جديداً على GitHub (مثلاً `osaid-android`).
2. ارفع محتويات مجلد `Project Files\OsaidAndroid\` بالكامل (مع مجلد `.github/workflows/build.yml`).
3. من تبويب **Actions** ← **Build Android APK** ← **Run workflow**.
4. بعد اكتمال البناء، نزّل **OsaidFile-Android-APK** من قسم **Artifacts** وثبّت ملف `OsaidFile-Android.apk` على أي هاتف أندرويد.
