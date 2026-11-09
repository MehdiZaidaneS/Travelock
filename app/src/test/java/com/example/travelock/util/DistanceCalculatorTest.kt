package com.example.travelock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DistanceCalculatorTest {
    @Test
    fun sameCoordinatesHaveZeroDistance() {
        assertEquals(0.0, DistanceCalculator.distanceInMeters(60.1738, 24.9381, 60.1738, 24.9381), 0.0)
    }

    @Test
    fun oneDegreeAlongEquatorIsAbout111195Meters() {
        assertEquals(111_195.0, DistanceCalculator.distanceInMeters(0.0, 0.0, 0.0, 1.0), 1.0)
    }

    @Test
    fun londonToNewYorkIsAbout5570KilometersInEitherDirection() {
        val forward = DistanceCalculator.distanceInMeters(51.5074, -0.1278, 40.7128, -74.0060)
        val reverse = DistanceCalculator.distanceInMeters(40.7128, -74.0060, 51.5074, -0.1278)
        assertEquals(5_570_000.0, forward, 10_000.0)
        assertEquals(forward, reverse, 0.001)
    }

    @Test
    fun crossingDateLineTakesTheShortDistance() {
        assertEquals(222_390.0, DistanceCalculator.distanceInMeters(0.0, 179.0, 0.0, -179.0), 2.0)
    }

    @Test
    fun oppositePointsReturnAFiniteHalfCircumference() {
        assertEquals(20_015_114.0, DistanceCalculator.distanceInMeters(0.0, 0.0, 0.0, 180.0), 2.0)
    }

    @Test
    fun rejectsInvalidOrUnavailableCoordinates() {
        for (latitude in listOf(-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                DistanceCalculator.distanceInMeters(latitude, 0.0, 0.0, 0.0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                DistanceCalculator.distanceInMeters(0.0, 0.0, latitude, 0.0)
            }
        }
        for (longitude in listOf(-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                DistanceCalculator.distanceInMeters(0.0, longitude, 0.0, 0.0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                DistanceCalculator.distanceInMeters(0.0, 0.0, 0.0, longitude)
            }
        }
    }
}
