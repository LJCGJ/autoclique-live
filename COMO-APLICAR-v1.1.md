# AutoClique Live — como aplicar a v1.1

Dois jeitos. Use o **A** se o seu repositório local está limpo (sem alterações
pendentes); se der conflito, use o **B**.

## A) Patch git (recomendado)

No PowerShell, dentro de `D:\Projetos\autoclique-live`:

    git status                      # confira que nao ha alteracoes pendentes
    git checkout -b v1.1
    git apply --3way autoclique-v1.1.patch
    git add -A
    git commit -m "v1.1"

## B) Copiar os arquivos (fallback)

Extraia `autoclique-v1.1-arquivos.zip` **por cima** da pasta do projeto,
substituindo os arquivos existentes. Ele contém só os 19 arquivos alterados.

## Gerar o .aab e publicar

    .\run.ps1                       # ou: .\gradlew bundleRelease

O bundle sai em `app\build\outputs\bundle\release\app-release.aab`
(versionCode 2, versionName 1.1, targetSdk 36).

Depois: Play Console → Produção → Criar nova versão → enviar o .aab →
notas da versão → revisão. Eu conduzo essa parte com você.

## O que mudou

- API 36 (compileSdk/targetSdk) e AGP 8.9.3 — exigência do Google para
  qualquer atualização a partir de 31/08/2026.
- Botão "Iniciar" e "Aceitar/Recusar" não ficam mais atrás da barra de
  navegação (tratamento de WindowInsets / edge-to-edge).
- Títulos e botões "Novo / Renomear / Excluir" quebram linha com fonte grande.
- Ao tocar em "Conceder" na Acessibilidade, um diálogo explica o passo a
  passo e deixa claro que não é o TalkBack.
- Inglês como idioma padrão + português (segue o idioma do aparelho).
  Todos os textos fixos do código foram movidos para strings.xml.
- README: linha com a senha do keystore removida.

## Segurança do keystore (recomendado)

A senha antiga estava pública no README do GitHub. Para trocar:

    keytool -storepasswd -keystore autoclique.jks
    keytool -keypasswd  -keystore autoclique.jks -alias autoclique

e atualize `keystore.properties` (storePassword / keyPassword).
Depois faça backup do `autoclique.jks` em local seguro.
