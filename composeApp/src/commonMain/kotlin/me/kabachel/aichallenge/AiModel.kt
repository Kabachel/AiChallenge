package me.kabachel.aichallenge

data class AiModel(
    val name: String,
    val url: String,
    val temperatures: List<Double>
)
