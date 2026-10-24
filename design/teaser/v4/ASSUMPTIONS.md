# Teaser v4 — assumptions and open items

Outputs
- meelano-teaser-v4-9x16-35s.mp4: 1080x1920, 30 fps, H.264 + AAC 48 kHz, 35.5 s (ideal 35 s; the 6 voice clips set the timing).
- meelano-teaser-v4-9x16-15s.mp4: same format, 15.2 s (hook + dashboard + CTA).
- meelano-teaser-v4-4x5-35s.mp4: 1080x1350, 35.5 s. meelano-teaser-v4-16x9-35s.mp4: 1920x1080, 35.5 s.
- SRT files (Persian captions, timed to voice) for each cut; poster-9x16.jpg is a dashboard frame.

Assumptions
1. The app UI is rebuilt from scratch as motion graphics with demo data. No real screenshot or real customer data is shown; all screens are drawn.
2. Store name is "فروشگاه نمونه". Figures (فروش ۴۰۰ میلیون / ۵۴ فاکتور, خرید ۱۲۰, دریافت ۲۸۰, پرداخت ۹۰ میلیون) and stock rows (بادام اصلی ۱۲ کیلو کم‌موجود, کاجو ناموجود, پسته اکبری ۸۵ کیلو) come from the brief and are treated as demo values.
3. CTA is "امروز امتحان کن". The "۱۴ روز رایگان" trial is NOT used because it is unconfirmed.
4. The meelano.ir URL is NOT shown in the video because it is unverified. Confirm the domain before adding an end-card URL.
5. Captions are placed in fixed safe zones (top band for 9:16 and 4:5; right column for 16:9), not overlapping the phone mockup.
6. Music is procedural (original, generated in code), so there is no licensing issue.
7. Voice is voice-00 (chosen in Turn 16). Two clips were sped up 10% (v2t, v5t) with pitch preserved to keep the 35 s target.
8. The 15 s cut drops the assistant scene and keeps hook, dashboard and CTA.
9. The old mockup design/teaser/meelano-teaser-30s.mp4 is not deleted (awaiting your decision).
10. Floating arrow button hiding during capture is not applicable here, since no screen recording is used.

Rebuild: python make_v4.py portrait35 | portrait15 | square35 | landscape35 (requires /home/user/teaserenv and the Vazirmatn fonts in manager/app/src/main/assets/fonts).
