# =====================================================================
#  Captura telas do emulador/aparelho ja no formato aceito pela Play.
#
#  Uso:  .\capturar-telas.ps1
#        Navegue no app ate a tela desejada e pressione Enter.
#        Digite S e Enter para encerrar.
#
#  Por que nao basta o screencap puro: a Play exige que "a dimensao maxima
#  nao seja mais que o dobro da minima". Um Pixel 6 (1080x2400) da 2,22:1 e
#  seria RECUSADO. Este script alarga a imagem com o roxo do app ate a
#  proporcao ficar dentro do limite, sem cortar nada do conteudo.
# =====================================================================

param(
    [string]$Pasta = "loja",
    [string]$Prefixo = "tela"
)

$ErrorActionPreference = "Continue"
$PSNativeCommandUseErrorActionPreference = $false

Add-Type -AssemblyName System.Drawing

$proj = $PSScriptRoot
Set-Location $proj

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m" -ForegroundColor Green }
function Fail($m) { Write-Host ""; Write-Host "ERRO: $m" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- adb
$sdk = $null
foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"), "D:\Android\Sdk")) {
    if ($c -and (Test-Path (Join-Path $c "platform-tools\adb.exe"))) { $sdk = $c; break }
}
if (-not $sdk) { Fail "Nao encontrei o Android SDK." }
$adb = Join-Path $sdk "platform-tools\adb.exe"

function Aparelho {
    $l = (& $adb devices) | Select-String -Pattern "^\S+\s+device\s*$" | Select-Object -First 1
    if ($l) { return ($l.ToString().Trim() -split '\s+')[0] }
    return ""
}

$serial = Aparelho
if (-not $serial) {
    Info "Nenhum aparelho. Reiniciando o servidor do adb..."
    & $adb kill-server 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    & $adb start-server 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    $serial = Aparelho
}
if (-not $serial) {
    Fail "Nenhum emulador ou celular conectado. Abra o emulador (.\run.ps1) e rode de novo."
}
Ok "Aparelho: $serial"

$destinoPasta = Join-Path $proj $Pasta
New-Item -ItemType Directory -Force -Path $destinoPasta | Out-Null

# ------------------------------------------------------ ajuste de formato
# Alarga com o roxo do app ate caber em no maximo 1,95:1 (margem de folga
# sobre o limite de 2:1 da Play) e grava em PNG 24 bits, sem canal alfa.
function Ajustar($origem, $destino) {
    $img = [System.Drawing.Image]::FromFile($origem)
    try {
        $w = $img.Width
        $h = $img.Height
        $novoW = $w
        $novoH = $h

        $menor = [Math]::Min($w, $h)
        $maior = [Math]::Max($w, $h)
        if ($maior -gt (1.95 * $menor)) {
            if ($h -ge $w) { $novoW = [int][Math]::Ceiling($h / 1.95) }
            else           { $novoH = [int][Math]::Ceiling($w / 1.95) }
        }

        $bmp = New-Object System.Drawing.Bitmap($novoW, $novoH, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        try {
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            try {
                $g.Clear([System.Drawing.ColorTranslator]::FromHtml("#2A165C"))
                $g.DrawImage($img, [int](($novoW - $w) / 2), [int](($novoH - $h) / 2), $w, $h)
            } finally { $g.Dispose() }
            $bmp.Save($destino, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $bmp.Dispose() }

        return @{ De = "$w x $h"; Para = "$novoW x $novoH"; Mudou = ($novoW -ne $w -or $novoH -ne $h) }
    } finally { $img.Dispose() }
}

# ------------------------------------------------------------------ laco
Write-Host ""
Write-Host "Navegue no app ate a tela que quer fotografar e pressione Enter." -ForegroundColor Yellow
Write-Host "Digite S e Enter quando terminar." -ForegroundColor Yellow
Write-Host ""

$n = 0
while ($true) {
    $r = Read-Host "Enter para capturar (S para sair)"
    if ($r -match '^[sSqQ]') { break }

    $n++
    $tmp = Join-Path $env:TEMP "captura_$n.png"
    $destino = Join-Path $destinoPasta ("{0}-{1}.png" -f $Prefixo, $n)

    & $adb -s $serial shell screencap -p /sdcard/_cap.png 2>&1 | Out-Null
    & $adb -s $serial pull /sdcard/_cap.png "$tmp" 2>&1 | Out-Null
    & $adb -s $serial shell rm /sdcard/_cap.png 2>&1 | Out-Null

    if (-not (Test-Path $tmp)) {
        Write-Host "Falhou ao capturar. Tente de novo." -ForegroundColor Red
        $n--
        continue
    }

    $info = Ajustar $tmp $destino
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue

    if ($info.Mudou) {
        Ok ("{0}  ({1} -> {2}, alargada para caber na regra da Play)" -f (Split-Path $destino -Leaf), $info.De, $info.Para)
    } else {
        Ok ("{0}  ({1})" -f (Split-Path $destino -Leaf), $info.De)
    }
}

Write-Host ""
if ($n -ge 2) {
    Ok "$n captura(s) em $Pasta\ . A Play exige no minimo 2 e aceita ate 8."
} elseif ($n -eq 1) {
    Write-Host "AVISO: so 1 captura. A Play exige no minimo 2." -ForegroundColor Yellow
} else {
    Write-Host "Nenhuma captura feita." -ForegroundColor Yellow
}
