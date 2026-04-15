package com.willfp.ecocollections.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.data.profile
import com.willfp.eco.util.StringUtils
import com.willfp.ecocollections.api.giveCollectionCount
import com.willfp.ecocollections.api.resetCollection
import com.willfp.ecocollections.api.setCollectionCount
import com.willfp.ecocollections.collections.Collections
import com.willfp.ecocollections.collections.CollectionsLeaderboard
import com.willfp.ecocollections.plugin
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandEcoCollections : PluginCommand(
    plugin,
    "ecocollections",
    "ecocollections.command.admin",
    false
) {
    init {
        this.addSubcommand(SubcommandGive)
            .addSubcommand(SubcommandSet)
            .addSubcommand(SubcommandSetTier)
            .addSubcommand(SubcommandReset)
            .addSubcommand(SubcommandReload)
            .addSubcommand(SubcommandLeaderboard)
    }

    override fun onExecute(sender: CommandSender, args: List<String>) {
        // Base command with no subcommand -- show usage
        sender.sendMessage(StringUtils.format("&eEcoCollections &7- Subcommands: give, set, settier, reset, reload, leaderboard"))
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return emptyList()
    }
}

/**
 * /ecocollections give <player> <collection> <amount>
 * Adds count and fires rewards.
 */
private object SubcommandGive : Subcommand(
    plugin,
    "give",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage(StringUtils.format("&cUsage: /ecocollections give <player> <collection> <amount>"))
            return
        }

        val playerName = args[0]
        val collectionId = args[1].lowercase()
        val amount = args[2].toDoubleOrNull()

        if (amount == null) {
            sender.sendMessage(StringUtils.format("&cInvalid amount: ${args[2]}"))
            return
        }

        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-player")
                        .replace("%name%", playerName)
                )
            )
            return
        }

        val collection = Collections.getByID(collectionId)
        if (collection == null) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-collection")
                        .replace("%id%", collectionId)
                )
            )
            return
        }

        target.giveCollectionCount(collection, amount)
        sender.sendMessage(
            StringUtils.format(
                plugin.langYml.getString("commands.give-success")
                    .replace("%amount%", amount.toLong().toString())
                    .replace("%player%", target.name)
                    .replace("%collection%", collection.name)
            )
        )
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> Collections.values().map { it.id }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            3 -> listOf("1", "10", "100", "1000")
                .filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}

/**
 * /ecocollections set <player> <collection> <amount>
 * Sets count, does NOT fire rewards.
 */
@Suppress("DEPRECATION")
private object SubcommandSet : Subcommand(
    plugin,
    "set",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage(StringUtils.format("&cUsage: /ecocollections set <player> <collection> <amount>"))
            return
        }

        val playerName = args[0]
        val collectionId = args[1].lowercase()
        val amount = args[2].toDoubleOrNull()

        if (amount == null) {
            sender.sendMessage(StringUtils.format("&cInvalid amount: ${args[2]}"))
            return
        }

        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-player")
                        .replace("%name%", playerName)
                )
            )
            return
        }

        val collection = Collections.getByID(collectionId)
        if (collection == null) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-collection")
                        .replace("%id%", collectionId)
                )
            )
            return
        }

        target.setCollectionCount(collection, amount)
        sender.sendMessage(
            StringUtils.format(
                plugin.langYml.getString("commands.set-success")
                    .replace("%amount%", amount.toLong().toString())
                    .replace("%player%", target.name ?: playerName)
                    .replace("%collection%", collection.name)
            )
        )
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> Collections.values().map { it.id }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            3 -> listOf("0", "1", "10", "100", "1000")
                .filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}

/**
 * /ecocollections settier <player> <collection> <tier>
 * Sets tier directly, does not fire rewards.
 */
@Suppress("DEPRECATION")
private object SubcommandSetTier : Subcommand(
    plugin,
    "settier",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage(StringUtils.format("&cUsage: /ecocollections settier <player> <collection> <tier>"))
            return
        }

        val playerName = args[0]
        val collectionId = args[1].lowercase()
        val tier = args[2].toIntOrNull()

        if (tier == null) {
            sender.sendMessage(StringUtils.format("&cInvalid tier: ${args[2]}"))
            return
        }

        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-player")
                        .replace("%name%", playerName)
                )
            )
            return
        }

        val collection = Collections.getByID(collectionId)
        if (collection == null) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-collection")
                        .replace("%id%", collectionId)
                )
            )
            return
        }

        // Write tier directly to PDC
        target.profile.write(collection.tierKey, tier.coerceIn(0, collection.maxTier))
        sender.sendMessage(
            StringUtils.format(
                plugin.langYml.getString("commands.settier-success")
                    .replace("%tier%", tier.toString())
                    .replace("%player%", target.name ?: playerName)
                    .replace("%collection%", collection.name)
            )
        )
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> Collections.values().map { it.id }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            3 -> {
                val collection = Collections.getByID(args[1].lowercase())
                val maxTier = collection?.maxTier ?: 10
                (0..maxTier).map { it.toString() }
                    .filter { it.startsWith(args[2]) }
            }
            else -> emptyList()
        }
    }
}

/**
 * /ecocollections reset <player> <collection|all>
 * Resets count, tier, completed, and unlocked flags.
 */
@Suppress("DEPRECATION")
private object SubcommandReset : Subcommand(
    plugin,
    "reset",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage(StringUtils.format("&cUsage: /ecocollections reset <player> <collection|all>"))
            return
        }

        val playerName = args[0]
        val collectionArg = args[1].lowercase()

        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.no-such-player")
                        .replace("%name%", playerName)
                )
            )
            return
        }

        if (collectionArg == "all") {
            // Reset every collection for the player
            for (collection in Collections.values()) {
                target.resetCollection(collection)
            }
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.reset-success")
                        .replace("%player%", target.name ?: playerName)
                        .replace("%collection%", "all collections")
                )
            )
        } else {
            val collection = Collections.getByID(collectionArg)
            if (collection == null) {
                sender.sendMessage(
                    StringUtils.format(
                        plugin.langYml.getString("commands.no-such-collection")
                            .replace("%id%", collectionArg)
                    )
                )
                return
            }

            target.resetCollection(collection)
            sender.sendMessage(
                StringUtils.format(
                    plugin.langYml.getString("commands.reset-success")
                        .replace("%player%", target.name ?: playerName)
                        .replace("%collection%", collection.name)
                )
            )
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> {
                val options = mutableListOf("all")
                options.addAll(Collections.values().map { it.id })
                options.filter { it.lowercase().startsWith(args[1].lowercase()) }
            }
            else -> emptyList()
        }
    }
}

/**
 * /ecocollections reload
 * Reloads configs and re-binds counters.
 */
private object SubcommandReload : Subcommand(
    plugin,
    "reload",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        this.plugin.reload()
        sender.sendMessage(
            StringUtils.format(
                plugin.langYml.getString("commands.reload-success")
            )
        )
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return emptyList()
    }
}

/**
 * /ecocollections leaderboard refresh [collection]
 * Invalidates leaderboard cache(s).
 */
private object SubcommandLeaderboard : Subcommand(
    plugin,
    "leaderboard",
    "ecocollections.command.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty() || args[0].lowercase() != "refresh") {
            sender.sendMessage(StringUtils.format("&cUsage: /ecocollections leaderboard refresh [collection]"))
            return
        }

        if (args.size >= 2) {
            // Refresh specific collection leaderboard
            val collectionId = args[1].lowercase()
            val collection = Collections.getByID(collectionId)
            if (collection == null) {
                sender.sendMessage(
                    StringUtils.format(
                        plugin.langYml.getString("commands.no-such-collection")
                            .replace("%id%", collectionId)
                    )
                )
                return
            }
            CollectionsLeaderboard.invalidate(collection)
            sender.sendMessage(StringUtils.format("&aRefreshed leaderboard for &e${collection.name}&a."))
        } else {
            // Refresh all leaderboards
            CollectionsLeaderboard.invalidateAll()
            sender.sendMessage(StringUtils.format("&aRefreshed all leaderboards."))
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> listOf("refresh").filter { it.startsWith(args[0].lowercase()) }
            2 -> Collections.values().map { it.id }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            else -> emptyList()
        }
    }
}
