---
description: How to create a formal Recovery Point (by Kodari Manager)
---

// turbo-all
Whenever the USER asks to "잡아달라" (capture/create) a recovery point, follow these steps exactly without skipping:

### 1. Identify Version Number
- Determine the next version (e.g., v1.0.1 -> v1.0.2).

### 2. Archive Current Best Assets
- Create a backup directory: `processed_stickers_v[VERSION]`
- Copy all critical assets (like stickers from `app/src/main/res/drawable-nodpi/stk_premium_*.png`) to this directory.
```powershell
mkdir processed_stickers_v[VERSION]
copy app\src\main\res\drawable-nodpi\stk_premium_*.png processed_stickers_v[VERSION]\ /y
```

### 3. Create Versioned APK
- Duplicate the latest build to a versioned name.
```powershell
copy NewDateMapDiary_v9_B148.apk NewDateMapDiary_v[VERSION]_Description.apk /y
```

### 4. Update Landing Page (index.html)
- Update the main `btn-primary` link to the new versioned APK.
- Add a new `feature-item` entry to the `<section id="recovery">` with the version summary and download links.

### 5. Generate Manifest Artifact
- Create a `RECOVERY_POINT_v[VERSION].md` artifact.
- Document:
    - Version Name & Timestamp.
    - Verified Asset List.
    - Processing Logic DNA (Nukki/Crop settings used).
    - System status.

### 6. Verify QR Code Link
- Ensure all QR generation code in `MainActivity.kt` and `CardEditorActivity.kt` points to the root landing page URL.

### 7. Final Report
- Inform the USER that the recovery point has been captured, the web page updated, and the manual followed 100%.
