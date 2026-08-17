# =====================================================================
#  Cria um emulador SEM Google Play Store para desenvolver o AutoClique.
#
#  Por que:  o Play Protect bloqueia por padrao qualquer app instalado
#            fora da loja que declare um AccessibilityService. Imagens
#            sem Play Store nao tem esse verificador, entao o app instala
#            direto - sem precisar desligar protecao de nada.
#
#  Uso:  .\criar-emulador.ps1
#        .\criar-emulador.ps1 -Nome meu_avd -Api 34
# =====================================================================

param(
    [string]$Nome = "autoclique_dev",
    [int]$Api = 0,
    [switch]$Recriar
)

$ErrorActionPreference = "Continue"
$PSNativeCommandUseErrorActionPreference = $false

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m" -ForegroundColor Green }
function Fail($m) { Write-Host ""; Write-Host "ERRO: $m" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- SDK
$sdk = $null
foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"), "D:\Android\Sdk")) {
    if ($c -and (Test-Path (Join-Path $c "platform-tools\adb.exe"))) { $sdk = $c; break }
}
if (-not $sdk) { Fail "Nao encontrei o Android SDK. Defina ANDROID_HOME." }
Ok "SDK em $sdk"

# ------------------------------------------------- command-line tools
# sdkmanager/avdmanager podem estar em cmdline-tools\<qualquer versao>\bin
# ou no antigo tools\bin. Se nao existirem, baixamos na hora.
function Find-SdkTools($sdkPath) {
    $dirs = @()
    $ct = Join-Path $sdkPath "cmdline-tools"
    if (Test-Path $ct) { $dirs += (Get-ChildItem $ct -Directory -ErrorAction SilentlyContinue).FullName }
    $dirs += (Join-Path $sdkPath "tools")
    foreach ($d in $dirs) {
        $s = Join-Path $d "bin\sdkmanager.bat"
        $a = Join-Path $d "bin\avdmanager.bat"
        if ((Test-Path $s) -and (Test-Path $a)) {
            return [PSCustomObject]@{ Sdkmanager = $s; Avdmanager = $a }
        }
    }
    return $null
}

$tools = Find-SdkTools $sdk

if (-not $tools) {
    Info "Command-line Tools nao instaladas. Baixando (~150 MB)..."
    $ProgressPreference = 'SilentlyContinue'   # sem isso o download fica lentissimo
    $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    $zip = Join-Path $env:TEMP "android-cmdline-tools.zip"
    $tmp = Join-Path $env:TEMP "android-cmdline-tools-extract"

    $baixou = $true
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -ErrorAction Stop
    } catch {
        $baixou = $false
    }

    if ($baixou) {
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
        $destino = Join-Path $sdk "cmdline-tools\latest"
        New-Item -ItemType Directory -Force -Path (Join-Path $sdk "cmdline-tools") | Out-Null
        if (Test-Path $destino) { Remove-Item -Recurse -Force $destino -ErrorAction SilentlyContinue }
        Move-Item -Path (Join-Path $tmp "cmdline-tools") -Destination $destino -Force
        Remove-Item -Force $zip -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
        $tools = Find-SdkTools $sdk
        if ($tools) { Ok "Command-line Tools instaladas em $destino" }
    }
}

if (-not $tools) {
    Fail @"
Nao consegui obter o 'sdkmanager' automaticamente.
Instale pela interface: Android Studio > Tools > SDK Manager >
aba 'SDK Tools' > marque 'Android SDK Command-line Tools (latest)' > Apply.
Depois rode este script de novo.
"@
}

$sdkmanager = $tools.Sdkmanager
$avdmanager = $tools.Avdmanager

# JDK (o sdkmanager precisa de Java)
if (-not ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe")))) {
    foreach ($c in @(
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr"),
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr")
    )) {
        if ($c -and (Test-Path (Join-Path $c "bin\java.exe"))) { $env:JAVA_HOME = $c; break }
    }
}

$emulatorExe = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

# --------------------------------------------------- AVD ja existe?
$existentes = @(& $emulatorExe -list-avds 2>$null | Where-Object { $_ -and $_.Trim() -ne "" } | ForEach-Object { $_.Trim() })
if ($existentes -contains $Nome) {
    if ($Recriar) {
        Info "Apagando o AVD '$Nome' para recriar..."
        & $avdmanager delete avd -n $Nome 2>&1 | Out-Null
    } else {
        Ok "O AVD '$Nome' ja existe. Use -Recriar para refazer do zero."
        Write-Host ""
        Write-Host "Para usar:  .\run.ps1" -ForegroundColor Cyan
        exit 0
    }
}

# ------------------------------------------- escolher a imagem certa
Info "Consultando as imagens disponiveis (pode levar 1 minuto)..."
$lista = (& $sdkmanager --sdk_root="$sdk" --list 2>&1 | Out-String)

# Ordem de preferencia. NENHUMA delas e 'google_apis_playstore' - e justamente
# a Play Store que traz o Play Protect.
$candidatos = @()
if ($Api -gt 0) {
    $candidatos += "system-images;android-$Api;google_apis;x86_64"
    $candidatos += "system-images;android-$Api;default;x86_64"
}
$candidatos += @(
    "system-images;android-35;google_apis;x86_64",
    "system-images;android-34;google_apis;x86_64",
    "system-images;android-33;google_apis;x86_64",
    "system-images;android-35;default;x86_64",
    "system-images;android-34;default;x86_64"
)

$imagem = $null
foreach ($c in $candidatos) {
    if ($lista -match [regex]::Escape($c)) { $imagem = $c; break }
}
if (-not $imagem) { Fail "Nenhuma imagem de sistema compativel encontrada no repositorio do SDK." }
Ok "Imagem escolhida: $imagem"

# ------------------------------------------------------ baixar imagem
Info "Baixando/instalando a imagem (so na primeira vez, ~1.5 GB)..."
# Aceita as licencas pendentes sem travar esperando input.
$sim = ("y`n" * 30)
$sim | & $sdkmanager --sdk_root="$sdk" --licenses 2>&1 | Out-Null
& $sdkmanager --sdk_root="$sdk" "$imagem" "platform-tools" "emulator"
if ($LASTEXITCODE -ne 0) { Fail "Falha ao baixar a imagem do sistema." }
Ok "Imagem instalada"

# ---------------------------------------------------------- criar AVD
Info "Criando o AVD '$Nome'..."
$perfil = "pixel_6"
$saida = ("no`n" | & $avdmanager create avd -n $Nome -k "$imagem" -d $perfil --force 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    # Alguns SDKs nao tem o perfil pixel_6; tenta sem perfil.
    $saida = ("no`n" | & $avdmanager create avd -n $Nome -k "$imagem" --force 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail "Falha ao criar o AVD:`n$saida" }
}
Ok "AVD criado"

# --------------------------------------------- ajustes de desempenho
# A pasta dos AVDs pode estar fora do perfil do usuario (ANDROID_AVD_HOME).
$avdHome = $env:ANDROID_AVD_HOME
if (-not $avdHome) { $avdHome = Join-Path $env:USERPROFILE ".android\avd" }
$cfg = Join-Path $avdHome "$Nome.avd\config.ini"
if (Test-Path $cfg) {
    # 2 GB e o equilibrio para uma maquina de 12 GB que tambem roda
    # Teams/navegador. Subir mais faz o Windows comecar a paginar.
    $props = @{
        "hw.ramSize"               = "2048M"
        "vm.heapSize"              = "256M"
        "hw.keyboard"              = "yes"
        "disk.dataPartition.size"  = "6G"
        "hw.lcd.density"           = "420"
        "showDeviceFrame"          = "no"
    }
    $linhas = Get-Content $cfg
    foreach ($k in $props.Keys) {
        $linhas = $linhas | Where-Object { $_ -notmatch "^\s*$([regex]::Escape($k))\s*=" }
        $linhas += "$k=$($props[$k])"
    }
    Set-Content -Path $cfg -Value $linhas -Encoding ascii
    Ok "Desempenho ajustado (3 GB de RAM, 6 GB de disco)"
}

Write-Host ""
Write-Host "Pronto. Esse emulador nao tem Play Store, entao nao tem Play Protect:" -ForegroundColor Green
Write-Host "o AutoClique instala direto, sem o aviso de app bloqueado." -ForegroundColor Green
Write-Host ""
Write-Host "Agora e so rodar:  .\run.ps1" -ForegroundColor Cyan
Write-Host "(o run.ps1 ja da preferencia para o AVD '$Nome')" -ForegroundColor DarkGray
