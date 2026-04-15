package com.willfp.ecocollections.libreforge.effect

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.data.profile
import com.willfp.ecocollections.api.event.PlayerCollectionUnlockEvent
import com.willfp.ecocollections.collections.Collections
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit

object EffectUnlockCollection : Effect<NoCompileData>("unlock_collection") {
    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require("collection", "You must specify the collection!")
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        val collection = Collections.getByID(config.getString("collection").lowercase()) ?: return false
        // No-op if already unlocked.
        if (player.profile.read(collection.unlockedKey)) return false
        // Fire the event so other plugins can observe, but we IGNORE cancellation here:
        // this effect is the documented admin override.
        Bukkit.getPluginManager().callEvent(PlayerCollectionUnlockEvent(player, collection))
        player.profile.write(collection.unlockedKey, true)
        return true
    }
}
