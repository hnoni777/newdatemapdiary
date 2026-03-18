# 🛡️ Recovery Point v1.12.0: Ghost Cleaner Edition

## Metadata
- **Date**: 2026-03-18
- **Version**: v1.12.0 (versionCode 161)
- **APK**: `NewDateMapDiary_v1_12_0_GhostCleaner.apk`
- **Purpose**: Ghost memory cleanup syncing logic & 10 new premium 3D stickers.

## What's Included
1. **Ghost Memory Cleaner**: 
   - `MemoryMapActivity.kt` onResume explicitly syncs with the phone's gallery. Missing `content://` URIs result in complete deletion from the internal DB, fixing "ghost popups" left after user gallery deletions.
2. **Additional Premium Stickers**: 
   - 10 basic stickers successfully migrated to premium 3D clay-morphism designs.
   - All 39 premium stickers compressed to optimized sizes (400px width/height limits) using custom tool `StickerOptimizer.java`, resolving large file concerns.
3. **Recovery Architecture Updates**:
   - `processed_stickers_v1.12.0` folder initialized and populated with optimized transparent sticker assets.
   - HTML landing page (`index.html`) correctly routing main CTA to v1.12.0.

## Commands Executed
- Build: `./gradlew.bat assembleDebug`
- Version bumped in `app/build.gradle.kts`.
- APK safely cached for users to reinstall this precise checkpoint.
