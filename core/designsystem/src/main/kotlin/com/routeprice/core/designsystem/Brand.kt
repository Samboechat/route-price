package com.routeprice.core.designsystem

/**
 * Configuração de marca em um único lugar (nome provisório "Route Price").
 * Mantenha em sincronia com res/values/brand.xml deste módulo.
 */
object Brand {
    const val NAME = "Route Price"
    const val WORDMARK_PRIMARY = "ROUTE"
    const val WORDMARK_SECONDARY = "PRICE"
    const val TAGLINE = "Orçamentos profissionais para viagens particulares"
    const val SHARE_SIGNATURE = "Orçamento gerado com Route Price"

    /** Cor de destaque da marca (ARGB) — também usada nos documentos PDF e imagem. */
    const val ACCENT_ARGB = 0xFFC6F45A.toInt()
    const val INK_ARGB = 0xFF0A0B0D.toInt()
}
