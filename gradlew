#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION=9.3.1
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
WRAPPER_ROOT="${APP_HOME}/.gradle-wrapper"
INSTALL_DIR="${WRAPPER_ROOT}/gradle-${GRADLE_VERSION}"
ZIP_PATH="${WRAPPER_ROOT}/${DIST_NAME}"
GRADLE_BIN="${INSTALL_DIR}/bin/gradle"

mkdir -p "${WRAPPER_ROOT}"

if [ ! -x "${GRADLE_BIN}" ]; then
  if [ ! -f "${ZIP_PATH}" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "${DIST_URL}" -o "${ZIP_PATH}"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "${DIST_URL}" -O "${ZIP_PATH}"
    else
      echo "Neither curl nor wget is available to download Gradle." >&2
      exit 1
    fi
  fi

  rm -rf "${INSTALL_DIR}"
  unzip -q -o "${ZIP_PATH}" -d "${WRAPPER_ROOT}"
fi

exec "${GRADLE_BIN}" -p "${APP_HOME}" "$@"

