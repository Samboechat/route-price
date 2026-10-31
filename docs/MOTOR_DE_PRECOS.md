# Motor de preços

Código: `core/domain/src/main/kotlin/com/routeprice/core/domain/pricing/PricingEngine.kt`
Testes: `core/domain/src/test/.../PricingEngineTest.kt` (valores conferidos à mão, exemplos abaixo).

O motor é Kotlin puro, sem Android e sem interface. Recebe a viagem (`TripPricingInput`),
os parâmetros (`PricingSettings`) e o veículo, e devolve um `PricingResult` com os dois modelos,
o preço mínimo sustentável, as linhas do preço sugerido e os avisos.

## Regras gerais

| Regra | Por quê |
|---|---|
| Dinheiro em **centavos inteiros** (`Money`) | Sem erro de ponto flutuante. |
| Cálculos intermediários em `BigDecimal` (64 bits decimais), **uma divisão só no fim** de cada linha | Ex.: 12,5 km ÷ 6 km/kWh × R$ 0,90 = 1,875 exato, e não 1,8749… |
| Cada linha é **arredondada para centavos, meio para cima** (`HALF_UP`) | Padrão financeiro brasileiro. |
| O **total é a soma das linhas** já arredondadas | A composição mostrada ao passageiro sempre fecha com o valor final. |
| **Pedágios e despesas extras são repasse**: entram uma vez, no fim, sem ajuste nem margem | Evita cobrar lucro sobre pedágio e evita contá-lo duas vezes. |
| Combustível, manutenção e desgaste **só existem no Modelo B** | A tarifa por km do Modelo A já remunera esses custos; os modelos nunca são somados. |
| Deslocamento sem passageiro (ida ao embarque/retorno) **só entra nos custos** | É custo do motorista, não distância percorrida com o passageiro. |
| Distância e tempo vêm da **rota percorrida** (Google Routes) ou do motorista | Nunca distância em linha reta. |

Notação: `km = metros ÷ 1000`, `min = segundos ÷ 60`, `round(x)` = arredondamento para centavos meio para cima.

## Modelo A — Tarifa comercial

```
mult        = multiplicador do veículo (padrão 1)
DISTÂNCIA   = round(km × tarifa_km × mult)
TEMPO       = round(segundos × tarifa_min × mult ÷ 60)
PARADAS     = round(nº_paradas × taxa_parada)                       (se houver paradas)
ESPERA      = round(max(0, espera_min − espera_grátis) × tarifa_espera)
serviço     = DISTÂNCIA + TEMPO + PARADAS + ESPERA
AJUSTE      = round(serviço × ajuste% ÷ 100)                        (ajuste limitado a −90% … +300%)
COMPLEMENTO_TARIFA_MÍNIMA = max(0, tarifa_mínima − (serviço + AJUSTE))
total_A     = serviço + AJUSTE + COMPLEMENTO + PEDÁGIOS + DESPESAS
```

## Modelo B — Custos operacionais + margem

```
km_total    = km + km_deslocamento_vazio
seg_total   = segundos + 60 × (espera_min + min_deslocamento_vazio)
COMBUSTÍVEL = round(km_total × preço_energia ÷ consumo_km_por_unidade)
MANUTENÇÃO  = round(km_total × manutenção_por_km)
DESGASTE    = round(km_total × desgaste_por_km)
TEMPO_MOT.  = round(seg_total × valor_hora ÷ 3600)
custos      = COMBUSTÍVEL + MANUTENÇÃO + DESGASTE + TEMPO_MOT.
m           = margem% ÷ 100                                          (limitada a 0 … 90%)
MARGEM      = round(custos ÷ (1 − m)) − custos
total_B     = custos + MARGEM + PEDÁGIOS + DESPESAS
```

A margem é **sobre o preço** (lucro = m% do preço do serviço), não um acréscimo sobre o custo.
Com 20%: custo 23,13 → preço 28,91 → lucro 5,78 = 20% de 28,91.

## Os três preços

| Preço | Definição |
|---|---|
| **Mínimo sustentável** | `custos + PEDÁGIOS + DESPESAS` do Modelo B, com margem zero. Abaixo disso o motorista paga para trabalhar. |
| **Sugerido** | Total do modelo escolhido nas configurações. Se ficar abaixo do mínimo sustentável, recebe a linha *Complemento de custo mínimo* e o aviso `COMMERCIAL_BELOW_SUSTAINABLE`. Depois aplica o arredondamento comercial (para cima: nenhum, R$ 0,50, R$ 1 ou R$ 5) como linha própria. |
| **Final negociado** | Opcional, digitado pelo motorista. O orçamento guarda o calculado e o negociado; a diferença (R$ e %) aparece na tela e entra na visão do passageiro como *Desconto combinado* / *Ajuste combinado*. Abaixo do mínimo sustentável a tela alerta. |

## Origem de cada número

Cada linha carrega um `ValueSource`, mostrado ao passageiro:

- **Confirmado** (`EXTERNAL`): veio de fonte externa — pedágio estimado pelo Google Routes.
- **Informado** (`DRIVER_INFORMED`): tarifa, taxa, margem, valor da hora, despesas ou pedágio digitado pelo motorista.
- **Estimado** (`ESTIMATED`): calculado pelo app (combustível, manutenção, desgaste, complemento).

Distância e tempo têm a origem indicada separadamente ("calculados pelo Google Routes" ou "informados pelo motorista").
Pedágio sem valor na fonte nunca é inventado: aparece como "há pedágio, valor não informado" e gera o aviso `TOLLS_PRICE_UNKNOWN`.

## Exemplos conferidos (iguais aos testes)

Veículo: 10 km/L, manutenção R$ 0,15/km, desgaste R$ 0,10/km. Gasolina R$ 6,00. Hora R$ 30. Margem 20%.
Tarifas: R$ 2,50/km, R$ 0,40/min, R$ 5/parada, espera R$ 0,50/min após 5 min, mínima R$ 25.

**Viagem simples — 12,5 km, 25 min**

| Linha | Cálculo | Valor |
|---|---|---|
| Distância | 12,5 × 2,50 | 31,25 |
| Tempo | 25 × 0,40 | 10,00 |
| **Total A** | | **41,25** |
| Combustível | 12,5 ÷ 10 × 6,00 | 7,50 |
| Manutenção | 12,5 × 0,15 = 1,875 | 1,88 |
| Desgaste | 12,5 × 0,10 | 1,25 |
| Tempo do motorista | 25/60 × 30 | 12,50 |
| **Mínimo sustentável** | | **23,13** |
| Margem | 23,13 ÷ 0,8 = 28,9125 → 28,91 − 23,13 | 5,78 |
| **Total B** | | **28,91** |
| Sugerido (A, arredondado p/ cima R$ 1) | 41,25 + 0,75 | **42,00** |

**Paradas, espera, pedágio e despesas — 40 km, 60 min, 3 paradas, 12 min de espera, ajuste +10%, pedágio R$ 12,40, despesas R$ 8,00**

100,00 + 24,00 + 15,00 + 3,50 (7 min × 0,50) = 142,50 → ajuste 14,25 → 156,75 + 12,40 + 8,00 = **177,15** → arredondado **178,00**.

**Tarifa abaixo do custo — 5 km, 20 min, tarifas R$ 1,00/km e R$ 0,10/min, mínima R$ 10**

Comercial: 7,00 → tarifa mínima → 10,00. Custos: 3,00 + 0,75 + 0,50 + 10,00 = 14,25.
Sugerido sobe para **14,25** com a linha *Complemento de custo mínimo* (4,25) e o aviso.

Outros cenários testados: troca de combustível e de consumo, margem 0 e acima de 90%, espera dentro da tolerância,
deslocamento vazio, multiplicador de veículo, ajuste negativo, minutos fracionados, veículo elétrico, ausência de
veículo/preço de combustível, distância zero e uma varredura que confere, para todos os modelos e regras de
arredondamento, que a soma das linhas é o total e que o sugerido nunca fica abaixo do mínimo sustentável.
