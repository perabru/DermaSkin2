# 🩺 DermaSkin2

Aplicativo Android desenvolvido em **Kotlin** para análise visual de manchas e lesões de pele utilizando **Inteligência Artificial** e técnicas de processamento de imagem.

O sistema permite capturar uma imagem pelo smartphone, enviá-la para um modelo de classificação desenvolvido no **Google Teachable Machine** e apresentar uma análise complementar baseada em características visuais da lesão.

Além da classificação por Inteligência Artificial, o aplicativo realiza uma avaliação inspirada no método **ABCDE**, estima o tamanho visual da região analisada e permite gerar um **relatório em PDF** com os resultados.

> ⚠️ **Importante:** o DermaSkin2 é um projeto acadêmico e experimental de triagem visual. Ele não realiza diagnóstico médico e não substitui a avaliação de um dermatologista ou outro profissional de saúde.

---

# 📱 Sobre o projeto

O **DermaSkin2** foi desenvolvido com o objetivo de explorar a aplicação de Inteligência Artificial e processamento de imagens em dispositivos móveis.

O aplicativo combina:

- 📱 Android;
- 🟣 Kotlin;
- 🤖 Inteligência Artificial;
- 🧠 Google Teachable Machine;
- 🔥 Firebase;
- 📷 Câmera do smartphone;
- 🖼️ Processamento de imagens;
- 🔬 Análise visual ABCDE;
- 📊 Classificação de risco;
- 📄 Relatórios em PDF.

O sistema funciona como uma ferramenta experimental de **apoio à triagem visual de alterações da pele**.

---

# 🚀 Funcionalidades

O aplicativo possui atualmente:

- 👤 Criação de conta;
- 🔐 Login de usuário;
- 🔥 Autenticação com Firebase Authentication;
- 💾 Cadastro de informações no Firebase Realtime Database;
- 📷 Captura de imagem utilizando a câmera;
- 🤖 Análise utilizando Inteligência Artificial;
- 🧠 Integração com modelo do Google Teachable Machine;
- 📊 Exibição das probabilidades das classes;
- 🚦 Classificação visual em risco baixo, médio ou alto;
- 🔍 Análise complementar utilizando critérios ABCDE;
- 📐 Estimativa da área da mancha;
- 📏 Estimativa de diâmetro visual em pixels;
- 🔄 Registro manual de evolução da lesão;
- 📄 Geração de relatório em PDF;
- 🚪 Logout da conta.

---

# 🧠 Inteligência Artificial

O DermaSkin2 utiliza um modelo desenvolvido com:

**Google Teachable Machine**

O modelo é carregado diretamente pela internet utilizando:

- TensorFlow.js;
- Teachable Machine Image;
- WebView Android.

O modelo atualmente utilizado está disponível em:

```text
https://teachablemachine.withgoogle.com/models/zceGA0QO5/
```

O aplicativo carrega:

```text
model.json
metadata.json
```

e utiliza a imagem capturada para realizar a classificação.

---

# 🧬 Classes da Inteligência Artificial

O modelo trabalha com duas classes principais:

```text
Cancer
Nao_Cancer
```

Após a análise, o aplicativo recebe a probabilidade de cada uma das classes.

Exemplo:

```text
Cancer: 18.5%
Nao_Cancer: 81.5%
```

Esses percentuais representam a **compatibilidade encontrada pelo modelo**, e não uma probabilidade clínica ou diagnóstico médico.

---

# 🚦 Classificação de risco

Com base na compatibilidade com a classe `Cancer`, o aplicativo organiza o resultado em três níveis visuais.

### 🟢 Risco baixo

```text
Cancer < 40%
```

A imagem apresentou baixa compatibilidade com a classe utilizada pelo modelo.

---

### 🟡 Risco médio

```text
Cancer >= 40% e < 70%
```

O modelo encontrou compatibilidade intermediária e o aplicativo recomenda maior atenção à lesão.

---

### 🔴 Risco alto

```text
Cancer >= 70%
```

A imagem apresentou alta compatibilidade com a classe utilizada pelo modelo.

Nesse caso, o aplicativo reforça a recomendação de avaliação profissional.

---

# 🔬 Análise ABCDE

Além do resultado da Inteligência Artificial, o DermaSkin2 realiza uma análise visual complementar inspirada no método **ABCDE**.

```text
A → Assimetria
B → Bordas
C → Cor
D → Diâmetro
E → Evolução
```

---

## A — Assimetria

O aplicativo compara regiões opostas da imagem para estimar diferenças visuais.

Simplificadamente:

```text
Lado esquerdo
       ↓
Comparação de pixels
       ↑
Lado direito
```

Quanto maior a diferença entre as regiões, maior o índice de assimetria visual calculado.

---

## B — Bordas

O sistema verifica alterações de brilho entre pixels vizinhos.

Grandes variações podem indicar regiões com bordas visualmente mais irregulares.

O aplicativo calcula uma pontuação baseada na quantidade de alterações encontradas.

---

## C — Cor

O aplicativo analisa os canais:

```text
R → Red
G → Green
B → Blue
```

Primeiro é calculada a média das cores da imagem.

Depois o sistema verifica quanto cada pixel varia em relação à média.

Isso gera um índice de **variação de cor**.

---

## D — Diâmetro

O aplicativo estima visualmente a área da região mais escura encontrada na imagem.

A imagem é redimensionada para:

```text
224 × 224 pixels
```

O sistema calcula:

- Área aproximada em pixels;
- Percentual ocupado pela região na imagem;
- Diâmetro visual aproximado em pixels.

Exemplo:

```text
Área visual estimada: 4250 pixels

Ocupação aproximada:
8.47%

Diâmetro visual aproximado:
73.56 pixels
```

### ⚠️ Limitação da medida

O valor representa apenas uma estimativa visual.

Não é possível transformar diretamente esse valor em milímetros sem possuir um objeto de referência na imagem, como:

- régua;
- marcador;
- moeda;
- escala conhecida.

A distância da câmera também influencia o resultado.

---

## E — Evolução

O aplicativo possui uma opção para o usuário informar se percebeu mudança recente na mancha ou lesão.

Exemplo:

```text
☑ Houve mudança recente na mancha
```

Essa informação participa da análise complementar exibida pelo aplicativo.

---

# 🧮 Pontuação visual complementar

O sistema utiliza quatro fatores para gerar uma pontuação:

```text
Assimetria
+
Bordas
+
Cor
+
Evolução
```

O resultado pode variar de:

```text
0 / 4
até
4 / 4
```

Exemplo:

```text
Pontuação visual estimada: 3/4
```

Com base nessa pontuação, o aplicativo apresenta orientações de atenção:

```text
0–1 → Atenção baixa

2–3 → Atenção moderada

4 → Atenção alta
```

Essa pontuação também representa apenas uma análise computacional experimental.

---

# 📐 Estimativa da área da lesão

Para estimar aproximadamente a região da imagem, o sistema utiliza informações de luminosidade.

O fluxo simplificado é:

```text
Imagem
   │
   ▼
Redimensionamento
224 × 224
   │
   ▼
Cálculo de brilho
   │
   ▼
Brilho médio
   │
   ▼
Definição de limiar
   │
   ▼
Identificação de pixels
potencialmente pertencentes
à região analisada
   │
   ▼
Área aproximada
```

O algoritmo considera pixels mais escuros em relação ao brilho médio da imagem.

---

# 📊 Fluxo geral do aplicativo

```text
              ┌─────────────────┐
              │     Usuário     │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ Cadastro/Login  │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │    Firebase     │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ Capturar imagem │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │     Bitmap      │
              └────────┬────────┘
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
 ┌──────────────────┐    ┌──────────────────┐
 │ Teachable Machine│    │ Processamento de │
 │      + TF.js     │    │      imagem      │
 └────────┬─────────┘    └────────┬─────────┘
          │                       │
          ▼                       ▼
 ┌──────────────────┐    ┌──────────────────┐
 │ Cancer           │    │ Assimetria       │
 │ Nao_Cancer       │    │ Bordas           │
 └────────┬─────────┘    │ Cor              │
          │              │ Área             │
          │              │ Evolução         │
          │              └────────┬─────────┘
          │                       │
          └───────────┬───────────┘
                      │
                      ▼
              ┌─────────────────┐
              │ Resultado final │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ Relatório PDF   │
              └─────────────────┘
```

---

# 🔥 Firebase

O projeto utiliza dois serviços principais do Firebase.

## Firebase Authentication

Responsável por:

- criação de conta;
- autenticação;
- login;
- manutenção da sessão;
- logout.

O login utiliza:

```text
E-mail
Senha
```

---

## Firebase Realtime Database

Durante o cadastro, algumas informações do usuário são armazenadas em:

```text
usuarios/
    UID_DO_USUARIO/
```

Estrutura aproximada:

```json
{
  "id": "UID_DO_USUARIO",
  "nome": "Nome do usuário",
  "email": "usuario@email.com",
  "dataCadastro": 0000000000000
}
```

---

# 🔐 Cadastro

Para criar uma conta o aplicativo solicita:

```text
Nome
E-mail
Senha
```

A senha deve possuir pelo menos:

```text
6 caracteres
```

O usuário também precisa aceitar o aviso relacionado à natureza de triagem do aplicativo.

---

# 📷 Captura da imagem

O aplicativo utiliza a câmera do smartphone para capturar a imagem que será analisada.

A permissão utilizada é:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

O aplicativo utiliza:

```kotlin
ActivityResultContracts.TakePicturePreview()
```

para receber a imagem capturada como um `Bitmap`.

---

# 🌐 Acesso à internet

O DermaSkin2 necessita de internet porque o modelo de Inteligência Artificial é carregado online.

O Manifest possui:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

O fluxo do modelo é:

```text
Aplicativo
    │
    ▼
WebView
    │
    ▼
TensorFlow.js
    │
    ▼
Teachable Machine
    │
    ▼
model.json
metadata.json
    │
    ▼
Predição
```

---

# 📄 Relatório PDF

Após uma análise, o aplicativo permite gerar um relatório em formato:

```text
PDF
```

O nome é criado automaticamente no formato:

```text
relatorio_dermaskin_XXXXXXXXXXXX.pdf
```

O relatório reúne informações obtidas durante a análise.

A geração é feita utilizando:

```kotlin
PdfDocument
```

do próprio Android.

---

# 🛠️ Tecnologias utilizadas

| Tecnologia | Utilização |
|---|---|
| Kotlin | Linguagem principal |
| Android Studio | Desenvolvimento |
| Android SDK | Plataforma |
| XML | Interface gráfica |
| Firebase Authentication | Cadastro e login |
| Firebase Realtime Database | Armazenamento dos usuários |
| Google Teachable Machine | Modelo de classificação |
| TensorFlow.js | Execução do modelo |
| JavaScript | Comunicação com o modelo |
| Android WebView | Execução da IA dentro do aplicativo |
| Bitmap | Processamento da imagem |
| Canvas | Construção do relatório |
| PdfDocument | Geração do PDF |
| Gradle | Build e dependências |

---

# ⚙️ Configuração atual

O projeto utiliza:

```text
Application ID:
com.perabru.dermaskin2

Minimum SDK:
24

Target SDK:
36

Version:
1.0

Java:
11
```

---

# 📂 Estrutura principal

```text
DermaSkin2/
│
├── app/
│   │
│   ├── src/
│   │   └── main/
│   │       │
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── perabru/
│   │       │           └── dermaskin2/
│   │       │               │
│   │       │               ├── MainActivity.kt
│   │       │               └── AnaliseActivity.kt
│   │       │
│   │       ├── res/
│   │       │   ├── drawable/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   └── activity_analise.xml
│   │       │   │
│   │       │   ├── mipmap/
│   │       │   └── values/
│   │       │
│   │       └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts
│   ├── google-services.json
│   └── proguard-rules.pro
│
├── gradle/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
│
└── README.md
```

---

# 💻 Arquivos principais

## `MainActivity.kt`

Responsável principalmente pelo sistema de autenticação.

Possui:

- cadastro;
- login;
- validação de formulário;
- Firebase Authentication;
- armazenamento do usuário;
- controle de sessão;
- redirecionamento para a análise.

---

## `AnaliseActivity.kt`

É o núcleo principal do projeto.

Responsável por:

- captura da imagem;
- gerenciamento da câmera;
- carregamento do modelo;
- comunicação Kotlin ↔ JavaScript;
- Teachable Machine;
- interpretação da predição;
- análise de risco;
- análise ABCDE;
- processamento dos pixels;
- estimativa da área;
- geração do PDF;
- logout.

---

## `activity_main.xml`

Interface responsável pelas telas de:

```text
Cadastro
Login
```

---

## `activity_analise.xml`

Interface responsável pela área de análise.

Possui componentes para:

- câmera;
- imagem capturada;
- análise;
- resultado;
- risco;
- tamanho;
- ABCDE;
- detalhes da IA;
- geração do PDF.

---

## `AndroidManifest.xml`

Responsável pelas principais configurações do aplicativo.

Inclui as permissões:

```xml
android.permission.CAMERA
android.permission.INTERNET
```

---

# ▶️ Como executar

## 1. Clone o projeto

```bash
git clone https://github.com/perabru/DermaSkin2.git
```

Entre na pasta:

```bash
cd DermaSkin2
```

---

## 2. Abra no Android Studio

Abra:

```text
Android Studio
    ↓
Open
    ↓
DermaSkin2
```

---

## 3. Aguarde o Gradle

Espere a conclusão da sincronização:

```text
Gradle Sync
```

---

## 4. Firebase

Caso esteja utilizando seu próprio projeto Firebase, configure:

```text
Firebase Authentication
        ↓
Sign-in method
        ↓
Email/Password
        ↓
Enable
```

Também configure o:

```text
Realtime Database
```

Depois coloque o arquivo:

```text
google-services.json
```

dentro da pasta:

```text
app/
```

---

## 5. Execute

Conecte um dispositivo Android ou utilize um emulador.

Clique em:

```text
▶ Run
```

Para testar corretamente a câmera, é recomendado utilizar um smartphone físico.

---

# 📲 Como usar

O fluxo básico é:

```text
1. Abra o aplicativo

2. Crie uma conta

3. Aceite o aviso de triagem

4. Faça login

5. Entre na área de análise

6. Informe se houve mudança recente na lesão

7. Toque para tirar uma foto

8. Posicione a mancha no centro da imagem

9. Capture a foto

10. Aguarde o carregamento da IA

11. Toque em analisar

12. Veja o percentual das classes

13. Consulte a classificação de risco

14. Consulte a análise ABCDE

15. Consulte a estimativa visual de tamanho

16. Gere o relatório PDF
```

---

# 📷 Recomendações para captura

Para melhorar a consistência da imagem:

- Utilize boa iluminação;
- Evite sombras muito fortes;
- Mantenha a câmera estável;
- Mantenha a lesão centralizada;
- Evite imagens desfocadas;
- Evite filtros de câmera;
- Procure utilizar condições semelhantes ao comparar imagens.

A qualidade da fotografia influencia diretamente a análise computacional.

---

# ⚠️ Limitações

O sistema possui limitações importantes.

### Modelo de IA

A classificação depende diretamente:

- das imagens utilizadas no treinamento;
- da iluminação;
- da câmera;
- da qualidade da fotografia;
- do enquadramento;
- do modelo treinado.

### Tamanho

A medida atualmente é realizada em:

```text
pixels
```

e não em milímetros.

### ABCDE

A análise ABCDE implementada representa uma interpretação computacional simplificada de características visuais da imagem.

### Diagnóstico

O aplicativo **não deve ser utilizado para confirmar ou descartar câncer de pele ou qualquer outra doença**.

O resultado deve ser interpretado somente como uma ferramenta experimental de triagem visual.

---

# 🚨 Aviso médico

> O DermaSkin2 não é um dispositivo médico e não substitui consulta, exame clínico, dermatoscopia, biópsia ou diagnóstico realizado por profissional habilitado.

Se existir uma lesão que:

- mudou de tamanho;
- mudou de formato;
- mudou de cor;
- começou a sangrar;
- apresenta ferida persistente;
- coça frequentemente;
- cresce rapidamente;

a orientação apresentada pelo próprio projeto é buscar avaliação profissional.

---

# 🔮 Possíveis melhorias

Algumas evoluções possíveis para o projeto:

- [ ] Armazenar histórico das análises;
- [ ] Comparar imagens ao longo do tempo;
- [ ] Criar perfil completo do usuário;
- [ ] Armazenar relatórios no Firebase;
- [ ] Criar gráfico de evolução;
- [ ] Permitir seleção de imagem da galeria;
- [ ] Utilizar CameraX;
- [ ] Melhorar segmentação da lesão;
- [ ] Adicionar objeto de referência para medida em milímetros;
- [ ] Implementar modelo TensorFlow Lite local;
- [ ] Permitir funcionamento offline;
- [ ] Armazenar o modelo diretamente no aplicativo;
- [ ] Melhorar a análise de bordas;
- [ ] Melhorar a segmentação por cor;
- [ ] Adicionar processamento OpenCV;
- [ ] Criar histórico ABCDE;
- [ ] Criar acompanhamento periódico;
- [ ] Melhorar o relatório PDF;
- [ ] Adicionar gráficos ao relatório;
- [ ] Desenvolver painel web;
- [ ] Implementar testes automatizados;
- [ ] Realizar validação científica do modelo.

---

# 🎓 Aplicações acadêmicas

O projeto pode ser utilizado no estudo de:

- Inteligência Artificial;
- Machine Learning;
- Visão Computacional;
- Processamento Digital de Imagens;
- Desenvolvimento Mobile;
- Kotlin;
- Firebase;
- Engenharia de Software;
- Sistemas de apoio à decisão;
- Interação Homem-Computador.

---

# 👨‍💻 Autor

**Bruno Michel Pera**

Desenvolvimento de projetos nas áreas de:

- Desenvolvimento de Software;
- Android/Kotlin;
- Inteligência Artificial;
- Internet das Coisas;
- Sistemas Embarcados;
- Processamento de Sinais;
- Visão Computacional;
- Educação Tecnológica.

GitHub:

**[@perabru](https://github.com/perabru)**

---

# 🔗 Repositório

```text
https://github.com/perabru/DermaSkin2
```

---

# ⭐ Contribuições

Sugestões, melhorias e contribuições são bem-vindas.

Se o projeto foi útil para seus estudos, considere deixar uma **⭐ Star** no repositório.

---

## 🩺 DermaSkin2

**Inteligência Artificial e processamento de imagens aplicados à triagem visual de alterações da pele.**
