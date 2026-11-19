# Fontes de dados do Vigia Território

| Arquivo | Origem | Licença | Finalidade |
| --- | --- | --- | --- |
| `territories.csv` | Sintético (territórios fictícios do município de Nova Aurora) | CC0 | Lista de territórios monitorados |
| `readings.csv` | Sintético, gerado por `tools/generate_synthetic_data.py` (seed 2026) | CC0 | Chuva por hora (mm/h) observada e prevista; nível do córrego (% da cota) |

- Nenhum dado pessoal. Nenhum dado real apresentado como real: todo registro tem `origin = SINTETICO`
  e o app mostra o selo "Dado sintético".
- `offset_hours` é relativo ao momento da carga (-71..0 observado, +1..+72 previsto).
- O formato imita as estações automáticas do INMET (chuva horária em mm) para permitir a troca por
  dados abertos reais (BDMEP/INMET) alterando apenas `CsvSeeder`.
- Limiares de chuva: faixas dos avisos de chuvas intensas do INMET (até 50 mm/dia; 50–100 mm/dia; > 100 mm/dia).
