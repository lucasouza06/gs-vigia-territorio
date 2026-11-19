# Vigia Território · Territory Intelligence Dashboard

App Android (Kotlin + Jetpack Compose) da **GS2026.2 – Cross-Platform Application Development (FIAP)**.
Antecipa **chuva intensa e risco de alagamento** por território e apoia a decisão do gestor:
todo alerta vem com justificativa, confiança e fonte, e nenhuma decisão de alto impacto é automatizada.

> Todos os dados são **sintéticos** e os territórios são **fictícios** (município de Nova Aurora).

## Como executar

1. Android Studio Ladybug (2024.2) ou mais novo, com JDK 17.
2. Abra a pasta do projeto e espere o Gradle sincronizar.
3. Rode no emulador (ex.: Pixel, API 34) ou num aparelho com Android 8.0+.

Linha de comando:

```bash
./gradlew testDebugUnitTest   # testes das regras de alerta e da decisão
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
```

| Item | Valor |
| --- | --- |
| minSdk / targetSdk | 26 / 35 |
| Pacote | `br.com.vigiaterritorio` |
| Permissões | `POST_NOTIFICATIONS` (sem `INTERNET`) |
| CI | `.github/workflows/android.yml` roda testes e gera o APK a cada push |

## Roteiro de demonstração

| Território | O que mostra |
| --- | --- |
| Jardim das Águas | Alerta **crítico**: R1 (92 mm previstos) + R2 (solo saturado, 74 mm em 72 h) + R3 (córrego a 83% em 6 h) |
| Vila Esperança | Alerta de **atenção** (58 mm previstos) |
| Nova Aurora (município) | Alerta **informativo** (34 mm previstos) |
| Centro Histórico | Sem risco |
| Sítio Boa Vista | **Dados insuficientes** (pluviômetro com 10 de 24 horas): o app não emite alerta |

- **Permissão negada:** no painel, toque em "Ativar alertas críticos" e recuse. A faixa continua e o alerta segue visível no app. Recuse de novo e o botão leva às configurações do sistema.
- **Dados desatualizados:** 6 h depois da carga, o painel avisa e para de emitir alertas. Em Configurações → "Recarregar base sintética" a base é refeita.

## Arquitetura

Camadas (blueprint da disciplina) num único módulo, com MVVM e fluxo unidirecional na interface:

```
app/src/main/java/br/com/vigiaterritorio/
├── domain/        Kotlin puro (sem android.*)
│   ├── model/         entidades e enums do negócio
│   ├── engine/        AlertEngine (R1, R2, R3), IndicatorCalculator, DataFreshnessPolicy
│   ├── usecase/       ObserveDashboard, RegisterDecision, CheckCriticalAlerts…
│   └── repository/    contratos: ReadingRepository, DecisionRepository, PreferencesRepository, AlertNotifier
├── data/          Room, DataStore, CsvSeeder (implementam os contratos)
├── platform/      AndroidAlertNotifier, controle da permissão de notificação
├── di/            AppContainer (injeção manual)
└── ui/            tema, 9 componentes reutilizáveis, 5 telas, navegação
```

- **Estado:** `ViewModel` + `StateFlow<UiState>` (`sealed interface`), coletado com `collectAsStateWithLifecycle`.
- **Persistência:** Room (leituras, alertas decididos, decisões) e DataStore (território, limiares, responsável).
- **Offline:** CSV em `assets` → Room na primeira abertura (ver ADR-001 no documento).
- **Dados:** `tools/generate_synthetic_data.py` gera os CSV; origem e licença em `app/src/main/assets/DATA_SOURCES.md`.

## Regras de alerta

| Regra | Condição | Efeito |
| --- | --- | --- |
| R1 | Chuva prevista em 24 h: ≥ 30 mm / ≥ 50 mm / > 100 mm (ou pico de 20 / 30 / 60 mm/h) | Informativo / Atenção / Crítico |
| R2 | Acumulado de 72 h ≥ 60 mm | Sobe o nível de R1 em um grau |
| R3 | Regressão das últimas 6 leituras do córrego projetada em 6 h: ≥ 80% / ≥ 100% da cota | Atenção / Crítico |
| — | Dados com mais de 6 h ou cobertura de 24 h < 70% | **Não emite alerta** |

Confiança = 0,90 − 0,10 (sintético) − 0,5 × (1 − cobertura) − 0,10 (dados com mais de 3 h), entre 30% e 95%.

## Uso de IA

Rascunho inicial gerado com apoio do Claude (Anthropic). Todo trecho foi revisado, executado e assinado por um integrante. O registro completo está na Etapa 8 do documento.
