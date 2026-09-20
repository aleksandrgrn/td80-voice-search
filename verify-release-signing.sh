#!/usr/bin/env bash
# Падает, если release-APK подписан не ключом TD80 — например, откатом на debug-ключ
# или потерей td80.keystore.path из local.properties (тогда APK выйдет неподписанным).
set -euo pipefail
cd "$(dirname "$0")"

# Отпечаток, а не DN: DN — произвольная строка, её можно повторить в чужом keystore.
# Поверх опубликованного APK встанет только сборка с этим сертификатом.
EXPECTED_SHA256="00c4db335f8bfe4e6430526b2f682106b47473ce7f40fecfd56ec388ef3aff3c"
APK=app/build/outputs/apk/release/app-release.apk

fail() { echo "ПРОВАЛ: $*"; exit 1; }

# sdk.dir имеет приоритет: именно им собирает Gradle. Пробелы вокруг '=' и CRLF допустимы.
# Проверка на существование файла обязательна: без неё sed вернёт 2 и set -e убьёт
# скрипт раньше, чем сработает запасной путь ниже.
SDK_DIR=""
if [ -f local.properties ]; then
    SDK_DIR=$(sed -n 's/^sdk\.dir[[:space:]]*=[[:space:]]*//p' local.properties | tr -d '\r' | head -1)
fi
[ -n "$SDK_DIR" ] || SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK_DIR" ] || fail "Android SDK не найден: нет sdk.dir в local.properties и нет ANDROID_SDK_ROOT/ANDROID_HOME"

APKSIGNER=$(find "$SDK_DIR/build-tools" -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -1)
[ -n "$APKSIGNER" ] || fail "apksigner не найден в $SDK_DIR/build-tools"

# Без этого уцелевший APK от прошлой сборки прошёл бы проверку вместо текущего.
rm -f "$APK"
./gradlew --no-daemon -q assembleRelease

if [ ! -f "$APK" ]; then
    ls app/build/outputs/apk/release/ || true
    fail "$APK отсутствует — release собрался без подписи"
fi

CERTS=$("$APKSIGNER" verify --print-certs "$APK") || fail "apksigner отверг $APK: битый или неподписанный"

if ! grep -qF "certificate SHA-256 digest: $EXPECTED_SHA256" <<<"$CERTS"; then
    echo "$CERTS"
    fail "подпись не та, ожидался отпечаток SHA-256: $EXPECTED_SHA256"
fi

echo "OK: $APK подписан ключом TD80, отпечаток совпал"
grep -F 'certificate DN:' <<<"$CERTS" || true
