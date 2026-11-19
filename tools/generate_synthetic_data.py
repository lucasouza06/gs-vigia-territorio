#!/usr/bin/env python3
"""
Gera a base SINTÉTICA do Vigia Território (licença CC0).

Saída (em app/src/main/assets):
  - territories.csv : id,name,type,latitude,longitude
  - readings.csv    : territory_id,indicator,kind,offset_hours,value,origin

Cada leitura guarda o deslocamento em horas em relação ao momento da carga
(-71..0 = observado, +1..+72 = previsto). O app converte para data/hora real
ao carregar, de modo que a demonstração faz sentido em qualquer dia.

Todos os territórios são fictícios (município de "Nova Aurora").
Cenários:
  jardim-das-aguas   -> CRÍTICO (chuva forte prevista + solo saturado + córrego subindo)
  vila-esperanca     -> ATENÇÃO
  nova-aurora        -> INFORMATIVO (agregado do município)
  centro-historico   -> NORMAL (sem alerta)
  sitio-boa-vista    -> DADOS INSUFICIENTES (pluviômetro com falhas, sem sensor de córrego)

Uso:  python3 tools/generate_synthetic_data.py
"""
import csv
import math
import random
from pathlib import Path

SEED = 2026
ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"

TERRITORIES = [
    ("jardim-das-aguas", "Jardim das Águas", "BAIRRO", -23.5402, -46.4501),
    ("vila-esperanca", "Vila Esperança", "BAIRRO", -23.5520, -46.4710),
    ("centro-historico", "Centro Histórico", "BAIRRO", -23.5480, -46.4890),
    ("sitio-boa-vista", "Sítio Boa Vista", "RURAL", -23.6150, -46.5300),
    ("nova-aurora", "Nova Aurora (município)", "CIDADE", -23.5550, -46.4800),
]


def shaped_series(rng, n, total, peak=None, peak_index=None, dry_ratio=0.45):
    """n valores >= 0 somando exatamente `total` (1 casa decimal).
    Se `peak` for dado, a posição `peak_index` recebe esse valor e as demais ficam abaixo dele."""
    weights = [0.0 if rng.random() < dry_ratio else rng.random() ** 2 for _ in range(n)]
    if peak is not None:
        weights[peak_index] = 0.0
    remaining = total - (peak or 0.0)
    s = sum(weights) or 1.0
    values = [round(w / s * remaining, 1) for w in weights]
    if peak is not None:
        cap = peak - 1.0
        overflow = 0.0
        for i, v in enumerate(values):
            if v > cap:
                overflow += v - cap
                values[i] = cap
        # redistribui o excesso nas horas com chuva abaixo do teto
        while overflow > 0.05:
            for i, v in enumerate(values):
                if i != peak_index and 0 < v < cap and overflow > 0.05:
                    add = min(cap - v, overflow, 2.0)
                    values[i] = round(v + add, 1)
                    overflow -= add
        values[peak_index] = peak
    diff = round(total - sum(values), 1)
    # ajusta o arredondamento na maior hora que não seja o pico
    idx = max((i for i in range(n) if i != peak_index), key=lambda i: values[i])
    values[idx] = round(values[idx] + diff, 1)
    return values


def stream_series(rng, base, last_six):
    """72 leituras observadas (-71..0): estável perto de `base`, terminando nos 6 valores dados."""
    early = [round(base + math.sin(i / 7) * 2 + rng.uniform(-1, 1), 1) for i in range(66)]
    return early + last_six


def build(rng):
    rows = []

    def add(tid, indicator, kind, offsets, values):
        for off, v in zip(offsets, values):
            if v is None:
                continue
            rows.append((tid, indicator, kind, off, f"{v:.1f}", "SINTETICO"))

    obs_old = list(range(-71, -23))   # 48 h mais antigas
    obs_24 = list(range(-23, 1))      # últimas 24 h
    fc_24 = list(range(1, 25))
    fc_48 = list(range(25, 73))

    # Jardim das Águas: 72 h = 74 mm (24 h = 18 mm); previsão 24 h = 92 mm, pico 38 mm/h
    t = "jardim-das-aguas"
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_old, shaped_series(rng, 48, 56.0))
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_24, shaped_series(rng, 24, 18.0))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_24, shaped_series(rng, 24, 92.0, peak=38.0, peak_index=17, dry_ratio=0.3))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_48, shaped_series(rng, 48, 31.0))
    add(t, "NIVEL_CORREGO_PCT", "OBSERVADO", list(range(-71, 1)),
        stream_series(rng, 44, [55.5, 58.0, 60.5, 63.0, 65.5, 68.0]))

    # Vila Esperança: 72 h = 22 mm; previsão 24 h = 58 mm, pico 24 mm/h; córrego 52% subindo 0,8/h
    t = "vila-esperanca"
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_old, shaped_series(rng, 48, 14.0, dry_ratio=0.7))
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_24, shaped_series(rng, 24, 8.0, dry_ratio=0.6))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_24, shaped_series(rng, 24, 58.0, peak=24.0, peak_index=18, dry_ratio=0.35))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_48, shaped_series(rng, 48, 20.0))
    add(t, "NIVEL_CORREGO_PCT", "OBSERVADO", list(range(-71, 1)),
        stream_series(rng, 46, [48.0, 48.8, 49.6, 50.4, 51.2, 52.0]))

    # Centro Histórico: tempo bom, sem alerta
    t = "centro-historico"
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_old, shaped_series(rng, 48, 9.0, dry_ratio=0.8))
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_24, shaped_series(rng, 24, 3.0, dry_ratio=0.8))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_24, shaped_series(rng, 24, 8.0, peak=4.0, peak_index=15, dry_ratio=0.6))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_48, shaped_series(rng, 48, 6.0, dry_ratio=0.7))
    add(t, "NIVEL_CORREGO_PCT", "OBSERVADO", list(range(-71, 1)),
        stream_series(rng, 30, [30.4, 30.1, 30.3, 30.0, 30.2, 30.1]))

    # Sítio Boa Vista: pluviômetro com falhas (só 10 das últimas 24 h) e sem sensor de córrego
    t = "sitio-boa-vista"
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_old, shaped_series(rng, 48, 12.0, dry_ratio=0.7))
    present = set(rng.sample(obs_24, 10))
    last24 = shaped_series(rng, 24, 6.0, dry_ratio=0.5)
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_24, [v if off in present else None for off, v in zip(obs_24, last24)])
    add(t, "CHUVA_MM_H", "PREVISTO", fc_24, shaped_series(rng, 24, 46.0, peak=18.0, peak_index=16, dry_ratio=0.4))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_48, shaped_series(rng, 48, 15.0))

    # Nova Aurora (município, agregado): previsão 24 h = 34 mm, pico 12 mm/h -> informativo
    t = "nova-aurora"
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_old, shaped_series(rng, 48, 26.0))
    add(t, "CHUVA_MM_H", "OBSERVADO", obs_24, shaped_series(rng, 24, 9.0))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_24, shaped_series(rng, 24, 34.0, peak=12.0, peak_index=17, dry_ratio=0.35))
    add(t, "CHUVA_MM_H", "PREVISTO", fc_48, shaped_series(rng, 48, 18.0))
    add(t, "NIVEL_CORREGO_PCT", "OBSERVADO", list(range(-71, 1)),
        stream_series(rng, 38, [40.0, 40.2, 40.1, 40.4, 40.3, 40.5]))

    return rows


def main():
    rng = random.Random(SEED)
    ASSETS.mkdir(parents=True, exist_ok=True)
    with open(ASSETS / "territories.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "name", "type", "latitude", "longitude"])
        w.writerows(TERRITORIES)
    rows = build(rng)
    with open(ASSETS / "readings.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["territory_id", "indicator", "kind", "offset_hours", "value", "origin"])
        w.writerows(rows)
    print(f"{len(TERRITORIES)} territórios, {len(rows)} leituras -> {ASSETS}")


if __name__ == "__main__":
    main()
