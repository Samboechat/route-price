package com.routeprice.core.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Valor monetário em centavos de real. Toda soma de preços acontece em centavos inteiros,
 * o que elimina erros de ponto flutuante na composição do orçamento.
 */
@Serializable
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    val isZero: Boolean get() = cents == 0L

    val isPositive: Boolean get() = cents > 0L

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(cents, 2)

    companion object {
        val ZERO = Money(0)

        /** Converte para centavos com arredondamento financeiro (meio para cima). */
        fun of(value: BigDecimal): Money =
            Money(value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())

        fun reais(value: String): Money = of(BigDecimal(value))

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO) { acc, m -> acc + m }
    }
}

fun maxOf(a: Money, b: Money): Money = if (a >= b) a else b

/** Serializa BigDecimal como texto para não perder precisão no JSON. */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.routeprice.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}
