@file:JvmName("CollectionAPI")

package com.willfp.ecocollections.api

import com.willfp.eco.core.data.profile
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.toNumeral
import com.willfp.ecocollections.api.event.PlayerCollectionCompleteEvent
import com.willfp.ecocollections.api.event.PlayerCollectionTierUpEvent
import com.willfp.ecocollections.api.event.PlayerCollectionUnlockEvent
import com.willfp.ecocollections.collections.Collection
import com.willfp.ecocollections.collections.Collections
import com.willfp.ecocollections.plugin
import com.willfp.ecocollections.libreforge.trigger.TriggerCollectionComplete
import com.willfp.ecocollections.libreforge.trigger.TriggerCollectionTierUp
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.DispatchedTrigger
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/**
 * Get the current count for a collection.
 */
fun OfflinePlayer.getCollectionCount(collection: Collection): Double {
    return this.profile.read(collection.countKey)
}

/**
 * Get the current tier for a collection.
 */
fun OfflinePlayer.getCollectionTier(collection: Collection): Int {
    return this.profile.read(collection.tierKey)
}

/**
 * Check if a collection is completed (max tier reached and doneKey set).
 */
fun OfflinePlayer.isCollectionComplete(collection: Collection): Boolean {
    return this.profile.read(collection.doneKey)
}

/**
 * Check if a collection is unlocked.
 * Returns true if the collection has no unlock conditions, or if the unlocked PDC flag is set.
 */
fun OfflinePlayer.isCollectionUnlocked(collection: Collection): Boolean {
    if (!collection.hasUnlockConditions) return true
    return this.profile.read(collection.unlockedKey)
}

/**
 * Attempt to unlock a collection by evaluating unlock conditions.
 * Returns true if newly unlocked.
 */
fun Player.tryUnlockCollection(collection: Collection): Boolean {
    if (!collection.hasUnlockConditions) return false
    if (this.profile.read(collection.unlockedKey)) return false

    if (!collection.unlockConditions.areMet(this.toDispatcher(), EmptyProvidedHolder)) {
        return false
    }

    val event = PlayerCollectionUnlockEvent(this, collection)
    Bukkit.getPluginManager().callEvent(event)
    if (event.isCancelled) return false

    this.profile.write(collection.unlockedKey, true)
    sendUnlockMessages(this, collection)
    return true
}

/**
 * Raw set of collection count. Does NOT fire rewards or events.
 */
fun OfflinePlayer.setCollectionCount(collection: Collection, count: Double) {
    this.profile.write(collection.countKey, count)
    val newTier = collection.getTierForCount(count)
    this.profile.write(collection.tierKey, newTier)
}

/**
 * The critical flow -- adds count and fires all rewards/events per spec section 14.
 */
fun Player.giveCollectionCount(collection: Collection, amount: Double) {
    // Step 0: No-op on non-positive amount
    if (amount <= 0) return

    // Step 1: UNLOCK GATE
    if (!this.tryUnlockCollection(collection)) return

    // Step 1.5: LIVE CONDITIONS GATE
    if (collection.hasConditions) {
        if (!collection.conditions.areMet(this.toDispatcher(), EmptyProvidedHolder)) {
            return
        }
    }

    // Step 2: Read previous count
    val previousCount = this.profile.read(collection.countKey)

    // Step 3: Compute new count
    val newCount = previousCount + amount

    // Step 4: Write new count (always, even post-completion)
    this.profile.write(collection.countKey, newCount)

    // Step 5: Read previous tier
    val previousTier = this.profile.read(collection.tierKey)

    // Step 6: Compute new tier
    val newTier = collection.getTierForCount(newCount)

    // Step 7: If no tier change, return
    if (newTier == previousTier) return

    // Step 8: Tier-up loop
    for (t in (previousTier + 1)..newTier) {
        val tierUpEvent = PlayerCollectionTierUpEvent(this, collection, t - 1, t)
        Bukkit.getPluginManager().callEvent(tierUpEvent)
        if (tierUpEvent.isCancelled) continue

        // Advance tier in PDC
        this.profile.write(collection.tierKey, t)

        // Run allTierRewards chain (if non-empty)
        collection.allTierRewards?.trigger(
            DispatchedTrigger(
                this.toDispatcher(),
                TriggerCollectionTierUp,
                TriggerData(player = this, location = this.location)
            )
        )

        // Run tier-specific rewards (if entry exists)
        collection.tierRewards[t]?.trigger(
            DispatchedTrigger(
                this.toDispatcher(),
                TriggerCollectionTierUp,
                TriggerData(player = this, location = this.location)
            )
        )

        // Send tier-up messages
        sendTierUpMessages(this, collection, t - 1, t)
    }

    // Step 9: Completion check
    if (newTier == collection.maxTier && !this.profile.read(collection.doneKey)) {
        val completeEvent = PlayerCollectionCompleteEvent(this, collection)
        Bukkit.getPluginManager().callEvent(completeEvent)
        if (!completeEvent.isCancelled) {
            this.profile.write(collection.doneKey, true)

            // Run completion reward effects
            collection.completionRewardEffects?.trigger(
                DispatchedTrigger(
                    this.toDispatcher(),
                    TriggerCollectionComplete,
                    TriggerData(player = this, location = this.location)
                )
            )

            // Send completion messages
            sendCompletionMessages(this, collection)
        }
    }
}

/**
 * Reset all collection data for a player on a specific collection.
 * Clears count, tier, completed, and unlocked flags.
 */
fun OfflinePlayer.resetCollection(collection: Collection) {
    this.profile.write(collection.countKey, 0.0)
    this.profile.write(collection.tierKey, 0)
    this.profile.write(collection.doneKey, false)
    this.profile.write(collection.unlockedKey, false)
}

/**
 * Sum of all collection tiers across all collections for the player.
 */
val OfflinePlayer.totalCollectionTiers: Int
    get() = Collections.values().sumOf { this.getCollectionTier(it) }

/**
 * Count of collections the player has unlocked.
 */
val OfflinePlayer.unlockedCollectionCount: Int
    get() = Collections.values().count { this.isCollectionUnlocked(it) }

/**
 * Count of collections the player has completed.
 */
val OfflinePlayer.completedCollectionCount: Int
    get() = Collections.values().count { this.isCollectionComplete(it) }


// ==================== Private message helpers ====================

private fun applyPlaceholders(
    message: String,
    player: Player,
    collection: Collection,
    previousTier: Int? = null,
    tier: Int? = null
): String {
    var result = message
        .replace("%player%", player.name)
        .replace("%collection_name%", collection.name)
        .replace("%collection_id%", collection.id)
        .replace("%max_tier%", collection.maxTier.toString())
        .replace("%max_tier_numeral%", collection.maxTier.toNumeral())

    if (tier != null) {
        result = result
            .replace("%tier%", tier.toString())
            .replace("%tier_numeral%", tier.toNumeral())
    }

    if (previousTier != null) {
        result = result
            .replace("%previous_tier%", previousTier.toString())
            .replace("%previous_tier_numeral%", previousTier.toNumeral())
    }

    return StringUtils.format(result)
}

/**
 * Send tier-up messages per lang.yml + config.yml toggles.
 */
private fun sendTierUpMessages(player: Player, collection: Collection, previousTier: Int, tier: Int) {
    if (!plugin.configYml.getBool("messages.tier-up.enabled")) return

    if (plugin.configYml.getBool("messages.tier-up.chat")) {
        val chatMsg = plugin.langYml.getString("messages.tier-up.chat")
        player.sendMessage(applyPlaceholders(chatMsg, player, collection, previousTier, tier))
    }

    if (plugin.configYml.getBool("messages.tier-up.title")) {
        val title = applyPlaceholders(
            plugin.langYml.getString("messages.tier-up.title"),
            player, collection, previousTier, tier
        )
        val subtitle = applyPlaceholders(
            plugin.langYml.getString("messages.tier-up.subtitle"),
            player, collection, previousTier, tier
        )
        player.sendTitle(title, subtitle, 10, 40, 10)
    }

    if (plugin.configYml.getBool("messages.tier-up.sound")) {
        val soundString = plugin.langYml.getString("messages.tier-up.sound")
        player.playSound(player.location, soundString, 1.0f, 1.0f)
    }
}

/**
 * Send completion messages per lang.yml + config.yml toggles.
 */
private fun sendCompletionMessages(player: Player, collection: Collection) {
    if (!plugin.configYml.getBool("messages.complete.enabled")) return

    if (plugin.configYml.getBool("messages.complete.chat")) {
        val chatMsg = plugin.langYml.getString("messages.complete.chat")
        player.sendMessage(applyPlaceholders(chatMsg, player, collection))
    }

    if (plugin.configYml.getBool("messages.complete.title")) {
        val title = applyPlaceholders(
            plugin.langYml.getString("messages.complete.title"),
            player, collection
        )
        val subtitle = applyPlaceholders(
            plugin.langYml.getString("messages.complete.subtitle"),
            player, collection
        )
        player.sendTitle(title, subtitle, 10, 40, 10)
    }

    if (plugin.configYml.getBool("messages.complete.sound")) {
        val soundString = plugin.langYml.getString("messages.complete.sound")
        player.playSound(player.location, soundString, 1.0f, 1.0f)
    }

    if (plugin.configYml.getBool("messages.complete.broadcast")) {
        val broadcastMsg = applyPlaceholders(
            plugin.langYml.getString("messages.complete.broadcast"),
            player, collection
        )
        Bukkit.broadcastMessage(broadcastMsg)
    }
}

/**
 * Send unlock messages per lang.yml + config.yml toggles.
 */
private fun sendUnlockMessages(player: Player, collection: Collection) {
    if (!plugin.configYml.getBool("messages.unlock.enabled")) return

    if (plugin.configYml.getBool("messages.unlock.chat")) {
        val chatMsg = plugin.langYml.getString("messages.unlock.chat")
        player.sendMessage(applyPlaceholders(chatMsg, player, collection))
    }

    if (plugin.configYml.getBool("messages.unlock.title")) {
        val title = applyPlaceholders(
            plugin.langYml.getString("messages.unlock.title"),
            player, collection
        )
        val subtitle = applyPlaceholders(
            plugin.langYml.getString("messages.unlock.subtitle"),
            player, collection
        )
        player.sendTitle(title, subtitle, 10, 40, 10)
    }

    if (plugin.configYml.getBool("messages.unlock.sound")) {
        val soundString = plugin.langYml.getString("messages.unlock.sound")
        player.playSound(player.location, soundString, 1.0f, 1.0f)
    }
}
