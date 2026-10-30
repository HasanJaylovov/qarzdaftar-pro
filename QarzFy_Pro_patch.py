#!/usr/bin/env python3
"""QarzFy Pro UI/branding + GitHub Actions patch.
Run from the Android project root: python3 QarzFy_Pro_patch.py
This patch intentionally does not touch database, business logic, or app features.
"""
from pathlib import Path
from datetime import datetime
import shutil
import sys

ROOT = Path.cwd()
MAIN = ROOT / 'app/src/main/java/uz/qarzdaftar/pro/MainActivity.kt'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
GRADLE = ROOT / 'app/build.gradle'
WORKFLOW = ROOT / '.github/workflows/build-apk.yml'

required = [MAIN, MANIFEST, GRADLE]
missing = [str(p.relative_to(ROOT)) for p in required if not p.is_file()]
if missing:
    print('XATO: loyiha root papkasida kerakli fayllar topilmadi:')
    print('\n'.join(' - ' + x for x in missing))
    print('Terminalda Android loyiha papkasiga kirib, patchni qayta ishga tushiring.')
    sys.exit(1)

stamp = datetime.now().strftime('%Y%m%d-%H%M%S')
backup_dir = ROOT / f'.qarzfy-patch-backup-{stamp}'

# Exact replacements with count guards prevent accidental edits to unexpected code.
def replace_checked(text, old, new, expected, label):
    count = text.count(old)
    if count == 0 and new in text:
        return text, 0  # already patched; safe to run again
    if count != expected:
        raise RuntimeError(f'{label}: kutilgan {expected} ta moslik, topildi {count}. Fayl o\'zgartirilmadi.')
    return text.replace(old, new), count

try:
    main_text = MAIN.read_text(encoding='utf-8')
    changes = []
    for old, new, expected, label in [
        ('Color.parseColor("#F5F7FF")', 'Color.parseColor("#F4F5FC")', 4, 'Asosiy fon rangi'),
        ('R.drawable.ic_qarzdaftar_logo', 'R.drawable.ic_qarzfy_logo', 4, 'Logo resursi'),
        ('setPadding(16, 12, 16, 112)', 'setPadding(18, 16, 18, 116)', 1, 'Workspace padding'),
        ('LinearLayout.LayoutParams(44, 44).apply { rightMargin = 14 }', 'LinearLayout.LayoutParams(48, 48).apply { rightMargin = 12 }', 1, 'Chap menyu tugmasi'),
        ('LinearLayout.LayoutParams(46, 46).apply { rightMargin = 12 }', 'LinearLayout.LayoutParams(52, 52).apply { rightMargin = 12 }', 1, 'Header logo o‘lchami'),
        ('LinearLayout.LayoutParams(44, 44).apply { rightMargin = 10 }', 'LinearLayout.LayoutParams(48, 48).apply { rightMargin = 8 }', 1, 'Chat tugmasi'),
        ('LinearLayout.LayoutParams(44, 44)', 'LinearLayout.LayoutParams(48, 48)', 1, 'QR tugmasi'),
        ('setPadding(26, 22, 26, 22)', 'setPadding(24, 24, 24, 24)', 1, 'Hero card padding'),
        ('text = "🏪 Tezkor qarz va to\'lovlar nazorati 🚩"', 'text = "QarzFy Pro  •  Tezkor moliyaviy boshqaruv"', 1, 'Hero sarlavhasi'),
        ('textSize = 14.5f', 'textSize = 17f', 1, 'Hero sarlavha o‘lchami'),
    ]:
        main_text, n = replace_checked(main_text, old, new, expected, label)
        if n:
            changes.append(f'MainActivity.kt: {label} ({n})')

    manifest_text = MANIFEST.read_text(encoding='utf-8')
    manifest_repls = [
        ('android:icon="@drawable/ic_qarzdaftar_logo"', 'android:icon="@drawable/ic_qarzfy_logo"'),
        ('android:roundIcon="@drawable/ic_qarzdaftar_logo"', 'android:roundIcon="@drawable/ic_qarzfy_logo"'),
        ('android:label="QarzFy"', 'android:label="QarzFy Pro"'),
    ]
    for old, new in manifest_repls:
        if old in manifest_text:
            manifest_text = manifest_text.replace(old, new, 1)
            changes.append('AndroidManifest.xml: ' + old.split('=')[0])
        elif new not in manifest_text:
            raise RuntimeError(f'Manifestda kutilgan qator topilmadi: {old}')

    gradle_text = GRADLE.read_text(encoding='utf-8')
    if 'versionCode 171' in gradle_text:
        gradle_text = gradle_text.replace('versionCode 171', 'versionCode 172', 1)
        changes.append('app/build.gradle: versionCode 172')
    elif 'versionCode 172' not in gradle_text:
        raise RuntimeError('app/build.gradle ichida versionCode 171 yoki 172 topilmadi.')
    if 'versionName "17.1-AutoSaveExportFixed"' in gradle_text:
        gradle_text = gradle_text.replace('versionName "17.1-AutoSaveExportFixed"', 'versionName "17.2-QarzFy-Redesign"', 1)
        changes.append('app/build.gradle: versionName 17.2-QarzFy-Redesign')
    elif 'versionName "17.2-QarzFy-Redesign"' not in gradle_text:
        raise RuntimeError('app/build.gradle ichida kutilgan versionName topilmadi.')

    workflow_text = '''name: QarzFy Pro - Android APK\n\non:\n  push:\n    branches: [ main, master ]\n  pull_request:\n    branches: [ main, master ]\n  workflow_dispatch:\n\npermissions:\n  contents: read\n\nconcurrency:\n  group: android-build-${{ github.ref }}\n  cancel-in-progress: true\n\njobs:\n  build:\n    name: Build Debug APK\n    runs-on: ubuntu-latest\n    timeout-minutes: 30\n    steps:\n      - name: Checkout source\n        uses: actions/checkout@v4\n      - name: Set up JDK 17\n        uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: '17'\n      - name: Set up Gradle\n        uses: gradle/actions/setup-gradle@v4\n        with:\n          gradle-version: '8.5'\n      - name: Build APK\n        run: gradle --no-daemon assembleDebug\n      - name: Upload APK\n        uses: actions/upload-artifact@v4\n        with:\n          name: QarzFy-Pro-debug-APK\n          path: app/build/outputs/apk/debug/app-debug.apk\n          if-no-files-found: error\n          retention-days: 14\n'''

    # Ensure the new logo drawable exists before changing references.
    logo = ROOT / 'app/src/main/res/drawable/ic_qarzfy_logo.xml'
    if not logo.is_file():
        raise RuntimeError('ic_qarzfy_logo.xml topilmadi; logo fayli mavjud bo‘lmagani uchun patch to‘xtatildi.')

    backup_dir.mkdir()
    for p in [MAIN, MANIFEST, GRADLE]:
        dest = backup_dir / p.relative_to(ROOT)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, dest)
    if WORKFLOW.exists():
        dest = backup_dir / WORKFLOW.relative_to(ROOT)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(WORKFLOW, dest)

    MAIN.write_text(main_text, encoding='utf-8')
    MANIFEST.write_text(manifest_text, encoding='utf-8')
    GRADLE.write_text(gradle_text, encoding='utf-8')
    WORKFLOW.parent.mkdir(parents=True, exist_ok=True)
    WORKFLOW.write_text(workflow_text, encoding='utf-8')

    print('QarzFy Pro patch muvaffaqiyatli qo‘llandi.')
    print('O‘zgargan qismlar:')
    for item in changes:
        print(' - ' + item)
    print(' - .github/workflows/build-apk.yml: GitHub Actions APK build')
    print(f'Backup: {backup_dir.relative_to(ROOT)}')
    print('\nKeyingi tekshiruv:')
    print('./gradlew assembleDebug')
    print('\nBuild muvaffaqiyatli bo‘lsa, keyin git add, commit va push qilamiz.')
except Exception as exc:
    print('PATCH XATOSI:', exc)
    print('Hech qanday GitHub push bajarilmadi.')
    sys.exit(2)
