package xyz.crazyh.kscale3

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform