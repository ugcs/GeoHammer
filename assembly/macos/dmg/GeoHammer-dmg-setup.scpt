tell application "Finder"
  set theDisk to a reference to (disks whose URL = "DEPLOY_VOLUME_URL")
  open theDisk

  set theWindow to a reference to (container window of disks whose URL = "DEPLOY_VOLUME_URL")

  set current view of theWindow to icon view
  set toolbar visible of theWindow to false
  set statusbar visible of theWindow to false
  set pathbar visible of theWindow to false

  -- bounds include the title bar: content area fits the 520x340 background
  set the bounds of theWindow to {400, 100, 920, 468}

  set theViewOptions to a reference to the icon view options of theWindow
  set arrangement of theViewOptions to not arranged
  set icon size of theViewOptions to 128
  set background picture of theViewOptions to POSIX file "DEPLOY_BG_FILE"

  -- Create alias for install location
  make new alias file at POSIX file "DEPLOY_VOLUME_PATH" to POSIX file "DEPLOY_INSTALL_LOCATION" with properties {name:"DEPLOY_INSTALL_LOCATION_DISPLAY_NAME"}

  -- jpackage copies .VolumeIcon.icns after this script: position a placeholder, Finder keeps it by file name
  do shell script "touch " & quoted form of ("DEPLOY_VOLUME_PATH" & "/.VolumeIcon.icns")
  set position of ((POSIX file ("DEPLOY_VOLUME_PATH" & "/.VolumeIcon.icns")) as alias) to {1000, 1000}

  set allTheFiles to the name of every item of theWindow
  repeat with theFile in allTheFiles
    set theFilePath to POSIX path of theFile
    set appFilePath to POSIX path of "/DEPLOY_TARGET"
    if theFilePath ends with "DEPLOY_INSTALL_LOCATION_DISPLAY_NAME" then
      set position of item theFile of theWindow to {390, 130}
    else if theFilePath ends with appFilePath then
      set position of item theFile of theWindow to {120, 130}
    else
      -- Service files (.background, .DS_Store, ...) are visible when Finder shows hidden files: move them out of view
      set position of item theFile of theWindow to {1000, 1000}
    end if
  end repeat

  update theDisk without registering applications
  delay 5
  close (get window of theDisk)
end tell
