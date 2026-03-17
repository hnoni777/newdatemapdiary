---
description: How to create a formal Recovery Point (by Kodari Manager)
---

// turbo-all
Whenever the USER asks to "잡아달라" (capture/create) a recovery point, follow these steps exactly without skipping:

### 1. Identify & Bump Version Code
- Determine the next version (e.g., v1.0.1 -> v1.0.2).
- **CRITICAL**: Update `app/build.gradle.kts` with a new `versionCode` and `versionName`.

### 2. New Build Generation (The most important step)
- Run fresh build to embed latest code and assets.
```powershell
gradlew.bat assembleDebug
```
- Verify `app-debug.apk` is generated, then copy to versioned name:
```powershell
copy app\build\outputs\apk\debug\app-debug.apk NewDateMapDiary_v[VERSION]_Description.apk /y
```

### 3. Archive Master Assets
- Create backup directory: `processed_stickers_v[VERSION]`
- Copy all critical assets from `app/src/main/res/drawable-nodpi/stk_premium_*.png`.

### 4. Update Landing Page (index.html)
- Update the main `btn-primary` link to the NEW versioned APK.
- Update the version text (e.g., v10.1).
- Add a new entry to the `<section id="recovery">`.
- **Mirroring**: Ensure `intro_variant_1.html` and others are mirrored with the same content.

### 5. Git Deployment (Deployment)
- Stage, commit, and push to ensure the live server is updated.
```powershell
git add .
git commit -m "Capture Recovery Point v[VERSION]: [Description]"
git push origin master
```

### 6. Generate Manifest Artifact
- Create `RECOVERY_POINT_v[VERSION].md` documenting the state.

### 7. Final Verification
- Check the live URL if possible. Notify USER that build, versioning, and deployment are 100% complete.
