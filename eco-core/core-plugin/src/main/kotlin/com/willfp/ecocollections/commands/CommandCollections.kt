package com.willfp.ecocollections.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.ecocollections.gui.CollectionsGUI
import com.willfp.ecocollections.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandCollections : PluginCommand(
    plugin,
    "collections",
    "ecocollections.command.collections",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (sender !is Player) return
        CollectionsGUI.open(sender)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return emptyList()
    }
}
