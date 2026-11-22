# ۲) نصب روی گوشی و تنظیمات

## نصب
1. فایل `variza-sms.apk` رو روی گوشی باز کن.
2. اگه اندروید گفت نصب از این منبع مجاز نیست، **Settings** رو بزن و برای همون برنامه‌ای که APK رو باهاش باز کردی (مرورگر/فایل‌منیجر/تلگرام) **Allow from this source** رو روشن کن، برگرد و **Install** بزن.
3. اگه Play Protect هشدار داد: **More details** ← **Install anyway**. (اپ شخصیه و توی پلی‌استور نیست؛ این هشدار عادیه.)

## مجوزها (بار اول که اپ رو باز می‌کنی)
- **پیامک (Receive SMS)**: حتماً **Allow**.
- **اعلان (Notifications)**: **Allow**. نوتیفیکیشن کم‌اهمیت «Variza SMS فعال است» همون چیزیه که اپ رو زنده نگه می‌داره؛ پاکش نکن.
- اگه اندروید مجوز پیامک رو به‌خاطر نصب دستی بست («Restricted setting»): **Settings ← Apps ← Variza SMS ← سه‌نقطه‌ی بالا ← Allow restricted settings**، بعد دوباره مجوز پیامک رو بده.

## برای اینکه همیشه بک‌گراند کار کنه (خیلی مهم)
1. توی اپ دکمه‌ی **خاموش کردن بهینه‌سازی باتری** رو بزن و **Allow** کن.
2. **Settings ← Apps ← Variza SMS ← Battery** رو روی **Unrestricted / بدون محدودیت** بذار.
3. بر اساس برند گوشی:
   - **Xiaomi / Redmi / Poco:** Apps ← Manage apps ← Variza SMS ← **Autostart** روشن؛ Battery saver روی **No restrictions**. توی Recent apps اپ رو قفل کن.
   - **Samsung:** Settings ← Battery ← Background usage limits ← **Never sleeping apps** ← Variza SMS رو اضافه کن.
   - **Huawei / Honor:** Battery ← App launch ← Variza SMS ← **Manage manually** و هر سه گزینه روشن.
   - **Oppo / Realme / OnePlus:** Battery ← App battery management ← Variza SMS ← **Allow background activity** و Auto-launch روشن.
4. اپ رو **Force stop نکن**. کشیدنش از لیست اپ‌های باز مشکلی نداره.

## استفاده
1. **آدرس بک‌اند** رو وارد کن (مثلاً `https://example.com/api/sms`) و **ذخیره** بزن.
2. **تست اتصال** رو بزن؛ باید «✓ تست موفق بود» ببینی.
3. شماره‌ها رو یکی‌یکی اضافه کن (`09121234567` و `+989121234567` هر دو جواب می‌دن). برای فرستنده‌های اسم‌دار (مثل بانک) خود اسم رو بنویس.
4. هر پیامکی که از این شماره‌ها به هر سیم‌کارتت بیاد خودکار به بک‌اند می‌ره و وضعیتش توی «آخرین پیام‌ها» دیده می‌شه.
