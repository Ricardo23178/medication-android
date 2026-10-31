param(
    [ValidateSet("build", "test", "check", "install", "devices", "studio")]
    [string]$Task = "build"
)
$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot
$toolsDir = Join-Path (Split-Path $projectDir -Parent) "tools"
# 只影响当前终端/子进程，不改系统环境变量。
$env:JAVA_HOME = "D:\android-studio\jbr"
$env:ANDROID_HOME = "D:\Android"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $toolsDir "gradle-home"
$env:ANDROID_USER_HOME = Join-Path $toolsDir "android-user"
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME "avd"
$env:TEMP = Join-Path $toolsDir "android-tmp"
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$($env:TEMP)"
foreach ($path in @($env:GRADLE_USER_HOME, $env:ANDROID_USER_HOME, $env:TEMP)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
if (-not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw "找不到Android Studio的JDK，请修改dev.ps1里的JAVA_HOME。"
}
Push-Location $projectDir
try {
    if ($Task -eq "studio") {
        $env:STUDIO_PROPERTIES = Join-Path $projectDir "studio.properties"
        Start-Process -FilePath "D:\android-studio\bin\studio64.exe" -ArgumentList ('"' + $projectDir + '"')
        return
    } elseif ($Task -eq "devices") {
        & "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
    } else {
        $gradleTasks = @(switch ($Task) {
            "build" { @("assembleDebug") }
            "test" { @("testDebugUnitTest") }
            "check" { @("testDebugUnitTest", "lintDebug", "assembleDebug") }
            "install" { @("installDebug") }
        }
        )
        $preparedGradle = Join-Path $toolsDir "gradle-9.5.0-local\bin\gradle.bat"
        if (Test-Path -LiteralPath $preparedGradle) {
            & $preparedGradle @gradleTasks --console=plain
        } else {
            & .\gradlew.bat @gradleTasks --console=plain
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "命令执行失败，退出码 $LASTEXITCODE" }
} finally {
    Pop-Location
}
