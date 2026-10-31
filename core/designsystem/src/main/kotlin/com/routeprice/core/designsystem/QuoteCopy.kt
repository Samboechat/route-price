package com.routeprice.core.designsystem

import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.LineKind
import com.routeprice.core.domain.model.PriceLine
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingWarning
import com.routeprice.core.domain.model.QuantityUnit
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RoundingRule
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.VehicleType
import com.routeprice.core.domain.util.BrFormat

/**
 * Textos do orçamento em um só lugar, usados na tela, no PDF e na imagem compartilhada.
 * Linguagem simples, pensada para o passageiro.
 */
object QuoteCopy {

    fun lineLabel(kind: LineKind): String = when (kind) {
        LineKind.DISTANCE -> "Distância percorrida"
        LineKind.TIME -> "Tempo de viagem"
        LineKind.STOPS -> "Paradas"
        LineKind.WAITING -> "Tempo de espera"
        LineKind.COMMERCIAL_ADJUSTMENT -> "Ajuste comercial"
        LineKind.MINIMUM_FARE_COMPLEMENT -> "Complemento da tarifa mínima"
        LineKind.ENERGY -> "Combustível"
        LineKind.MAINTENANCE -> "Manutenção"
        LineKind.WEAR -> "Desgaste e depreciação"
        LineKind.DRIVER_TIME -> "Tempo do motorista"
        LineKind.PROFIT_MARGIN -> "Margem do serviço"
        LineKind.TOLLS -> "Pedágios"
        LineKind.EXTRA_EXPENSES -> "Outras despesas"
        LineKind.SUSTAINABLE_COMPLEMENT -> "Complemento de custo mínimo"
        LineKind.ROUNDING -> "Arredondamento"
    }

    /** Explicação para a seção "Entenda o valor da sua viagem". */
    fun lineExplanation(kind: LineKind): String = when (kind) {
        LineKind.DISTANCE -> "Valor por quilômetro definido pelo motorista, aplicado à distância real da rota (não em linha reta)."
        LineKind.TIME -> "Valor por minuto, porque o tempo no trânsito também é trabalho."
        LineKind.STOPS -> "Taxa fixa por parada intermediária pedida no trajeto."
        LineKind.WAITING -> "Espera além da tolerância gratuita combinada."
        LineKind.COMMERCIAL_ADJUSTMENT -> "Ajuste definido pelo motorista para o horário, demanda ou tipo de serviço."
        LineKind.MINIMUM_FARE_COMPLEMENT -> "Viagens curtas têm um valor mínimo para cobrir o deslocamento."
        LineKind.ENERGY -> "Estimativa do combustível gasto, pelo consumo médio do veículo e o preço informado."
        LineKind.MAINTENANCE -> "Estimativa de revisões, óleo e peças proporcionais à distância."
        LineKind.WEAR -> "Estimativa de pneus e depreciação do veículo por quilômetro."
        LineKind.DRIVER_TIME -> "Valor da hora de trabalho do motorista durante a viagem."
        LineKind.PROFIT_MARGIN -> "Margem que mantém o serviço sustentável para o motorista."
        LineKind.TOLLS -> "Repasse dos pedágios do trajeto, sem acréscimo."
        LineKind.EXTRA_EXPENSES -> "Despesas da viagem informadas pelo motorista, como estacionamento."
        LineKind.SUSTAINABLE_COMPLEMENT -> "Garante que o valor cubra os custos reais da viagem."
        LineKind.ROUNDING -> "Arredondamento para um valor fácil de pagar."
    }

    fun lineDetail(line: PriceLine): String? {
        val q = line.quantity ?: return null
        val qty = when (line.unit) {
            QuantityUnit.KM -> BrFormat.decimal(q, 1) + " km"
            QuantityUnit.MINUTE -> BrFormat.decimal(q, 1) + " min"
            QuantityUnit.HOUR -> BrFormat.decimal(q, 2) + " h"
            QuantityUnit.STOP -> BrFormat.decimal(q, 0) + if (q.toInt() == 1) " parada" else " paradas"
            QuantityUnit.ENERGY_UNIT -> BrFormat.decimal(q, 2) + " un."
            QuantityUnit.PERCENT -> return BrFormat.decimal(q, 1) + "%"
            null -> return null
        }
        val rate = line.rate ?: return qty
        return "$qty × ${BrFormat.rate(rate)}"
    }

    fun sourceLabel(source: ValueSource): String = when (source) {
        ValueSource.EXTERNAL -> "Confirmado"
        ValueSource.DRIVER_INFORMED -> "Informado"
        ValueSource.ESTIMATED -> "Estimado"
    }

    fun sourceExplanation(source: ValueSource): String = when (source) {
        ValueSource.EXTERNAL -> "Dado de fonte externa (rota e pedágios do Google Maps Platform)."
        ValueSource.DRIVER_INFORMED -> "Valor definido ou informado pelo motorista."
        ValueSource.ESTIMATED -> "Estimativa calculada pelo app a partir dos dados da viagem."
    }

    fun routeSourceLabel(source: ValueSource): String = when (source) {
        ValueSource.EXTERNAL -> "Distância e tempo calculados pelo Google Routes"
        else -> "Distância e tempo informados pelo motorista"
    }

    fun modelLabel(model: PricingModel): String = when (model) {
        PricingModel.COMMERCIAL -> "Tarifa comercial"
        PricingModel.OPERATING_COST -> "Custos + margem"
    }

    fun modelDescription(model: PricingModel): String = when (model) {
        PricingModel.COMMERCIAL -> "Valor por km, por minuto, paradas, espera e tarifa mínima."
        PricingModel.OPERATING_COST -> "Combustível, manutenção, desgaste, tempo e margem de lucro."
    }

    fun warning(w: PricingWarning): String = when (w) {
        PricingWarning.NO_VEHICLE -> "Nenhum veículo selecionado: combustível e manutenção não entraram no custo mínimo."
        PricingWarning.MISSING_ENERGY_PRICE -> "Informe o preço do combustível nas configurações para estimar o custo real."
        PricingWarning.MISSING_CONSUMPTION -> "O veículo está sem consumo médio cadastrado."
        PricingWarning.MARGIN_CLAMPED -> "A margem foi limitada a 90%."
        PricingWarning.COMMERCIAL_BELOW_SUSTAINABLE ->
            "A tarifa comercial ficou abaixo dos seus custos. O valor foi elevado até o mínimo sustentável."
        PricingWarning.TOLLS_PRICE_UNKNOWN -> "A rota tem pedágio sem valor informado pela fonte. Digite o valor para incluí-lo."
        PricingWarning.ZERO_DISTANCE -> "Distância zero: apenas a tarifa mínima foi aplicada."
        PricingWarning.PARAMETERS_NOT_REVIEWED ->
            "Você ainda usa os parâmetros iniciais de exemplo. Revise suas tarifas e custos antes de enviar orçamentos."
    }

    fun status(s: QuoteStatus): String = when (s) {
        QuoteStatus.DRAFT -> "Rascunho"
        QuoteStatus.SENT -> "Enviado"
        QuoteStatus.ACCEPTED -> "Aceito"
        QuoteStatus.DECLINED -> "Recusado"
    }

    fun vehicleType(t: VehicleType): String = when (t) {
        VehicleType.HATCH -> "Hatch"
        VehicleType.SEDAN -> "Sedã"
        VehicleType.SUV -> "SUV"
        VehicleType.MINIVAN -> "Minivan"
        VehicleType.VAN -> "Van"
        VehicleType.PICKUP -> "Picape"
    }

    fun fuel(f: FuelType): String = when (f) {
        FuelType.GASOLINE -> "Gasolina"
        FuelType.ETHANOL -> "Etanol"
        FuelType.DIESEL -> "Diesel"
        FuelType.CNG -> "GNV"
        FuelType.ELECTRIC -> "Elétrico"
    }

    fun consumptionUnit(f: FuelType): String = "km/${f.unit}"

    fun rounding(r: RoundingRule): String = when (r) {
        RoundingRule.NONE -> "Sem arredondamento"
        RoundingRule.UP_TO_50_CENTS -> "Para cima, R$ 0,50"
        RoundingRule.UP_TO_1_REAL -> "Para cima, R$ 1"
        RoundingRule.UP_TO_5_REAIS -> "Para cima, R$ 5"
    }
}
