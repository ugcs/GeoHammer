#!/bin/sh
# Removes the .geohammer file association created by install-file-associations.sh.
set -e

DATA_DIR="${XDG_DATA_HOME:-$HOME/.local/share}"

rm -f "$DATA_DIR/mime/packages/geohammer.xml"
rm -f "$DATA_DIR/applications/geohammer.desktop"
rm -f "$DATA_DIR/icons/hicolor/32x32/apps/geohammer.png"

if command -v update-mime-database > /dev/null 2>&1; then
    update-mime-database "$DATA_DIR/mime"
fi
if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DATA_DIR/applications"
fi

echo "GeoHammer file association is removed"
