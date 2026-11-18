# Runs Gradle for this project from Windows PowerShell.
# The sources sit in OneDrive\文件, which breaks two things: the Java launcher cannot read the
# Chinese path, and OneDrive's sync locks Gradle's lock files. So the folder is mapped to a drive
# letter, and Gradle's project cache and build output (see build.gradle) live under ~/.gradle.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks = @("build"))
$ErrorActionPreference = "Continue"
$drive = "M:"
if (-not (Test-Path "$drive\gradlew.bat")) { subst $drive $PSScriptRoot }
# Minecraft 26.x needs Java 25.
if (-not ($env:JAVA_HOME -match "jdk-?2[5-9]")) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-25" }
Push-Location "$drive\"
try {
    & "$drive\gradlew.bat" --project-cache-dir "$HOME\.gradle\mistinn-project-cache" @Tasks
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
