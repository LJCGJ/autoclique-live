# =====================================================================
#  Libera as permissoes do AutoClique Live no EMULADOR, via adb.
#
#  Faz por comando o que voce faria na mao em Configuracoes:
#    - liga o servico de acessibilidade
#    - libera "sobrepor outros apps"
#    - concede a permissao de notificacoes
#
#  So roda em emulador. Num aparelho real o Android bloqueia a escrita
#  dessas configuracoes, e ai o caminho e pela interface mesmo.
# =====================================================================

$ErrorActionPreference = "Continue"
$PSNativeCommandUseErrorActionPreference = $false

$PKG = "com.autoclique.live"
$SVC = "$PKG/$PKG.service.ClickAccessibilityService"

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m" -ForegroundColor Green }
function Fail($m) { Write-Host ""; Write-Host "ERRO: $m" -ForegroundColor Red; exit 1 }

$sdk = $null
foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"), "D:\Android\Sdk")) {
    if ($c -and (Test-Path (Join-Path $c "platform-tools\adb.exe"))) { $sdk = $c; break }
}
if (-not $sdk) { Fail "Nao encontrei o Android SDK." }
$adb = Join-Path $sdk "platform-tools\adb.exe"

$linha = (& $adb devices) | Select-String -Pattern "^\S+\s+device\s*$" | Select-Object -First 1
if (-not $linha) { Fail "Nenhum aparelho conectado. Rode .\run.ps1 primeiro." }
$serial = ($linha.ToString().Trim() -split '\s+')[0]

if ($serial -notlike "emulator-*") {
    Fail "'$serial' nao e um emulador. Num celular real, ative na mao em Configuracoes > Acessibilidade."
}
Ok "Emulador: $serial"

$instalado = (& $adb -s $serial shell pm list packages $PKG | Out-String)
if ($instalado -notmatch [regex]::Escape($PKG)) { Fail "O app nao esta instalado. Rode .\run.ps1 primeiro." }

Info "Ligando o servico de acessibilidade..."
& $adb -s $serial shell settings put secure enabled_accessibility_services $SVC 2>&1 | Out-Null
& $adb -s $serial shell settings put secure accessibility_enabled 1 2>&1 | Out-Null

Info "Liberando 'sobrepor outros apps'..."
& $adb -s $serial shell appops set $PKG SYSTEM_ALERT_WINDOW allow 2>&1 | Out-Null

Info "Concedendo notificacoes..."
& $adb -s $serial shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>&1 | Out-Null

# --------------------------------------------------------- conferencia
Write-Host ""
Info "Conferindo..."
$a11y = (& $adb -s $serial shell settings get secure enabled_accessibility_services | Out-String).Trim()
$on   = (& $adb -s $serial shell settings get secure accessibility_enabled | Out-String).Trim()
$ops  = (& $adb -s $serial shell appops get $PKG SYSTEM_ALERT_WINDOW | Out-String).Trim()

if ($a11y -match [regex]::Escape($PKG) -and $on -eq "1") {
    Ok "Acessibilidade ligada"
} else {
    Write-Host "FALHOU: acessibilidade nao ficou ligada (valor: '$a11y', enabled: '$on')" -ForegroundColor Red
}
if ($ops -match "allow") { Ok "Sobreposicao liberada" } else { Write-Host "FALHOU: sobreposicao -> $ops" -ForegroundColor Red }

# Reabre o app para ele reler o estado das permissoes
& $adb -s $serial shell am force-stop $PKG 2>&1 | Out-Null
& $adb -s $serial shell am start -n "$PKG/$PKG.ui.MainActivity" 2>&1 | Out-Null

Write-Host ""
Write-Host "Pronto. O app ja pode tocar na tela." -ForegroundColor Green
