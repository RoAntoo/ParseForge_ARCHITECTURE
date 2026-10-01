#ifndef AppVersion
  #error AppVersion must come from pom.xml
#endif
#ifndef ProjectRoot
  #error ProjectRoot must be an absolute checkout path
#endif

[Setup]
AppId={{FB01D31C-0D7A-4A0B-9765-063ED02299E4}
AppName=ParseForge
AppVersion={#AppVersion}
AppPublisher=ParseForge contributors
AppCopyright=Copyright ParseForge contributors
VersionInfoDescription=Local document conversion
DefaultDirName={localappdata}\Programs\ParseForge
DefaultGroupName=ParseForge
DisableProgramGroupPage=yes
DisableDirPage=no
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#ProjectRoot}\build\installer
OutputBaseFilename=ParseForge-Setup-{#AppVersion}
SetupIconFile={#ProjectRoot}\src\main\resources\icons\ParseForge.ico
UninstallDisplayIcon={app}\ParseForge.exe
LicenseFile={#ProjectRoot}\LICENSE
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupLogging=yes
CloseApplications=yes
RestartApplications=no
Uninstallable=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startmenuicon"; Description: "{cm:StartMenuShortcut}"; GroupDescription: "{cm:AdditionalIcons}"

[CustomMessages]
spanish.StartMenuShortcut=Agregar al menú Inicio
english.StartMenuShortcut=Add to Start menu

[Files]
Source: "{#ProjectRoot}\build\app-image\ParseForge\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\ParseForge"; Filename: "{app}\ParseForge.exe"; Tasks: startmenuicon
Name: "{autodesktop}\ParseForge"; Filename: "{app}\ParseForge.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\ParseForge.exe"; Description: "{cm:LaunchProgram,ParseForge}"; Flags: nowait postinstall skipifsilent

; No UninstallDelete entries: documents, engines, models and settings are preserved.
