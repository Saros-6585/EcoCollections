package com.willfp.ecocollections.collections

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.willfp.eco.core.data.profile
import com.willfp.ecocollections.api.totalCollectionTiers
import com.willfp.ecocollections.plugin
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a player's rank on a per-collection leaderboard.
 */
sealed class CollectionRank {
    /**
     * Player is within the top exact-rank-cutoff. rank is 1-indexed.
     */
    data class Exact(val rank: Int) : CollectionRank()

    /**
     * Player is outside the cutoff. topPercent is "Top X%" value.
     */
    data class Percent(val topPercent: Double) : CollectionRank()

    /**
     * Player has count == 0 or is not in the snapshot.
     */
    data object Unranked : CollectionRank()
}

/**
 * A leaderboard entry for display purposes.
 */
data class LeaderboardEntry(val player: OfflinePlayer, val value: Number)

/**
 * Internal snapshot of a leaderboard at a point in time.
 */
internal data class LeaderboardSnapshot(
    val sortedEntries: List<Pair<UUID, Double>>,
    val rankMap: Map<UUID, Int>,
    val totalTrackedPlayers: Int
)

/**
 * Singleton object powering top-N placeholders and per-collection rank lookups.
 * Uses Caffeine caches with refreshAfterWrite semantics for lazy, async rebuilds.
 */
object CollectionsLeaderboard {

    /**
     * Per-collection caches, keyed by collection id.
     * Each cache stores a single LeaderboardSnapshot keyed by Boolean (true).
     */
    private val perCollectionCaches = ConcurrentHashMap<String, LoadingCache<Boolean, LeaderboardSnapshot>>()

    /**
     * Total tiers cache (global leaderboard across all collections).
     */
    private val totalsCache: LoadingCache<Boolean, LeaderboardSnapshot> by lazy {
        buildCache { buildTotalsSnapshot() }
    }

    private fun getRefreshDuration(): Duration {
        val intervalStr = plugin.configYml.getString("leaderboards.refresh-interval")
        return parseDuration(intervalStr)
    }

    private fun parseDuration(str: String): Duration {
        val trimmed = str.trim().lowercase()
        return when {
            trimmed.endsWith("h") -> Duration.ofHours(trimmed.removeSuffix("h").trim().toLongOrNull() ?: 6)
            trimmed.endsWith("m") -> Duration.ofMinutes(trimmed.removeSuffix("m").trim().toLongOrNull() ?: 360)
            trimmed.endsWith("s") -> Duration.ofSeconds(trimmed.removeSuffix("s").trim().toLongOrNull() ?: 21600)
            else -> Duration.ofSeconds(trimmed.toLongOrNull() ?: 21600)
        }
    }

    private fun buildCache(loader: () -> LeaderboardSnapshot): LoadingCache<Boolean, LeaderboardSnapshot> {
        return Caffeine.newBuilder()
            .refreshAfterWrite(getRefreshDuration())
            .executor { command -> plugin.scheduler.runAsync { command.run() } }
            .build { loader() }
    }

    private fun getOrCreateCollectionCache(collection: Collection): LoadingCache<Boolean, LeaderboardSnapshot> {
        return perCollectionCaches.computeIfAbsent(collection.id) {
            buildCache { buildCollectionSnapshot(collection) }
        }
    }

    /**
     * Build a snapshot for a specific collection by iterating all offline players.
     */
    private fun buildCollectionSnapshot(collection: Collection): LeaderboardSnapshot {
        val entries = mutableListOf<Pair<UUID, Double>>()
        for (offlinePlayer in Bukkit.getOfflinePlayers()) {
            val count = offlinePlayer.profile.read(collection.countKey)
            if (count > 0.0) {
                entries.add(offlinePlayer.uniqueId to count)
            }
        }
        entries.sortByDescending { it.second }

        val rankMap = mutableMapOf<UUID, Int>()
        for ((index, entry) in entries.withIndex()) {
            rankMap[entry.first] = index + 1
        }

        return LeaderboardSnapshot(
            sortedEntries = entries,
            rankMap = rankMap,
            totalTrackedPlayers = entries.size
        )
    }

    /**
     * Build a snapshot for total tiers across all collections.
     */
    private fun buildTotalsSnapshot(): LeaderboardSnapshot {
        val entries = mutableListOf<Pair<UUID, Double>>()
        for (offlinePlayer in Bukkit.getOfflinePlayers()) {
            val totalTiers = offlinePlayer.totalCollectionTiers
            if (totalTiers > 0) {
                entries.add(offlinePlayer.uniqueId to totalTiers.toDouble())
            }
        }
        entries.sortByDescending { it.second }

        val rankMap = mutableMapOf<UUID, Int>()
        for ((index, entry) in entries.withIndex()) {
            rankMap[entry.first] = index + 1
        }

        return LeaderboardSnapshot(
            sortedEntries = entries,
            rankMap = rankMap,
            totalTrackedPlayers = entries.size
        )
    }

    // ==================== Public API ====================

    /**
     * Get the rank of a player for a specific collection.
     */
    fun OfflinePlayer.getCollectionRank(collection: Collection): CollectionRank {
        val snapshot = try {
            getOrCreateCollectionCache(collection).get(true)
        } catch (e: Exception) {
            return CollectionRank.Unranked
        }

        val rank = snapshot.rankMap[this.uniqueId] ?: return CollectionRank.Unranked

        val cutoff = plugin.configYml.getInt("leaderboards.exact-rank-cutoff")

        // cutoff == 0 means always return exact rank
        if (cutoff == 0 || rank <= cutoff) {
            return CollectionRank.Exact(rank)
        }

        if (snapshot.totalTrackedPlayers == 0) {
            return CollectionRank.Unranked
        }

        val decimalPlaces = plugin.configYml.getInt("leaderboards.percent-decimal-places")
        val percent = (rank.toDouble() / snapshot.totalTrackedPlayers.toDouble()) * 100.0
        val factor = Math.pow(10.0, decimalPlaces.toDouble())
        val roundedPercent = Math.round(percent * factor) / factor

        return CollectionRank.Percent(roundedPercent)
    }

    /**
     * Get the top N players for a specific collection.
     * n is clamped to 1..10.
     */
    fun Collection.getTopPlayers(n: Int): List<LeaderboardEntry> {
        val clampedN = n.coerceIn(1, 10)
        val snapshot = try {
            getOrCreateCollectionCache(this).get(true)
        } catch (e: Exception) {
            return emptyList()
        }

        return snapshot.sortedEntries.take(clampedN).map { (uuid, count) ->
            LeaderboardEntry(
                player = Bukkit.getOfflinePlayer(uuid),
                value = count
            )
        }
    }

    /**
     * Get the top player by total tiers at a given position (1-indexed).
     */
    fun getTopByTotal(position: Int): LeaderboardEntry? {
        val snapshot = try {
            totalsCache.get(true)
        } catch (e: Exception) {
            return null
        }

        val index = position - 1
        if (index < 0 || index >= snapshot.sortedEntries.size) return null

        val (uuid, value) = snapshot.sortedEntries[index]
        return LeaderboardEntry(
            player = Bukkit.getOfflinePlayer(uuid),
            value = value.toInt()
        )
    }

    /**
     * Get the position of a player by total tiers (1-indexed).
     * Returns null if not on the leaderboard.
     */
    fun getPositionByTotal(uuid: UUID): Int? {
        val snapshot = try {
            totalsCache.get(true)
        } catch (e: Exception) {
            return null
        }

        return snapshot.rankMap[uuid]
    }

    /**
     * Get the top player for a specific collection at a given position (1-indexed).
     */
    fun getTop(collection: Collection, position: Int): LeaderboardEntry? {
        val snapshot = try {
            getOrCreateCollectionCache(collection).get(true)
        } catch (e: Exception) {
            return null
        }

        val index = position - 1
        if (index < 0 || index >= snapshot.sortedEntries.size) return null

        val (uuid, value) = snapshot.sortedEntries[index]
        return LeaderboardEntry(
            player = Bukkit.getOfflinePlayer(uuid),
            value = value
        )
    }

    /**
     * Get the position of a player for a specific collection (1-indexed).
     * Returns null if not on the leaderboard.
     */
    fun getPosition(collection: Collection, uuid: UUID): Int? {
        val snapshot = try {
            getOrCreateCollectionCache(collection).get(true)
        } catch (e: Exception) {
            return null
        }

        return snapshot.rankMap[uuid]
    }

    /**
     * Invalidate all leaderboard caches.
     */
    fun invalidateAll() {
        perCollectionCaches.values.forEach { it.invalidateAll() }
        perCollectionCaches.clear()
        totalsCache.invalidateAll()
    }

    /**
     * Invalidate leaderboard cache for a specific collection.
     */
    fun invalidate(collection: Collection) {
        perCollectionCaches[collection.id]?.invalidateAll()
        perCollectionCaches.remove(collection.id)
        totalsCache.invalidateAll()
    }
}
