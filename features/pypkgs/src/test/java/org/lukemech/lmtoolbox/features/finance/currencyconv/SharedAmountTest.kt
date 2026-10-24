package org.lukemech.lmtoolbox.features.finance.currencyconv

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedAmountTest {
    @Test
    fun `codes after or before the amount`() {
        assertEquals(SharedAmount("5", listOf("USD")), parseSharedAmount("5 USD"))
        assertEquals(SharedAmount("5", listOf("USD")), parseSharedAmount("usd 5"))
    }

    @Test
    fun symbols() {
        assertEquals(SharedAmount("12.50", listOf("EUR")), parseSharedAmount("€12,50"))
        assertEquals(SharedAmount("5", listOf("PLN")), parseSharedAmount("5 zł"))
        assertEquals(SharedAmount("3", listOf("GBP")), parseSharedAmount("£3"))
        assertEquals(SharedAmount("20", listOf("BRL")), parseSharedAmount("R$ 20"))
    }

    @Test
    fun `two currencies in order`() {
        assertEquals(SharedAmount("100", listOf("USD", "EUR")), parseSharedAmount("100 USD to EUR"))
        assertEquals(SharedAmount("100", listOf("PLN", "EUR")), parseSharedAmount("100 zł w euro EUR"))
    }

    @Test
    fun `grouping and decimals`() {
        assertEquals("1234.56", parseSharedAmount("1 234,56 zł").amount)
        assertEquals("1234.56", parseSharedAmount("$1,234.56").amount)
        assertEquals("1234.56", parseSharedAmount("1.234,56 €").amount)
        assertEquals("1500", parseSharedAmount("1.500 zł").amount)
        assertEquals("0.500", parseSharedAmount("0,500 EUR").amount)
        assertEquals("1000000", parseSharedAmount("1,000,000 USD").amount)
        assertEquals("12.5", parseSharedAmount("12.5").amount)
    }

    @Test
    fun `nothing to find`() {
        assertEquals(SharedAmount(null, emptyList()), parseSharedAmount("…"))
    }
}
