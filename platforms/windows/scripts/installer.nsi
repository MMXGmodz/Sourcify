Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
Name "Sourcify Player"
OutFile "..\dist\Sourcify-Player-Windows-${APP_VERSION}-x64-Setup.exe"
InstallDir "$LOCALAPPDATA\Programs\Sourcify Player"
InstallDirRegKey HKCU "Software\Sourcify Player" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
Icon "..\assets\icon.ico"
UninstallIcon "..\assets\icon.ico"
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey /LANG=1031 "ProductName" "Sourcify Player"
VIAddVersionKey /LANG=1031 "CompanyName" "Sourcify"
VIAddVersionKey /LANG=1031 "FileDescription" "Sourcify Player Setup"
VIAddVersionKey /LANG=1031 "FileVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=1031 "LegalCopyright" "Copyright 2026 Sourcify contributors"
!define MUI_ABORTWARNING
!define MUI_ICON "..\assets\icon.ico"
!define MUI_UNICON "..\assets\icon.ico"
!define MUI_WELCOMEPAGE_TEXT "Sourcify Player für Windows 10/11 (64 Bit).$\r$\n$\r$\nBitte schließe eine bereits laufende Ausgabe vor der Installation. Favoriten und Musikdateien bleiben erhalten."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\Sourcify Player.exe"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "German"
Function .onInit
 ${IfNot} ${RunningX64}
  MessageBox MB_ICONSTOP "Sourcify Player benötigt Windows mit 64 Bit."
  Abort
 ${EndIf}
 SetRegView 64
FunctionEnd
Section "Sourcify Player"
 SetOutPath "$INSTDIR"
 File /r "..\dist\win-unpacked\*.*"
 WriteUninstaller "$INSTDIR\Uninstall.exe"
 CreateDirectory "$SMPROGRAMS\Sourcify Player"
 CreateShortcut "$SMPROGRAMS\Sourcify Player\Sourcify Player.lnk" "$INSTDIR\Sourcify Player.exe"
 CreateShortcut "$DESKTOP\Sourcify Player.lnk" "$INSTDIR\Sourcify Player.exe"
 WriteRegStr HKCU "Software\Sourcify Player" "InstallDir" "$INSTDIR"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "DisplayName" "Sourcify Player"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "DisplayVersion" "${APP_VERSION}"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "Publisher" "Sourcify"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "DisplayIcon" "$INSTDIR\Sourcify Player.exe"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "NoModify" 1
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player" "NoRepair" 1
SectionEnd
Section "Uninstall"
 SetRegView 64
 DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "Sourcify Player"
 Delete "$DESKTOP\Sourcify Player.lnk"
 Delete "$SMPROGRAMS\Sourcify Player\Sourcify Player.lnk"
 RMDir "$SMPROGRAMS\Sourcify Player"
 !include "uninstall-files.nsh"
 Delete "$INSTDIR\Uninstall.exe"
 RMDir "$INSTDIR"
 DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Sourcify Player"
 DeleteRegKey HKCU "Software\Sourcify Player"
SectionEnd
