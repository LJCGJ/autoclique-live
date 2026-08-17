# Play Console — textos prontos para copiar

App: **AutoClique Live** · pacote `com.autoclique.live`

---

## 1. Ficha da loja

**Nome do app** (30 caracteres)

```
AutoClique Live
```

**Descrição breve** (80 caracteres — este texto tem 78)

```
Clicador automático com gatilho por cor. Você marca o ponto, ele toca sozinho.
```

**Descrição completa** (limite de 4000 caracteres)

```
O AutoClique Live toca na tela por você, nos pontos e nos intervalos que você mesmo define.

Marque um ponto arrastando uma mira sobre o botão desejado, escolha de quantos em quantos segundos ele deve ser tocado, e pronto. O app faz o resto — inclusive por cima de outros aplicativos.

GATILHO POR COR
O diferencial: em vez de tocar no vazio a cada X segundos, o app pode conferir a cor do pixel naquele ponto e só tocar quando o botão realmente aparecer. Útil para botões que surgem de tempos em tempos.

BOTS SEPARADOS
Organize seus pontos em bots com nome. Um para cada situação, cada um com suas próprias posições e tempos. Só o bot selecionado é executado, então um não atrapalha o outro.

CONTROLE SIMPLES
• Intervalos em segundos, com a taxa de cliques por segundo calculada na hora
• Botão flutuante para ligar e desligar sem voltar ao app
• Notificação permanente enquanto está ativo, para você sempre saber e poder parar
• Pausa automática se a tela girar, evitando toques em lugar errado

PRIVACIDADE
• Não coleta, não armazena e não compartilha nenhum dado pessoal
• Não pede permissão de internet — não há como enviar informação para fora do aparelho
• Sem anúncios e sem ferramentas de análise
• Suas configurações ficam apenas no seu celular e somem ao desinstalar

SOBRE A PERMISSÃO DE ACESSIBILIDADE
Este app usa o Serviço de Acessibilidade unicamente para executar toques nas coordenadas que você configurou. Ele não lê o conteúdo das telas, não interpreta textos e não toma decisões por conta própria: segue apenas o roteiro que você definiu. Antes de qualquer uso, o app exibe uma tela explicando isso e pede seu consentimento.

Código-fonte aberto: github.com/LJCGJ/autoclique-live
```

**Categoria:** Ferramentas
**Tipo:** Aplicativo · Gratuito
**Site:** `https://ljcgj.github.io/autoclique-live/`
**Política de privacidade:** `https://ljcgj.github.io/autoclique-live/privacidade.html`

---

## 2. Declaração de Permissão — AccessibilityService

É o formulário mais importante. O texto abaixo se apoia na parte da política do
Google que **permite** automação determinística baseada em regras.

**Funcionalidade principal do app**

```
Automação de toques na tela definida pelo usuário. A pessoa marca coordenadas
específicas e define de quantos em quantos segundos cada uma deve ser tocada.
O aplicativo executa exatamente esse roteiro.
```

**Por que o AccessibilityService é necessário**

```
Executar um toque na tela sobre outro aplicativo só é possível, no Android,
através de AccessibilityService.dispatchGesture(). Não existe API alternativa
para essa funcionalidade. O serviço é usado exclusivamente para despachar
gestos de toque.

O app NÃO lê conteúdo de tela: o método onAccessibilityEvent() está vazio e
todos os eventos recebidos do sistema são descartados. Nenhum texto, campo de
formulário, credencial ou informação exibida por outros aplicativos é lido,
armazenado ou transmitido.

O comportamento é determinístico e baseado em regras estáticas definidas pelo
usuário, conforme permitido pela política. O aplicativo não inicia, planeja
nem executa ações de forma autônoma, não usa aprendizado de máquina e não
altera seu comportamento sem uma mudança explícita feita pelo usuário.
```

**Divulgação proeminente**

```
Na primeira execução, antes de qualquer permissão ser solicitada, o app exibe
uma tela dedicada que descreve: quais permissões sensíveis são usadas, para que
servem, o que NÃO é feito com elas, e o fato de nenhum dado sair do aparelho.
O consentimento é dado por ação afirmativa em um botão "Aceito e quero
continuar". Recusar encerra o aplicativo, e sem o aceite nenhuma tela funcional
fica acessível.
```

**Uso de captura de tela (MediaProjection)**

```
Recurso opcional, solicitado apenas se o usuário ativar o gatilho por cor em
algum ponto. Nesse caso é lida a cor dos pixels de uma janela de 3x3 em torno
da coordenada configurada, para decidir se o toque deve ocorrer. Nenhuma
imagem é gravada, exibida ou transmitida; o valor de cor é comparado em
memória e descartado.
```

---

## 3. Segurança de Dados

Respostas para o questionário:

| Pergunta | Resposta |
|---|---|
| Coleta ou compartilha dados do usuário? | **Não** |
| Os dados são criptografados em trânsito? | Não se aplica (não há transmissão) |
| Há como solicitar exclusão de dados? | Não se aplica (nada é coletado) |
| Tipos de dados coletados | **Nenhum** |

Justificativa, se pedirem: o app não declara `android.permission.INTERNET`.
Sem essa permissão o Android impede qualquer conexão de rede — é uma
característica verificável no manifesto, não apenas uma promessa.

---

## 4. Classificação indicativa

- Categoria: **Utilitário / Produtividade / Comunicação**
- Violência, sexo, drogas, jogos de azar, linguagem imprópria: **Não** para todos
- Compartilha localização, informações pessoais ou permite comunicação entre usuários: **Não**
- Compras no app: **Não**
- Resultado esperado: **Livre para todos os públicos**

---

## 5. Público-alvo

- Faixa etária: **18 anos ou mais** (evita as exigências extras da política de família)
- O app é direcionado a crianças? **Não**

---

## 6. Capturas de tela

A Play exige no mínimo **2** capturas de celular; o ideal são 4 a 6.
Formato: PNG ou JPEG, entre 320 e 3840 px de lado, proporção máxima 2:1.

Sugestão do que mostrar, nesta ordem:

1. Tela principal com um bot selecionado e alguns pontos na lista
2. A mira em tela cheia sobre um botão real
3. O editor de ponto, mostrando "2 cliques por segundo"
4. A tela de divulgação proeminente (mostra transparência ao revisor)

Para capturar com qualidade direto do emulador ou do celular:

```powershell
$a = "D:\Android\Sdk\platform-tools\adb.exe"
& $a shell screencap -p /sdcard/tela1.png
& $a pull /sdcard/tela1.png .\loja\tela1.png
& $a shell rm /sdcard/tela1.png
```

Repita trocando o número para cada tela.
