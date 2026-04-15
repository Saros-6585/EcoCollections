package com.willfp.ecocollections.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.onLeftClick
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.toNumeral
import com.willfp.ecocollections.api.getCollectionCount
import com.willfp.ecocollections.api.getCollectionTier
import com.willfp.ecocollections.collections.Collection
import com.willfp.ecocollections.plugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object CollectionDetailGUI {

    /**
     * Open the detail GUI for a specific collection.
     * Shows tier list and player progress.
     *
     * This GUI is NEVER opened for a locked collection
     * (the GroupGUI click handler prevents it).
     */
    fun open(player: Player, collection: Collection) {
        val title = StringUtils.format(
            plugin.configYml.getString("gui.detail.title")
                .replace("%collection_name%", collection.name)
        )
        val rows = plugin.configYml.getInt("gui.detail.rows")

        val playerTier = player.getCollectionTier(collection)
        val playerCount = player.getCollectionCount(collection)

        // Center slot: collection icon with name and description
        val centerRow = rows / 2
        val centerCol = 5 // Middle column of 9

        val centerSlot = buildCenterSlot(player, collection, playerTier, playerCount)

        // Tier slots: one item per tier, positioned around the center
        val tierSlots = calculateTierPositions(collection.maxTier, rows)

        // Back button -> GroupGUI
        val backRow = rows
        val backCol = 1

        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta
        backMeta?.setDisplayName(StringUtils.format("&fBack"))
        backItem.itemMeta = backMeta

        // Rank slot
        val rankSlot = buildRankSlot(player, collection)

        val theMenu = menu(rows) {
            setTitle(title)

            setSlot(centerRow, centerCol, centerSlot)

            for (tier in 1..collection.maxTier) {
                val position = tierSlots.getOrNull(tier - 1) ?: continue
                val tierSlotItem = buildTierSlot(collection, tier, playerTier, playerCount)
                setSlot(position.first, position.second, tierSlotItem)
            }

            setSlot(backRow, backCol, slot(backItem) {
                onLeftClick { _, _, _, _ ->
                    val group = collection.group
                    if (group != null) {
                        GroupGUI.open(player, group)
                    } else {
                        CollectionsGUI.open(player)
                    }
                }
            })

            setSlot(backRow, 9, rankSlot)
        }

        theMenu.open(player)
    }

    /**
     * Builds the center slot showing the collection icon with name and description.
     */
    private fun buildCenterSlot(
        player: Player,
        collection: Collection,
        playerTier: Int,
        playerCount: Double
    ): Slot {
        val iconItem = collection.icon.item.clone()
        val meta = iconItem.itemMeta

        if (meta != null) {
            meta.setDisplayName(StringUtils.format(collection.name))

            val lore = mutableListOf<String>()

            // Description lines with placeholder substitution
            for (line in collection.descriptionLines) {
                lore.add(
                    StringUtils.format(
                        substituteDetailPlaceholders(line, collection, playerTier, playerCount)
                    )
                )
            }

            // Rank
            val rankLine = GroupGUI.formatRankLore(player, collection)
            if (rankLine != null) {
                lore.add("")
                lore.add(StringUtils.format(rankLine))
            }

            meta.lore = lore
            iconItem.itemMeta = meta
        }

        return slot(iconItem)
    }

    /**
     * Builds a tier slot showing tier number, requirement, rewards description,
     * and colored based on whether the player has reached that tier.
     */
    private fun buildTierSlot(
        collection: Collection,
        tier: Int,
        playerTier: Int,
        playerCount: Double
    ): Slot {
        val reached = playerTier >= tier
        val isMaxTier = tier == collection.maxTier
        val requirement = collection.tierRequirements[tier - 1]

        // Choose material/color based on tier status
        val material = when {
            isMaxTier && reached -> Material.GOLD_BLOCK
            reached -> Material.LIME_STAINED_GLASS_PANE
            else -> Material.GRAY_STAINED_GLASS_PANE
        }

        val tierItem = ItemStack(material)
        val meta = tierItem.itemMeta

        if (meta != null) {
            // Tier name with color
            val tierColor = when {
                isMaxTier && reached -> "&6"
                reached -> "&a"
                else -> "&7"
            }
            meta.setDisplayName(
                StringUtils.format("${tierColor}Tier ${tier.toNumeral()}")
            )

            val lore = mutableListOf<String>()

            // Requirement line
            lore.add(StringUtils.format("&7Requires: &e${requirement.toLong()} &7items"))

            // Progress for current tier
            if (!reached) {
                val percent = if (requirement > 0) {
                    (playerCount / requirement * 100).coerceIn(0.0, 100.0).toInt()
                } else {
                    100
                }
                lore.add(StringUtils.format("&7Progress: &e${playerCount.toLong()}&7/&e${requirement.toLong()} &8(&e${percent}%&8)"))
            } else {
                lore.add(StringUtils.format("&a&lCompleted"))
            }

            // Rewards description
            lore.add("")
            lore.add(StringUtils.format("&7Rewards:"))

            // Show rewards for this tier from tier-specific rewards
            val tierChain = collection.tierRewards[tier]
            if (tierChain != null) {
                lore.add(StringUtils.format("  &8- &fTier $tier rewards"))
            }

            // Show all-tier rewards
            val allChain = collection.allTierRewards
            if (allChain != null) {
                lore.add(StringUtils.format("  &8- &fEvery tier rewards"))
            }

            // Completion rewards indicator on max tier
            if (isMaxTier && collection.completionRewardEffects != null) {
                lore.add(StringUtils.format("  &8- &6Completion bonus"))
            }

            meta.lore = lore
            tierItem.itemMeta = meta
        }

        return slot(tierItem)
    }

    /**
     * Builds the rank info slot at the bottom of the detail GUI.
     */
    private fun buildRankSlot(player: Player, collection: Collection): Slot {
        val rankItem = ItemStack(Material.PLAYER_HEAD)
        val meta = rankItem.itemMeta

        if (meta != null) {
            meta.setDisplayName(StringUtils.format("&7Your Ranking"))

            val lore = mutableListOf<String>()
            val rankLine = GroupGUI.formatRankLore(player, collection)
            if (rankLine != null) {
                lore.add(StringUtils.format(rankLine))
            }

            meta.lore = lore
            rankItem.itemMeta = meta
        }

        return slot(rankItem)
    }

    /**
     * Calculates positions for tier slots arranged around the GUI.
     * Fills row by row, left to right, skipping the center slot.
     * Returns list of (row, column) pairs for each tier index.
     */
    private fun calculateTierPositions(maxTier: Int, rows: Int): List<Pair<Int, Int>> {
        val positions = mutableListOf<Pair<Int, Int>>()
        val centerRow = rows / 2
        val centerCol = 5

        // Place tier slots in rows 2 through (rows-1), columns 2 through 8
        // This avoids the border rows and gives space for back/rank buttons
        for (row in 2 until rows) {
            for (col in 2..8) {
                // Skip the center slot
                if (row == centerRow && col == centerCol) continue
                positions.add(Pair(row, col))
                if (positions.size >= maxTier) return positions
            }
        }

        return positions
    }

    /**
     * Substitutes collection-specific placeholders in a string for the detail view.
     */
    private fun substituteDetailPlaceholders(
        text: String,
        collection: Collection,
        tier: Int,
        count: Double
    ): String {
        val maxTier = collection.maxTier
        val required = if (tier >= maxTier) {
            plugin.langYml.getString("placeholders.max-tier")
        } else {
            collection.tierRequirements[tier].toLong().toString()
        }

        val percent = if (tier >= maxTier) {
            "100"
        } else {
            val req = collection.tierRequirements[tier]
            val prevReq = if (tier > 0) collection.tierRequirements[tier - 1] else 0.0
            ((count - prevReq) / (req - prevReq) * 100)
                .coerceIn(0.0, 100.0)
                .toInt()
                .toString()
        }

        val previousTier = (tier - 1).coerceAtLeast(0)
        val nextTier = (tier + 1).coerceAtMost(maxTier)

        return text
            .replace("%tier%", tier.toString())
            .replace("%tier_numeral%", tier.toNumeral())
            .replace("%previous_tier%", previousTier.toString())
            .replace("%previous_tier_numeral%", previousTier.toNumeral())
            .replace("%next_tier%", nextTier.toString())
            .replace("%next_tier_numeral%", nextTier.toNumeral())
            .replace("%max_tier%", maxTier.toString())
            .replace("%max_tier_numeral%", maxTier.toNumeral())
            .replace("%count%", count.toLong().toString())
            .replace("%required%", required)
            .replace("%percent%", percent)
            .replace("%collection_name%", collection.name)
    }
}
