#!/bin/sh
# Registers GeoHammer as the application for .geohammer files
# for the current user. Run it once after unpacking the archive.
set -e

INSTALL_DIR=$(cd "$(dirname "$0")" && pwd)
DATA_DIR="${XDG_DATA_HOME:-$HOME/.local/share}"

mkdir -p "$DATA_DIR/mime/packages" "$DATA_DIR/applications" "$DATA_DIR/icons/hicolor/32x32/apps"

cp "$INSTALL_DIR/geohammer-mime.xml" "$DATA_DIR/mime/packages/geohammer.xml"
cp "$INSTALL_DIR/logo32.png" "$DATA_DIR/icons/hicolor/32x32/apps/geohammer.png"

sed "s|@INSTALL_DIR@|$INSTALL_DIR|g" "$INSTALL_DIR/geohammer.desktop" > "$DATA_DIR/applications/geohammer.desktop"
chmod 644 "$DATA_DIR/applications/geohammer.desktop"

if command -v update-mime-database > /dev/null 2>&1; then
    update-mime-database "$DATA_DIR/mime"
fi
if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DATA_DIR/applications"
fi
if command -v gtk-update-icon-cache > /dev/null 2>&1; then
    gtk-update-icon-cache -f -t "$DATA_DIR/icons/hicolor" > /dev/null 2>&1 || true
fi
if command -v xdg-mime > /dev/null 2>&1; then
    xdg-mime default geohammer.desktop application/x-geohammer
fi

echo "GeoHammer is registered for .geohammer files"
