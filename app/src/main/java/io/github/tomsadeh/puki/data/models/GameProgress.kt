package io.github.tomsadeh.puki.data.models

data class GameProgress(
    val totalStars: Int = 0,
    val rewardedStars: Int = 0,  // up to here her stickers have had their moment on an end card
    val albumSeenStars: Int = 0  // the stars she had when she last opened the album
)
