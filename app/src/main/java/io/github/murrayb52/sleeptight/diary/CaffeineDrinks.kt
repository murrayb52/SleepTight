package io.github.murrayb52.sleeptight.diary

import io.github.murrayb52.sleeptight.data.CaffeineEntry
import io.github.murrayb52.sleeptight.data.CoffeeVenue
import io.github.murrayb52.sleeptight.data.DrinkType
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.CaffeineDose
import java.time.Instant

/** The drinks the Add sheet offers. Decaf is coffee with the decaf flag set. */
enum class DrinkChoice { COFFEE, DECAF, COKE, COKE_ZERO }

/** Soft-drink sizes on offer, in ml. */
val SOFT_DRINK_SIZES = listOf(250, 500, 1000)

/**
 * A drink as logged, with its caffeine resolved from the defaults in [settings] (v1 plan spec 8).
 * Coffee uses [venue] and [shots]; soft drinks use [volumeMl].
 */
fun caffeineEntry(
    drink: DrinkChoice,
    at: Instant,
    settings: Settings,
    venue: CoffeeVenue = CoffeeVenue.HOME,
    shots: Int = 1,
    volumeMl: Int = 250,
): CaffeineEntry = when (drink) {
    DrinkChoice.COFFEE, DrinkChoice.DECAF -> {
        val decaf = drink == DrinkChoice.DECAF
        val perShot = when {
            decaf -> settings.decafShotMg
            venue == CoffeeVenue.HOME -> settings.espressoHomeMg
            else -> settings.espressoRestaurantMg
        }
        CaffeineEntry(timestamp = at.toEpochMilli(), drinkType = DrinkType.COFFEE, decaf = decaf, venue = venue, shots = shots, mg = perShot * shots)
    }
    DrinkChoice.COKE, DrinkChoice.COKE_ZERO -> CaffeineEntry(
        timestamp = at.toEpochMilli(),
        drinkType = if (drink == DrinkChoice.COKE) DrinkType.COKE else DrinkType.COKE_ZERO,
        volumeMl = volumeMl,
        mg = settings.colaMgPer100Ml * volumeMl / 100.0,
    )
}

fun CaffeineEntry.toDose() = CaffeineDose(Instant.ofEpochMilli(timestamp), mg)
