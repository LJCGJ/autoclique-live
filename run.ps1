# =====================================================================
#  AutoClique Live - compila, instala no emulador e abre o app
#  Uso:  no VS Code aperte Ctrl+Shift+B
#        ou no terminal:  .\run.ps1
#  Opcoes:  .\run.ps1 -Bundle      (gera o .aab para enviar a Play Store)
#           .\run.ps1 -Logs        (abre o logcat depois de instalar)
#           .\run.ps1 -Clean       (limpa caches antes de compilar)
#           .\run.ps1 -Release     (instala a versao release assinada)
# =====================================================================

param(
    [switch]$Logs,
    [switch]$Clean,
    [switch]$Release,
    [switch]$Bundle,
    [string]$Avd = ""
)

# Comandos nativos com codigo de saida != 0 nao devem virar excecao:
# tratamos os erros na mao para dar mensagens em portugues.
$ErrorActionPreference = "Continue"
$PSNativeCommandUseErrorActionPreference = $false

$proj = $PSScriptRoot
Set-Location $proj

$PKG = "com.autoclique.live"
$ACTIVITY = "$PKG/$PKG.ui.MainActivity"

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m" -ForegroundColor Green }
function Fail($m) { Write-Host ""; Write-Host "ERRO: $m" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- SDK
Info "Procurando o Android SDK..."
$sdk = $null
foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
    if ($c -and (Test-Path (Join-Path $c "platform-tools\adb.exe"))) { $sdk = $c; break }
}
if (-not $sdk) {
    Fail (@(
        'Nao encontrei o Android SDK.',
        'Abra o Android Studio, menu Tools > SDK Manager, e veja o caminho em',
        '"Android SDK Location". Depois defina a variavel de ambiente ANDROID_HOME',
        'apontando para ele.'
    ) -join [Environment]::NewLine)
}
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulatorExe = Join-Path $sdk "emulator\emulator.exe"
Ok "SDK em $sdk"

# ------------------------------------------------------- local.properties
# O Gradle precisa deste arquivo para achar o SDK. Ele nao vai junto no zip
# porque o caminho muda de maquina para maquina.
$sdkEscaped = $sdk.Replace('\', '\\').Replace(':', '\:')
Set-Content -Path (Join-Path $proj "local.properties") -Value "sdk.dir=$sdkEscaped" -Encoding ascii
Ok "local.properties escrito"

# --------------------------------------------------------------- Java
# O Gradle 8.14 + AGP 8.7 sao homologados ate o JDK 21. Um JDK 22+ pode
# compilar, mas tambem pode estourar erro de versao no meio do build.
# Por isso preferimos o JBR que vem junto com o Android Studio.
function Get-JavaMajor($javaHome) {
    if (-not $javaHome) { return 0 }
    $exe = Join-Path $javaHome "bin\java.exe"
    if (-not (Test-Path $exe)) { return 0 }
    $out = (& $exe -version 2>&1 | Out-String)
    if ($out -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($out -match 'version "(\d+)')    { return [int]$Matches[1] }
    return 0
}

# Um JDK 22+ NAO serve: o Gradle 8.14 aborta com a mensagem enigmatica
# "* What went wrong:" seguida so do numero da versao (ex: "25").
$cacheJdk = Join-Path $proj ".jdk-path.txt"
$jdk = $null

if (Test-Path $cacheJdk) {
    $c = (Get-Content $cacheJdk -Raw -ErrorAction SilentlyContinue)
    if ($c) {
        $c = $c.Trim()
        $m = Get-JavaMajor $c
        if ($m -ge 17 -and $m -le 21) { $jdk = $c }
    }
}

if (-not $jdk) {
    Info "Procurando um JDK 17-21 na maquina..."
    $raizes = @(
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr"),
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr"),
        (Join-Path ${env:ProgramFiles(x86)} "Android\Android Studio\jbr"),
        "D:\Android\Android Studio\jbr",
        "D:\Program Files\Android\Android Studio\jbr"
    )
    # Pastas que costumam conter VARIAS instalacoes de JDK lado a lado.
    $pais = @(
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "JetBrains"),
        (Join-Path $env:ProgramFiles "Android"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium"),
        (Join-Path $env:USERPROFILE "scoop\apps"),
        (Join-Path $env:USERPROFILE ".jdks")
    )
    foreach ($p in $pais) {
        if ($p -and (Test-Path $p)) {
            foreach ($d in (Get-ChildItem $p -Directory -ErrorAction SilentlyContinue)) {
                $raizes += $d.FullName
                $raizes += (Join-Path $d.FullName "current")
                $raizes += (Join-Path $d.FullName "jbr")
            }
        }
    }
    $raizes += $env:JAVA_HOME

    foreach ($r in ($raizes | Where-Object { $_ } | Select-Object -Unique)) {
        $m = Get-JavaMajor $r
        if ($m -ge 17 -and $m -le 21) { $jdk = $r; Ok "JDK $m encontrado em $r"; break }
    }
}

if (-not $jdk) {
    Info "Nenhum JDK 17-21 na maquina. Baixando um JDK 21 so para este projeto (~190 MB, uma unica vez)..."
    $ProgressPreference = 'SilentlyContinue'
    $destino = Join-Path $proj ".jdk"
    $zip = Join-Path $env:TEMP "temurin-jdk21.zip"
    $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -ErrorAction Stop
        if (Test-Path $destino) { Remove-Item -Recurse -Force $destino -ErrorAction SilentlyContinue }
        Expand-Archive -Path $zip -DestinationPath $destino -Force
        Remove-Item -Force $zip -ErrorAction SilentlyContinue
        foreach ($sub in (Get-ChildItem $destino -Directory -ErrorAction SilentlyContinue)) {
            if (Test-Path (Join-Path $sub.FullName "bin\java.exe")) { $jdk = $sub.FullName; break }
        }
        if ($jdk) { Ok "JDK 21 instalado em $jdk" }
    } catch {
        Fail (@(
            'Nao consegui baixar o JDK 21 automaticamente.',
            'Instale o Android Studio (ja vem com um JDK 21 na pasta jbr) ou o',
            'Temurin 21, e rode este script de novo.'
        ) -join [Environment]::NewLine)
    }
}

if (-not $jdk) { Fail "Nao encontrei nem consegui instalar um JDK 17-21." }

$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk "bin") + ";" + $env:PATH
Set-Content -Path $cacheJdk -Value $jdk -Encoding ascii
Ok "Usando JAVA_HOME = $jdk"

# ------------------------------------------------- caches de outra maquina
# O zip do projeto foi gerado no Linux; esses caches tem caminhos invalidos aqui.
$marker = Join-Path $proj ".gradle\.limpo-no-windows"
if ($Clean -or -not (Test-Path $marker)) {
    Info "Limpando caches herdados de outra maquina..."
    foreach ($d in @(".gradle", ".kotlin", "build", "app\build")) {
        $full = Join-Path $proj $d
        if (Test-Path $full) { Remove-Item -Recurse -Force $full -ErrorAction SilentlyContinue }
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $proj ".gradle") | Out-Null
    Set-Content -Path $marker -Value "ok" -Encoding ascii
    Ok "Caches limpos"
}

# ------------------------------------------------ memoria antes de compilar
# Esta maquina tem 12 GB e roda Teams/navegador. Compilar com o emulador ja
# ligado (2 GB) somado a daemons velhos do Gradle estourava a memoria com
# "Failed to commit metaspace". Por isso COMPILAMOS ANTES de ligar o emulador.
function Get-FreeMB {
    try { [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024) }
    catch { 99999 }
}

function Stop-DaemonsJava {
    # 'gradlew --stop' apenas PEDE para o daemon sair; um daemon travado ignora
    # o pedido e continua segurando app\build\kotlin\...\caches-jvm. Como o
    # Kotlin agora compila in-process, quem segura esse cache e o proprio
    # daemon do Gradle. Aqui matamos o processo de verdade.
    #
    # Filtramos pela linha de comando para NAO derrubar outros javas da
    # maquina (o servidor de linguagem Java do VS Code, por exemplo).
    $mortos = 0
    try {
        Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match 'GradleDaemon|KotlinCompileDaemon' } |
            ForEach-Object {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                $mortos++
            }
    } catch { }
    if ($mortos -gt 0) { Info "Encerrados $mortos daemon(s) Java presos." }
    return $mortos
}

Stop-DaemonsJava | Out-Null

$livre = Get-FreeMB
if ($livre -lt 3500) {
    Info "Apenas $livre MB livres. Encerrando daemons antigos do Gradle..."
    & .\gradlew.bat --stop --console=plain 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    $livre = Get-FreeMB
}
Info "Memoria livre: $livre MB"

# --------------------------------------------------------------- compilar
# assembleXxx nao precisa de aparelho conectado: da para compilar com o
# emulador desligado e so depois ligar para instalar.
# -Bundle gera o .aab exigido pela Play Store (APK a loja nao aceita mais).
$task = "assembleDebug"
if ($Release) { $task = "assembleRelease" }
if ($Bundle)  { $task = "bundleRelease" }
$logBuild = Join-Path $proj "build-ultimo.log"

# No Windows, a extensao Java/Gradle do VS Code (e as vezes o antivirus) segura
# arquivos dentro de app\build. O Gradle entao falha com "Unable to delete
# directory". Encerrar os daemons e apagar o cache do Kotlin resolve.
function Reset-BuildTravado {
    & .\gradlew.bat --stop --console=plain 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    Stop-DaemonsJava | Out-Null
    Start-Sleep -Seconds 4

    # Apaga app\build inteiro: cache incremental pela metade e pior que
    # recompilar do zero, que aqui leva ~1 minuto.
    $full = Join-Path $proj "app\build"
    for ($i = 0; $i -lt 6 -and (Test-Path $full); $i++) {
        Remove-Item -Recurse -Force $full -ErrorAction SilentlyContinue
        if (Test-Path $full) { Start-Sleep -Seconds 2 }
    }
    if (-not (Test-Path $full)) { return $true }

    # Plano B: se nao da para APAGAR, tenta RENOMEAR. O Windows costuma
    # permitir renomear uma pasta mesmo com arquivos abertos dentro dela, e
    # para o Gradle o efeito e o mesmo: ele encontra um diretorio limpo.
    $velho = Join-Path $proj ("app\build.travado-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    try {
        Rename-Item -Path $full -NewName (Split-Path $velho -Leaf) -ErrorAction Stop
        Info "Nao deu para apagar, mas renomeei para $(Split-Path $velho -Leaf) - pode excluir depois."
        return $true
    } catch {
        return $false
    }
}

Info "Compilando ($task)..."
& .\gradlew.bat $task --console=plain 2>&1 | Tee-Object -FilePath $logBuild
$ok = ($LASTEXITCODE -eq 0)

if (-not $ok) {
    $texto = (Get-Content $logBuild -Raw -ErrorAction SilentlyContinue)

    if ($texto -match "Unable to delete directory|Failed to delete some children") {
        Info "Arquivos do build travados por outro processo. Liberando..."
        if (Reset-BuildTravado) {
            Info "Cache do Kotlin limpo. Compilando de novo..."
            & .\gradlew.bat $task --console=plain 2>&1 | Tee-Object -FilePath $logBuild
            $ok = ($LASTEXITCODE -eq 0)
        } else {
            # Nem matando os daemons deu. Mostra quem sobrou segurando arquivo,
            # para nao ficar no chute.
            Write-Host ""
            Write-Host "Processos java ainda vivos:" -ForegroundColor Yellow
            # Ifs independentes de proposito: o Windows PowerShell 5.1 recusa
            # um "if" de atribuicao com o elseif na linha seguinte. O PS7 aceita,
            # e por isso esse tipo de erro so aparece na maquina do usuario.
            $lista = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue)
            foreach ($p in $lista) {
                $c = [string]$p.CommandLine
                $tag = 'OUTRO'
                if ($c -match 'GradleDaemon') { $tag = 'GRADLE' }
                if ($c -match 'KotlinCompileDaemon') { $tag = 'KOTLIN' }
                if ($c -match 'gradle-server|vscode-gradle|gradle-language') { $tag = 'VSCODE-GRADLE' }
                if ($c -match 'jdt|lemminx|java-language|jdtls') { $tag = 'VSCODE-JAVA' }
                Write-Host ("  {0,-14} PID {1}" -f $tag, $p.ProcessId)
            }
            Fail (@(
                'Nao consegui apagar app\build mesmo apos encerrar os daemons.',
                'Alguem de fora esta com os arquivos abertos. Na ordem do mais provavel:',
                '  1) VS Code: Extensions > Gradle for Java > engrenagem > Disable (Workspace),',
                '     depois recarregue a janela;',
                '  2) antivirus: exclua a pasta do projeto da verificacao em tempo real;',
                '  3) se a lista acima mostra VSCODE, feche o VS Code e rode pelo PowerShell puro.'
            ) -join [Environment]::NewLine)
        }
    }
}

if (-not $ok) {
    Info "Tentando sem daemon, que usa bem menos memoria..."
    & .\gradlew.bat --stop --console=plain 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    & .\gradlew.bat $task --console=plain --no-daemon 2>&1 | Tee-Object -FilePath $logBuild
    $ok = ($LASTEXITCODE -eq 0)
}

if (-not $ok) { Fail "A compilacao falhou. O erro esta acima (log completo em build-ultimo.log)." }

# O AAB nao se instala num aparelho: e um formato de envio para a loja.
# Entao, com -Bundle, o trabalho termina aqui.
if ($Bundle) {
    $aabRel = "app\build\outputs\bundle\release\app-release.aab"
    $aab = Join-Path $proj $aabRel
    if (-not (Test-Path $aab)) { Fail "O build terminou mas nao achei o AAB em $aabRel" }
    $mb = [math]::Round((Get-Item $aab).Length / 1MB, 2)
    Ok "AAB gerado: $aabRel  ($mb MB)"
    Write-Host ""
    Write-Host "Envie este arquivo no Play Console, em Teste fechado > Criar versao." -ForegroundColor Cyan
    Write-Host "Lembre de subir o appVersionCode no gradle.properties antes do proximo envio." -ForegroundColor Yellow
    exit 0
}

$apkRel = "app\build\outputs\apk\debug\app-debug.apk"
if ($Release) { $apkRel = "app\build\outputs\apk\release\app-release.apk" }
$apk = Join-Path $proj $apkRel
if (-not (Test-Path $apk)) { Fail "O build terminou mas nao achei o APK em $apkRel" }
Ok "APK gerado: $apkRel"

# ----------------------------------------------------------- emulador
function Get-OnlineDevices { @((& $adb devices) | Select-String -Pattern "^\S+\s+device\s*$") }
function Get-ListedDevices { @((& $adb devices) | Select-String -Pattern "^(emulator-\d+|\S+device\S*)\s+\S+\s*$") }

function Restart-Adb {
    Info "Reiniciando o servidor do adb..."
    & $adb kill-server 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    & $adb start-server 2>&1 | Out-Null
    Start-Sleep -Seconds 3
}

Info "Procurando um emulador ligado..."
$connected = Get-OnlineDevices

# Aparece na lista mas nao esta 'device' (offline/unauthorized): normalmente e o
# servidor do adb travado ou de outra versao (Appium, scoop, SDK antigo).
if (-not $connected -and (Get-ListedDevices)) {
    Restart-Adb
    $connected = Get-OnlineDevices
}

if (-not $connected) {
    $avds = @(& $emulatorExe -list-avds 2>$null | Where-Object { $_ -and $_.Trim() -ne "" })
    if ($avds.Count -eq 0) {
        Fail (@(
            'Nenhum emulador ligado e nenhum AVD criado.',
            'Rode .\criar-emulador.ps1 para criar um sem Play Store, ou crie pelo',
            'Android Studio em Tools > Device Manager > Create Device (API 33+).'
        ) -join [Environment]::NewLine)
    }
    # Preferencia: o AVD pedido na linha de comando > o AVD de desenvolvimento
    # sem Play Store (criado por criar-emulador.ps1) > o primeiro da lista.
    $nomes = @($avds | ForEach-Object { $_.Trim() })
    $avd = $null
    if ($Avd -and ($nomes -contains $Avd)) { $avd = $Avd }
    elseif ($nomes -contains "autoclique_dev") { $avd = "autoclique_dev" }
    else { $avd = $nomes[0] }

    Info "Ligando o emulador '$avd' (pode levar 1-2 minutos na primeira vez)..."
    Start-Process -FilePath $emulatorExe -ArgumentList @("-avd", $avd) -WindowStyle Minimized
    $tries = 0
    $adbRestarts = 0
    while ($tries -lt 150) {
        $boot = (& $adb shell getprop sys.boot_completed 2>&1 | Out-String) -replace '\s', ''
        if ($boot -eq "1") { break }
        # Preso em 'offline' por muito tempo = servidor do adb ruim, nao boot lento.
        if ($tries -gt 0 -and $tries % 25 -eq 0 -and $adbRestarts -lt 2) {
            $adbRestarts++
            Restart-Adb
        }
        Start-Sleep -Seconds 2
        $tries++
    }
    if ($tries -ge 150) {
        Write-Host ""
        & $adb devices
        Fail (@(
            'O emulador nao terminou de iniciar (ficou em offline).',
            'Tente, nesta ordem:',
            '  1) Feche TODAS as janelas de emulador abertas e rode de novo.',
            '  2) Android Studio: Tools > Device Manager > seta do AVD > Cold Boot Now.',
            '  3) Se persistir, o AVD pode estar corrompido: Device Manager > Wipe Data.',
            '  4) Veja se ha outro adb concorrendo:  Get-Command adb -All'
        ) -join [Environment]::NewLine)
    }
}
Ok "Emulador pronto"

# Num EMULADOR (nunca num aparelho real) desliga o verificador de pacotes.
# Sem isso, imagens que trazem a Play Store bloqueiam o app por causa do
# AccessibilityService. Em imagem sem Play Store isso ja e no-op.
$primeiro = @(Get-OnlineDevices)[0]
$serial = if ($primeiro) { ($primeiro.ToString().Trim() -split '\s+')[0] } else { "" }
if ($serial -like "emulator-*") {
    & $adb -s $serial shell settings put global verifier_verify_adb_installs 0 2>&1 | Out-Null
    & $adb -s $serial shell settings put global package_verifier_enable 0 2>&1 | Out-Null
    Ok "Verificacao de instalacao desligada (somente neste emulador)"

    # Por padrao o Android esconde o teclado da tela quando existe teclado
    # fisico (hw.keyboard=yes no AVD). Com isto ligado valem os dois: digitar
    # pelo teclado do PC e tambem clicar nas teclas da tela.
    & $adb -s $serial shell settings put secure show_ime_with_hard_keyboard 1 2>&1 | Out-Null
    Ok "Teclado da tela liberado junto com o teclado fisico"
}

# -------------------------------------------------------------- instalar
# Instalamos com o adb, e nao com installDebug do Gradle: assim nao subimos
# outro daemon do Gradle com o emulador ja ocupando memoria.
Info "Instalando no emulador..."
$saida = (& $adb -s $serial install -r "$apk" 2>&1 | Out-String)

if ($saida -notmatch "Success" -and
    $saida -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|INSTALL_FAILED_VERSION_DOWNGRADE") {
    # Acontece ao trocar entre debug/release ou depois de regerar a chave.
    Info "Assinatura diferente da versao instalada. Removendo e reinstalando..."
    & $adb -s $serial uninstall $PKG 2>&1 | Out-Null
    $saida = (& $adb -s $serial install -r "$apk" 2>&1 | Out-String)
}

if ($saida -notmatch "Success") { Fail "Falha ao instalar:`n$saida" }
Ok "Instalado no emulador"

# ------------------------------------------------------------- abrir app
Info "Abrindo o app..."
& $adb -s $serial shell am start -n $ACTIVITY 2>&1 | Out-Null
Ok "Pronto"

Write-Host ""
Write-Host "Lembrete: no emulador o app so consegue tocar na tela depois de voce ativar" -ForegroundColor Yellow
Write-Host "Configuracoes > Acessibilidade > AutoClique Live, e liberar 'sobrepor outros apps'." -ForegroundColor Yellow

if ($Logs) {
    Write-Host ""
    Info "Logs do app (Ctrl+C para sair)"
    & $adb logcat -c
    & $adb logcat AutoClicker:V ScreenCapture:V ClickA11y:V AndroidRuntime:E "*:S"
}
