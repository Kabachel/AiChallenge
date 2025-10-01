package me.kabachel.aichallenge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform