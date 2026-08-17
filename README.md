# AutoClique Live

Clicador automático para Android. Você marca pontos na tela arrastando uma mira, define o intervalo de cada um, e o app toca sozinho nesses pontos — inclusive por cima de outros apps. Feito para resgatar recompensas de live sem ficar de olho no botão.

O diferencial: cada ponto pode ter um **gatilho por cor**. Em vez de tocar no vazio a cada X segundos, o app confere a cor do pixel naquele ponto e só toca quando o botão de resgatar realmente aparece.

---

## Instalação rápida (APK pronto)

1. Copie `AutoCliqueLive.apk` para o celular.
2. Abra o arquivo. O Android vai pedir para permitir a instalação de apps de fontes desconhecidas — autorize para o gerenciador de arquivos que você usou.
3. Instale e abra.

O APK é assinado com uma chave própria (`autoclique.jks`, senha `autoclique`). Guarde esse arquivo: sem ele você não consegue publicar atualizações por cima desta instalação.

**Requisitos:** Android 8.0 (API 26) ou superior.

---

## Primeira configuração

Na tela inicial há quatro permissões. As duas primeiras são obrigatórias.

| Permissão | Para quê | Obrigatória |
|---|---|---|
| **Acessibilidade** | É a única forma no Android de um app tocar na tela de outro app. | Sim |
| **Sobrepor outros apps** | Mostra a bolha flutuante e a mira em tela cheia. | Sim |
| **Notificações** | O serviço precisa aparecer na barra para o sistema não matá-lo. | Recomendada |
| **Captura de tela** | Lê a cor do pixel. Só é pedida se você usar o gatilho por cor. | Só com gatilho de cor |

Ao tocar em "Conceder" na Acessibilidade, o Android abre a lista de serviços — procure **AutoClique Live** (às vezes fica em "Apps instalados" ou "Baixados") e ative.

---

## Como marcar um ponto

1. Toque em **Adicionar ponto**. O app se minimiza sozinho.
2. Abra o app da live (Twitch, YouTube, TikTok…) e deixe o botão de resgatar visível na tela.
3. Arraste a mira até o centro do botão.
4. Se quiser o gatilho por cor, toque em **Gravar cor** — a mira some por um instante e o app memoriza a cor real do botão.
5. Toque em **Confirmar**. O app volta e abre a tela de configuração do ponto.

Na tela de configuração você ajusta:

- **Intervalo entre cliques** — quanto tempo esperar antes de tocar de novo *no mesmo ponto*. Cada ponto tem o seu.
- **Duração do toque** — 40 ms funciona para quase tudo. Aumente se o botão exigir um toque mais demorado.
- **Só clicar quando a cor bater** — liga o gatilho por cor.
- **Tolerância** — 0% exige a cor exata; valores maiores aceitam variação. Comece em 10–15%. Se nunca clicar, aumente; se clicar quando não devia, diminua.
- **Verificar a cor a cada** — de quanto em quanto tempo reconferir enquanto o botão não aparece. 250 ms é um bom padrão.

---

## Usando

Toque em **Iniciar** (ou na bolha flutuante). A bolha fica verde enquanto está clicando e roxa quando está parada:

- **Um toque na bolha** liga/desliga.
- **Arrastar a bolha** a reposiciona.
- A notificação também traz os botões *Iniciar/Parar* e *Encerrar*.

**Encerrar** fecha tudo: laço de cliques, bolha e leitura de tela.

---

## Detalhes que importam na prática

**Rotação de tela.** As coordenadas são absolutas. O app grava o tamanho da tela no momento em que você marcou o ponto e **pausa automaticamente** os pontos se você girar o celular, em vez de clicar no lugar errado. Volte à orientação original e ele retoma. Se você usa a live em paisagem, marque os pontos em paisagem.

**A bolha rouba toques.** A bolha fica acima de tudo, então um toque injetado embaixo dela cairia nela. Ao iniciar, o app detecta isso e afasta a bolha sozinho, avisando por um toast.

**Gatilho por cor e o indicador de gravação.** Enquanto a captura estiver ativa o Android mostra o ícone de "gravando tela". É o sistema avisando, não o app enviando nada — nenhuma imagem é salva nem sai do aparelho. A leitura é de uma janelinha de 3×3 pixels em volta do ponto, e a média evita que gradiente e antialiasing façam a cor oscilar.

**Consumo.** O laço acorda a cada 30 ms para conferir os relógios; o custo real é baixo. O gatilho por cor lê o último quadro já disponível em memória, sem copiar a tela.

**Otimização de bateria.** Se o app parar sozinho depois de um tempo em segundo plano, coloque-o na lista de "sem restrição" de bateria — fabricantes como Xiaomi, Samsung e Motorola são agressivos com serviços em segundo plano.

---

## Compilando do zero

Precisa de Android Studio (ou só o SDK + JDK 17+).

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (assinado)
```

Se estiver fora do Android Studio, crie um `local.properties` na raiz:

```
sdk.dir=/caminho/para/o/android-sdk
```

---

## Como o app é organizado

```
model/ClickPoint.kt              Um ponto: coordenada, intervalo, cor-alvo, tolerância
data/PointStore.kt               Persistência em JSON dentro do SharedPreferences
engine/AutoClicker.kt            O laço: cada ponto tem seu próprio relógio
service/ClickAccessibilityService.kt   Executa o toque via dispatchGesture
service/OverlayService.kt        Bolha flutuante + mira em tela cheia
capture/ScreenCapture.kt         VirtualDisplay + ImageReader, lê a cor de um pixel
capture/CaptureService.kt        Foreground service exigido pelo MediaProjection
capture/ProjectionRequestActivity.kt   Tela invisível que pede a permissão de captura
ui/MainActivity.kt               Permissões, lista de pontos, editor
```

Nenhuma permissão de internet é declarada — o app não tem como enviar nada para lugar nenhum.

---

## Solução de problemas

**"Ative a Acessibilidade primeiro" mesmo já tendo ativado.** Alguns fabricantes desativam o serviço ao limpar a memória. Reabra a lista de acessibilidade e confira.

**Gravei a cor mas nunca clica.** Aumente a tolerância. Se o botão tiver animação ou brilho pulsante, a cor varia bastante — 20–25% costuma resolver. Também vale remarcar a cor com o botão bem visível na tela.

**Clica sozinho na hora errada.** Tolerância alta demais, ou o ponto caiu num lugar cuja cor de fundo é parecida com a do botão. Diminua a tolerância ou marque um pixel mais característico do botão.

**O toque não faz nada em um app específico.** Alguns apps (bancos, jogos anticheat) bloqueiam toques sintéticos e telas com `FLAG_SECURE` não podem ser lidas pela captura. Não há contorno.

---

## Aviso

Automatizar interações em plataformas de live pode contrariar os termos de uso do serviço. Verifique as regras da plataforma que você usa antes de deixar o app rodando.
# autoclique-live
