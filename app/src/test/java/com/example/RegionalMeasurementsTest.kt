package com.example

import com.example.util.RegionalMeasurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionalMeasurementsTest {
    @Test fun explicitUnitsPreserveLegacyAndSeparateDimensions() {
        assertEquals(240.0, RegionalMeasurements.convert(1.0, "cup", "ml")!!, 1e-9)
        assertEquals(236.5882365, RegionalMeasurements.convert(1.0, "cup_us", "ml")!!, 1e-9)
        assertEquals(250.0, RegionalMeasurements.convert(1.0, "cup_metric", "ml")!!, 1e-9)
        assertEquals(1.0, RegionalMeasurements.convert(16.0, "oz", "lb")!!, 1e-9)
        assertEquals(20.0, RegionalMeasurements.convert(1.0, "tbsp_au", "ml")!!, 1e-9)
        assertEquals(28.4130625, RegionalMeasurements.convert(1.0, "fl_oz_uk", "ml")!!, 1e-9)
        assertNull(RegionalMeasurements.convert(1.0, "cup_us", "g"))
        assertNull(RegionalMeasurements.convert(1.0, "oz", "fl_oz_us"))
        assertNull(RegionalMeasurements.convert(-1.0, "g", "kg"))
    }

    @Test fun temperaturesConvertBothDirections() {
        assertEquals(356.0, RegionalMeasurements.convertTemperature(180.0, "C", "F")!!, 1e-9)
        assertEquals(0.0, RegionalMeasurements.convertTemperature(32.0, "F", "C")!!, 1e-9)
        assertNull(RegionalMeasurements.convertTemperature(Double.NaN, "C", "F"))
    }
}
