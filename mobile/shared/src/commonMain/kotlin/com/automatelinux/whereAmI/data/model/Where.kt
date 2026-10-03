package com.automatelinux.whereAmI.data.model

/** One answer from GET /api/where — mirrors the backend's JSON (lib/where.ts). */
data class Where(
    val address: Address,
    val neighborhoods: List<String>,
    val landmarks: List<String>,
    val industrial: Boolean?,
    val parkingArea: Int?,
    val locality: Locality?,
    val cello: Cello,
)

data class Address(
    val street: String?,
    val houseNumber: String?,
    val neighborhood: String?,
    val quarter: String?,
    val city: String?,
    val subdistrict: String?,
    val district: String?,
    val postcode: String?,
    val place: String?,
)

/** The population registry's row for the town (data.gov.il). */
data class Locality(
    val name: String,
    val population: Int,
    val subdistrict: String?,
    val regionalCouncil: String?,
    val ages: List<AgeBand>,
    val source: String,
)

data class AgeBand(val label: String, val count: Int)

data class Zone(val id: Int, val name: String, val reason: String?)

/** What to pick in Cello here. */
sealed interface Cello {
    /** Cello does not sell parking in this town. */
    data class NoCity(val message: String) : Cello

    /** Exactly one zone fits. */
    data class One(val cityId: Int, val city: String, val zone: Zone, val others: List<Zone>) : Cello

    /** Several fit — the street sign decides. */
    data class Several(val cityId: Int, val city: String, val candidates: List<Zone>, val message: String, val others: List<Zone>) : Cello

    /** None of the zone names says anything about this spot. */
    data class None(val cityId: Int, val city: String, val message: String, val others: List<Zone>) : Cello
}

data class ParkingSession(
    val transactionId: Long,
    val zoneName: String?,
    val startedAt: String?,
    val amount: Double?,
)

/** A street (or address) from search, with the neighbourhoods it runs through. */
data class StreetMatch(
    val label: String,
    val city: String?,
    val neighborhoods: List<StreetPlace>,
    val lat: Double,
    val lon: Double,
)

data class StreetPlace(val name: String?, val lat: Double, val lon: Double)
