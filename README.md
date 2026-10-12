# Retro Game Station (Android) 🎮🚀

[![Android CI](https://github.com/dougretrogames/RetroGameStation/actions/workflows/build.yml/badge.svg)](https://github.com/dougretrogames/RetroGameStation/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org)
[![NDK](https://img.shields.io/badge/NDK-r26c%20(26.3.11579264)-orange.svg)](https://developer.android.com/ndk)
[![CMake](https://img.shields.io/badge/CMake-3.22.1%2B-red.svg)](https://cmake.org)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20armeabi--v7a-lightgrey.svg)](https://developer.android.com/ndk/guides/abis)

**Retro Game Station** é uma estação de emulação de jogos retro multifuncional e de alto desempenho para o ecossistema Android (smartphones, consoles portáteis como Ayn Odin/Retroid Pocket, e Android TV / Google TV). 

Projetada com arquitetura modular Gradle KTS de ponta, isolamento nativo rigoroso em C++ (NDK r26+), pipeline de renderização direta com zero-overhead (`ANativeWindow` Vulkan/OpenGL ES), áudio de ultrabaixa latência via AAudio, conformidade total com o Scoped Storage do Android 11+ (SAF) e sistema unificado de configuração e controles físicos.

---

## 📑 Sumário

- [Visão Geral da Arquitetura](#-visão-geral-da-arquitetura)
- [Estrutura Multi-Módulo](#-estrutura-multi-módulo)
- [Isolamento Nativo NDK & CMake](#-isolamento-nativo-ndk--cmake)
- [Motor Canônico de Input & Controles](#-motor-canônico-de-input--controles)
- [Motor Canônico de Vídeo & Gráficos](#-motor-canônico-de-vídeo--gráficos)
- [Hotkeys Globais Padronizadas](#-hotkeys-globais-padronizadas)
- [Persistência & Scoped Storage (SAF)](#-persistência--scoped-storage-saf)
- [Internacionalização & Localização (i18n)](#-internacionalização--localização-i18n)
- [Compilação & CI/CD no GitHub Actions](#-compilação--cicd-no-github-actions)
- [Como Compilar Localmente](#-como-compilar-localmente)
- [Licenciamento & Créditos](#-licenciamento--créditos)

---

## 🏛 Visão Geral da Arquitetura

O **Retro Game Station** desacopla a interface do usuário (UI frontend), o gerenciador de configurações/armazenamento e os múltiplos motores de emulação nativos (Libretro Cores, PPSSPP Standalone, Mupen64Plus Standalone) em bibliotecas Android isoladas:

```
+----------------------------------------------------------------------------------+
|                                    :app                                          |
|         Application, EmulatorHostActivity, Android TV Leanback Banner            |
+----------------------------------------------------------------------------------+
          |                                                       |
          v                                                       v
+-----------------------+                               +--------------------------+
|       :frontend       |                               |      :core-canonical     |
| NeoStation UI, Assets |                               | MasterConfig, Input,     |
| i18n Catalogs (ARB)   |                               | Storage SAF, Hotkeys     |
+-----------------------+                               +--------------------------+
                                                                  |
         +--------------------------------------------------------+
         |                        |                               |
         v                        v                               v
+------------------+    +-------------------+           +--------------------+
| :engine-libretro |    |  :engine-ppsspp   |           | :engine-mupen64plus|
| libretro.h, JNI, |    | ppsspp.ini,       |           | mupen64plus.cfg,   |
| AAudio, Vulkan   |    | controls.ini JNI  |           | GLideN64.ini JNI   |
+------------------+    +-------------------+           +--------------------+
```

---

## 📦 Estrutura Multi-Módulo

| Módulo | Tipo | Responsabilidade Principal |
| :--- | :--- | :--- |
| **`:app`** | Android Application | Ponto de entrada APK, splits ABI (`arm64-v8a`, `armeabi-v7a`), orquestração do ciclo de vida Android (`EmulatorHostActivity`), manifestos TV/Mobile e empacotamento de runtime `libc++_shared.so`. |
| **`:core-canonical`** | Android Library | Modelos canônicos de controle (`MasterInputConfig`) e vídeo (`MasterVideoConfig`), resolução hierárquica em cascata de configurações, calibração analógica e zonas mortas radiais, Scoped Storage SAF e Hotkeys globais. |
| **`:engine-libretro`** | Android Native Lib | Host de núcleos Libretro (`libretro.h`), bridge EGL/Vulkan com `ANativeWindow`, driver de áudio AAudio com anel circular sem travas, e JNI bridge de controle. |
| **`:engine-ppsspp`** | Android Native Lib | Motor PSP standalone integrado. Serializadores para `ppsspp.ini` e `controls.ini`, mapeamentos NKCODE do Android e JNI wrapper. |
| **`:engine-mupen64plus`** | Android Native Lib | Motor N64 standalone integrado. Serializadores dinâmicos para `mupen64plus.cfg` e `GLideN64.ini`, bindings JNI e plugins de vídeo. |
| **`:frontend`** | Android Library | Módulo do NeoStation adaptado para Android nativo, catálogos de localização ARB (`pt-BR`, `pt-PT`, `en-US`, `es`) e navegador de ROMs com Scoped Storage. |

---

## 🛡 Isolamento Nativo NDK & CMake

Para evitar conflitos de símbolos nativos comuns ao integrar múltiplos emuladores no mesmo processo (tais como colisões entre versões divergentes de `zlib`, `libpng`, `SDL2`, `FFmpeg` ou a Runtime C++):

1. **Visibilidade Oculta:** Todas as compilações utilizam `-fvisibility=hidden` e `-fvisibility-inlines-hidden`.
2. **Version Scripts do Linker (`*_symbols.map`):** Cada biblioteca compartilhada nativa (`libretro_host.so`, `ppsspp_engine.so`, `mupen64plus_engine.so`) utiliza scripts do linker (`-Wl,--version-script=...`) para expor estritamente apenas os pontos de entrada JNI (`Java_org_retrostation_engine_*`), ocultando 100% dos símbolos internos.
3. **Isolamento de Bibliotecas Estáticas:** Uso de `-Wl,--exclude-libs,ALL` ao vincular dependências C/C++.
4. **Gerenciamento Centralizado de C++ STL:** O APK final empacota de forma limpa uma única instância de `libc++_shared.so` gerenciada pelo Gradle em `packagingOptions.pickFirsts`.

---

## 🎮 Motor Canônico de Input & Controles

O **Retro Game Station** resolve o problema de ter que remapear controles em cada emulador diferente:

1. **Mapeamento Único:** O usuário configura seu controle físico uma única vez na UI do aplicativo.
2. **Auto-Detecção de Gamepads:** O `GamepadAutoDetector` inspeciona o `InputDevice` do Android (Vendor ID, Product ID, descritor e botões disponíveis) e reconhece perfis padrão:
   - Xbox Wireless / One / Series Controllers
   - PlayStation DualShock 4 & DualSense (PS5)
   - Nintendo Switch Pro Controller
   - Linha 8BitDo (Ultimate, Pro 2, SN30 Pro)
   - Android HID genérico
3. **Zonas Mortas Radiais & Calibração de Eixos:** O `AxisCalibration` calcula zonas mortas circulares e normalização matemática em ponto flutuante ($[-1.0, 1.0]$) para os analógicos e $([0.0, 1.0])$ para gatilhos analógicos L2/R2.
4. **Injeção Transparente em Tempo de Execução:**
   - **Libretro:** Converte dinamicamente para os bits canônicos de `RETRO_DEVICE_JOYPAD` e eixos de `retro_input_state_t`.
   - **PPSSPP:** Injeta as seções `[ControlMapping]` com NKCODEs correspondentes no `controls.ini`.
   - **Mupen64Plus:** Gera as seções `[Input-SDL-Control1]` dinâmicas no arquivo `mupen64plus.cfg`.

---

## 📺 Motor Canônico de Vídeo & Gráficos

A renderização é abstraída pelo modelo `MasterVideoConfig`:

- **API Gráfica:** Seleção unificada entre Vulkan 1.1+ e OpenGL ES 3.0/3.2.
- **Aspect Ratio:** Original (4:3 / Core Provided), Widescreen (16:9), Esticado, 1:1 Quadrado ou Escalonamento Inteiro (*Integer Scale* pixel-perfect).
- **Sincronização Vertical (VSync) & Frame Pacing:** Controle centralizado de buffers de swapchain.
- **Escala de Resolução Interna:** 1x (Nativa), 2x (720p), 3x (1080p), até 5x (4K) para motores 3D como PSP e N64.
- **Herança Hierárquica em 3 Camadas:**
  $$\text{Global Settings} \longrightarrow \text{Platform/System Settings} \longrightarrow \text{Per-Game Overrides}$$

---

## ⚡ Hotkeys Globais Padronizadas

Combinações de botões unificadas garantem a mesma experiência independentemente do motor ativo:

| Combinação | Ação Executada |
| :--- | :--- |
| **Hotkey + X** *(ou Guia/Home)* | Abre o Menu Rápido In-Game (*Quick Menu*) |
| **Hotkey + R1** | Salvar Estado (*Save State*) no slot atual |
| **Hotkey + L1** | Carregar Estado (*Load State*) do slot atual |
| **Hotkey + D-Pad Up / Down** | Selecionar Slot de Save (0 a 9) |
| **Hotkey + R2** | Alternar Avanço Rápido (*Fast-Forward Toggle*) |
| **Hotkey + Start** | Sair do Jogo e Retornar ao Launcher com segurança |

O `GlobalHotkeyManager` intercepta os acordes de botões antes que o evento chegue ao laço nativo do jogo, eliminando ações fantasmas nos emuladores.

---

## 💾 Persistência & Scoped Storage (SAF)

Em conformidade integral com as políticas de armazenamento do Android 11+ (API 30+):

- **Leitura Direta sem Cópia Duplicada:** O `ScopedStorageManager` resolve URIs de conteúdo (`content://`) obtidos via Storage Access Framework, extrai descritores de arquivo nativos via `ParcelFileDescriptor` e disponibiliza nós em `/proc/self/fd/{fd}` para leitura direta em C/C++, evitando cópias temporárias gigantescas na inicialização de ROMs pesadas.
- **Estrutura de Pastas Isolada e Persistente:**
  ```
  Android/data/org.retrostation.app/files/
  ├── saves/{system}/       # Arquivos de save de bateria (.srm, .sav)
  ├── states/{system}/      # Snapshots de save state (.state)
  ├── bios/                 # Arquivos de firmware e BIOS de sistema
  ├── shaders/              # Shaders GLSL/Slang
  └── screenshots/          # Capturas de tela in-game
  ```

---

## 🌐 Internacionalização & Localização (i18n)

O **Retro Game Station** possui suporte multilíngue nativo completo, com paridade de 100% em todas as chaves e qualificadores de localidade:

| Idioma | Código | Qualificador Android | Catálogo Flutter NeoStation |
| :--- | :--- | :--- | :--- |
| **Português (Brasil)** | `pt-BR` | `res/values-pt-rBR/strings.xml` | `lib/l10n/app_pt_BR.arb` |
| **Português (Portugal)** | `pt-PT` | `res/values-pt-rPT/strings.xml` | `lib/l10n/app_pt_PT.arb` |
| **Inglês (EUA)** | `en-US` | `res/values/strings.xml` | `lib/l10n/app_en.arb` |
| **Espanhol** | `es` | `res/values-es/strings.xml` | `lib/l10n/app_es.arb` |

Todas as nuances ortográficas (como *Ecrã* em `pt-PT` vs *Tela* em `pt-BR`, e *Ficheiro* vs *Arquivo*) são rigorosamente respeitadas.

---

## 🔄 Compilação & CI/CD no GitHub Actions

O repositório inclui automação contínua configurada em [`.github/workflows/build.yml`](.github/workflows/build.yml):

- **Disparo:** A cada `push` ou `pull_request` nas branches `main` e `master`, além de acionamento manual (`workflow_dispatch`).
- **Ambiente:** Ubuntu Latest com JDK 17 (Eclipse Temurin), Android SDK 34, Android NDK `26.3.11579264` e CMake `3.22.1`.
- **Etapas Automatizadas:**
  1. Instalação automatizada do SDK, NDK e CMake via `sdkmanager`.
  2. Execução da suíte completa de testes unitários (`./gradlew test`).
  3. Compilação dos APKs de Debug com todas as arquiteturas nativas (`./gradlew assembleDebug`).
  4. Upload automático dos APKs gerados como artefatos transferíveis do GitHub Actions.

---

## 💻 Como Compilar Localmente

### Pré-requisitos

1. **Android Studio Iguana | 2023.2.1** ou superior.
2. **JDK 17** configurado no ambiente.
3. **Android SDK Platform 34** instalado.
4. **Android NDK** versão `26.3.11579264`.
5. **CMake** versão `3.22.1` ou superior.

### Linha de Comando (Gradle CLI)

Clone o repositório e execute os comandos no terminal:

```bash
# Clonar o repositório
git clone https://github.com/dougretrogames/RetroGameStation.git
cd RetroGameStation

# Executar suíte de testes unitários
./gradlew test

# Compilar APK de Debug (arm64-v8a e armeabi-v7a)
./gradlew assembleDebug

# Os APKs compilados serão gerados em:
# app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
# app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
```

---

## 📜 Licenciamento & Créditos

Este projeto é software livre e de código aberto licenciado sob a **[GNU General Public License v3.0](LICENSE)** (GPLv3).

### Projetos e Referências Upstream
- **[NeoStation Frontend](https://github.com/misobadev/neostation-frontend)** - UI base e conceitos de navegação para consoles retro.
- **[Libretro / RetroArch](https://github.com/libretro/RetroArch)** - Especificação da API `libretro.h` e conceitos de drivers gráficos/áudio leves.
- **[PPSSPP](https://github.com/hrydgard/ppsspp)** - Emulador standalone de Sony PlayStation Portable de autoria de Henrik Rydgård.
- **[Mupen64Plus-AE](https://github.com/mupen64plus-ae/mupen64plus-ae)** - Emulador standalone de Nintendo 64 para Android.
