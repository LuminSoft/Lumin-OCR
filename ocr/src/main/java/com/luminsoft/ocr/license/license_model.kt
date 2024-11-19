package com.luminsoft.ocr.license


data class Contract(
    val customer: String,
    val expiration: Expiration,
    val id: String,
    val eNROLL: ENROLL
)

data class Expiration(
    val day: Int,
    val month: Int,
    val year: Int
)

data class ENROLL(
    val mobile: Mobile
)

data class Mobile(
    val face: Feature,
    val document: Feature
)

data class Feature(
    val enabled: Boolean
)

data class ContractModel(
    val contract: Contract,
    val contractSignature: String
)