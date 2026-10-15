package com.kaspercito.simpmusic.expect

enum class Orientation {
    PORTRAIT, LANDSCAPE, UNSPECIFIED
}

expect fun currentOrientation(): Orientation