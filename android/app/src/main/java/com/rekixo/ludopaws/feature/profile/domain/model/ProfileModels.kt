package com.rekixo.ludopaws.feature.profile.domain.model

enum class ProfileGameMode(
    val label: String,
) {
    CLASSIC("Classic"),
    RUSH("Rush"),
    SNAKE_LADDER("Snake & Ladder"),
}

enum class ProfileMatchSource {
    ONLINE,
    LOCAL,
    COMPUTER,
}

data class ProfileModeStats(
    val games: Int = 0,
    val wins: Int = 0,
)

data class ProfileSnapshot(
    val displayName: String = "Guest User",
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val currentDayStreak: Int = 0,
    val bestDayStreak: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val totalXp: Int = 0,
    val onlineGames: Int = 0,
    val localGames: Int = 0,
    val classic: ProfileModeStats = ProfileModeStats(),
    val rush: ProfileModeStats = ProfileModeStats(),
    val snakeLadder: ProfileModeStats = ProfileModeStats(),
    val purchasedProducts: List<String> = emptyList(),
    val characterMatches: Map<String, Int> = emptyMap(),
)

data class ProfileLevelProgress(
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val fraction: Float,
)

data class ProfileBadge(
    val id: String,
    val title: String,
    val symbol: String,
    val unlocked: Boolean,
    val requirement: String,
    val progressCurrent: Int = 0,
    val progressGoal: Int = 1,
)
