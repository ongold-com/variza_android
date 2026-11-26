# Variza SMS Forwarder

اپ اندروید شخصی: پیامک‌های ورودی (همه‌ی سیم‌کارت‌ها) از شماره‌هایی که خودت تعیین می‌کنی را عیناً با `POST` JSON به یک آدرس بک‌اند می‌فرستد. همیشه در بک‌گراند فعال است، بعد از ری‌استارت گوشی خودش بالا می‌آید و در صورت خطا هر ۱۰ ثانیه دوباره تلاش می‌کند.

Personal native-Kotlin Android app. Forwards SMS from a user-managed list of senders to a backend URL as JSON, with an API-key header (from `.env` / GitHub secret), automatic retry every 10 s, foreground service and boot persistence.

## راهنما (به ترتیب بخوان)
1. [docs/01-github-setup.md](docs/01-github-setup.md) — ساخت APK روی GitHub
2. [docs/02-install-on-phone.md](docs/02-install-on-phone.md) — نصب و تنظیم گوشی
3. [docs/03-backend-and-testing.md](docs/03-backend-and-testing.md) — درخواست‌های ورود/ارسال و تست
4. [docs/04-signing-key.md](docs/04-signing-key.md) — کلید امضا (یک‌بار)

## ساختار
- `app/src/main/java/com/variza/smsforwarder/` — کد کاتلین
- `.github/workflows/build.yml` — بیلد خودکار APK
- `.github/workflows/generate-keystore.yml` — ساخت کلید امضا (یک‌بار)
- `.env.example` — نمونه‌ی فایل کلید API برای بیلد لوکال
