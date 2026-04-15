package com.willfp.ecocollections.gui

import com.willfp.eco.core.config.BuildableConfig
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.onLeftClick
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.util.StringUtils
import com.willfp.ecocollections.api.isCollectionComplete
import com.willfp.ecocollections.collections.Collection
import com.willfp.ecocollections.collections.Collections
import com.willfp.ecocollections.groups.CollectionGroup
import com.willfp.ecocollections.groups.CollectionGroups
import com.willfp.ecocollections.plugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object CollectionsGUI {

    /**
     * Open the collections GUI for a player.
     * Handles single/zero-group bypass per spec section 13 item 1.
     */
    fun open(player: Player) {
        val groups = CollectionGroups.values()

        when {
            groups.isEmpty() -> {
                // Zero-group fallback: create synthetic default group containing all collections
                val syntheticGroup = createSyntheticDefaultGroup()
                GroupGUI.open(player, syntheticGroup, bypassMode = true)
            }

            groups.size == 1 -> {
                // Single-group bypass: skip root GUI entirely
                GroupGUI.open(player, groups.first(), bypassMode = true)
            }

            else -> {
                // Normal mode: show root GUI with all groups
                openRootGUI(player)
            }
        }
    }

    /**
     * Creates a synthetic in-memory CollectionGroup for the zero-group fallback.
     * Contains all registered collections. Uses a BuildableConfig with minimal
     * required fields so CollectionGroup's constructor can parse without errors.
     */
    private fun createSyntheticDefaultGroup(): CollectionGroup {
        val displayName = plugin.langYml.getString("groups.default-group-name")

        val config = BuildableConfig()
        config.set("name", displayName)
        config.set("permission", "")
        config.set("gui.icon", "barrier")
        config.set("gui.position.row", 1)
        config.set("gui.position.column", 1)
        config.set("gui.lore", emptyList<String>())

        return CollectionGroup("default", config)
    }

    /**
     * Opens the root menu showing one icon per group.
     */
    private fun openRootGUI(player: Player) {
        val title = StringUtils.format(plugin.configYml.getString("gui.root.title"))
        val rows = plugin.configYml.getInt("gui.root.rows")

        // Filler
        val fillerMaterial = plugin.configYml.getString("gui.root.filler.material")
        val fillerMat = Material.matchMaterial(fillerMaterial.uppercase()) ?: Material.BLACK_STAINED_GLASS_PANE

        // Close button
        val closeMaterial = plugin.configYml.getString("gui.root.close.material")
        val closeName = plugin.configYml.getString("gui.root.close.name")
        val closeRow = plugin.configYml.getInt("gui.root.close.location.row")
        val closeColumn = plugin.configYml.getInt("gui.root.close.location.column")

        val closeItem = ItemStack(Material.matchMaterial(closeMaterial.uppercase()) ?: Material.BARRIER)
        val closeMeta = closeItem.itemMeta
        closeMeta?.setDisplayName(StringUtils.format(closeName))
        closeItem.itemMeta = closeMeta

        val theMenu = menu(rows) {
            setTitle(title)

            setMask(
                FillerMask(
                    MaskItems(fillerMat),
                    *Array(rows) { "111111111" }
                )
            )

            setSlot(closeRow, closeColumn, slot(closeItem) {
                onLeftClick { event, _ ->
                    event.whoClicked.closeInventory()
                }
            })

            // Group icons
            for (group in CollectionGroups.values()) {
                // Skip groups the player doesn't have permission for
                if (group.permission.isNotEmpty() && !player.hasPermission(group.permission)) {
                    continue
                }

                val groupIcon = buildGroupIcon(player, group)
                setSlot(group.guiRow, group.guiColumn, groupIcon)
            }
        }

        theMenu.open(player)
    }

    /**
     * Builds the icon slot for a group in the root menu.
     * Shows progress like (X/Y maxed) in lore.
     */
    private fun buildGroupIcon(player: Player, group: CollectionGroup): com.willfp.eco.core.gui.slot.Slot {
        val iconItem = group.icon.item.clone()
        val meta = iconItem.itemMeta

        if (meta != null) {
            meta.setDisplayName(StringUtils.format(group.name))

            // Calculate group progress
            val collectionsInGroup = getCollectionsInGroup(group)
            val maxedCount = collectionsInGroup.count { player.isCollectionComplete(it) }
            val totalCount = collectionsInGroup.size

            // Build lore from group config + progress
            val lore = mutableListOf<String>()
            for (line in group.guiLore) {
                lore.add(StringUtils.format(line))
            }
            lore.add("")
            lore.add(StringUtils.format("&7Progress: &e$maxedCount&7/&e$totalCount &7maxed"))

            meta.lore = lore
            iconItem.itemMeta = meta
        }

        return slot(iconItem) {
            onLeftClick { _, _, _, _ ->
                GroupGUI.open(player, group)
            }
        }
    }

    /**
     * Returns all collections that belong to a given group.
     * For the synthetic default group, returns all collections.
     */
    internal fun getCollectionsInGroup(group: CollectionGroup): List<Collection> {
        if (group.id == "default" && CollectionGroups.values().isEmpty()) {
            return Collections.values().toList()
        }
        return Collections.values().filter { it.group?.id == group.id }
    }
}
