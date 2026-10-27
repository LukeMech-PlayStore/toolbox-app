package org.lukemech.lmtoolbox.features.reference.common

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.FeatureRegistry

/** Every reference's data as it's built: tables whole, pinouts with each pin in a place of its own. */
class ReferenceDataTest {
    private val references = FeatureRegistry.features.filterIsInstance<ReferenceFeature>()
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun everyReferenceIsInItsCategory() {
        assertTrue(references.isNotEmpty())
        assertEquals(FeatureRegistry.features.filter { it.category == FeatureCategory.Reference }, references)
    }

    @Test
    fun tablesHaveACellUnderEveryHeading() {
        references.forEach { feature ->
            feature.sheets.filterIsInstance<TableSheet>().forEach { table ->
                val name = "${feature.route}/${table.name.resolve(resources)}"
                assertTrue("$name: no rows", table.rows.isNotEmpty())
                table.rows.forEachIndexed { i, row -> assertEquals("$name, row $i", table.columns.size, row.size) }
                // Every cell's text there, in this language too
                table.rows.flatten().forEach { it.resolve(resources) }
            }
        }
    }

    @Test
    fun pinsHaveTheirOwnPlaces() {
        references.forEach { feature ->
            feature.sheets.filterIsInstance<PinoutSheet>().forEach { pinout ->
                val name = "${feature.route}/${pinout.name.resolve(resources)}"
                assertTrue("$name: no pins", pinout.pins.isNotEmpty())
                val places = pinout.pins.map { it.x to it.y }
                assertEquals("$name: two pins drawn in one place", places.size, places.toSet().size)
                pinout.pins.forEach { it.description?.resolve(resources) }
            }
        }
    }

    @Test
    fun numberedPinsInOrder() {
        // Where the pins are numbered 1, 2, 3..., the list has each once, in order
        references.flatMap { f -> f.sheets.filterIsInstance<PinoutSheet>().map { f.route to it } }.forEach { (route, pinout) ->
            val numbers = pinout.pins.map { it.number.toIntOrNull() }
            if (numbers.any { it == null }) return@forEach
            assertEquals("$route/${pinout.name.resolve(resources)}", (1..numbers.size).toList(), numbers)
        }
    }

    @Test
    fun wellKnownPins() {
        fun pin(feature: ReferenceFeature, sheet: Int, number: String) =
            (feature.sheets[sheet] as PinoutSheet).pins.first { it.number == number }
        val rpi = org.lukemech.lmtoolbox.features.reference.rpi_gpio.RpiGpioFeature
        assertEquals("GPIO14", pin(rpi, 0, "8").name)
        assertEquals("GND", pin(rpi, 0, "39").name)
        val rs232 = org.lukemech.lmtoolbox.features.reference.rs232.Rs232Feature
        assertEquals("TXD", pin(rs232, 0, "3").name)
        assertEquals("GND", pin(rs232, 0, "5").name)
        assertEquals("TXD", pin(rs232, 1, "2").name)
        assertEquals("DTR", pin(rs232, 1, "20").name)
        val usb = org.lukemech.lmtoolbox.features.reference.usb.UsbFeature
        assertEquals("CC1", pin(usb, 7, "A5").name)
        // USB-C: B1 under A12
        assertEquals(pin(usb, 7, "A12").x, pin(usb, 7, "B1").x)
        assertEquals("SSRX−", pin(usb, 1, "5").name)
        assertEquals("SSTX−", pin(usb, 3, "5").name)
        assertEquals("ID", pin(usb, 6, "4").name)
        val display = org.lukemech.lmtoolbox.features.reference.display.DisplayFeature
        assertEquals("HPD", pin(display, 0, "19").name)
        assertEquals("HPD", pin(display, 1, "19").name)
        assertEquals("HPD", pin(display, 2, "1").name)
        assertEquals("DP_PWR", pin(display, 3, "20").name)
        assertEquals("DP_PWR", pin(display, 4, "20").name)
        assertEquals("Analog GND", pin(display, 5, "C5").name)
        assertEquals("H-Sync", pin(display, 6, "13").name)
        val atx = org.lukemech.lmtoolbox.features.reference.pc_power.PcPowerFeature
        assertEquals("PS_ON#", pin(atx, 0, "16").name)
        assertEquals(pin(atx, 0, "1").x, pin(atx, 0, "13").x)
        assertEquals("SENSE0", pin(atx, 4, "S3").name)
        assertEquals("DAS/DSS", pin(atx, 6, "11").name)
    }
}
