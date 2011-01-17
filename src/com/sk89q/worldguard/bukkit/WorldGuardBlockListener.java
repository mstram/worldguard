// $Id$
/*
 * WorldGuard
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldguard.bukkit;

import java.util.Iterator;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockDamageLevel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.event.block.*;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.inventory.ItemStack;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.blacklist.events.*;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import static com.sk89q.worldguard.bukkit.BukkitUtil.*;

public class WorldGuardBlockListener extends BlockListener {
    /**
     * Plugin.
     */
    private WorldGuardPlugin plugin;
    
    /**
     * Construct the object;
     * 
     * @param plugin
     */
    public WorldGuardBlockListener(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a block is damaged (or broken)
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();

        if (!plugin.itemDurability && event.getDamageLevel() == BlockDamageLevel.BROKEN) {
            ItemStack held = player.getItemInHand();
            held.setDamage((byte)-1);
            player.setItemInHand(held);
        }
        
        if (plugin.useRegions && event.getDamageLevel() == BlockDamageLevel.BROKEN) {
            Vector pt = BukkitUtil.toVector(event.getBlock());
            LocalPlayer localPlayer = plugin.wrapPlayer(player);
            
            if (!plugin.hasPermission(player, "/regionbypass")
                    && !plugin.regionManager.getApplicableRegions(pt).canBuild(localPlayer)) {
                player.sendMessage(ChatColor.DARK_RED + "You don't have permission for this area.");
                event.setCancelled(true);
                return;
            }
        }
        
        if (plugin.blacklist != null && event.getDamageLevel() == BlockDamageLevel.BROKEN) {
            if (!plugin.blacklist.check(
                    new BlockBreakBlacklistEvent(plugin.wrapPlayer(player),
                            toVector(event.getBlock()),
                            event.getBlock().getTypeId()), false, false)) {
                event.setCancelled(true);
                return;
            }

            if (!plugin.blacklist.check(
                    new DestroyWithBlacklistEvent(plugin.wrapPlayer(player),
                            toVector(event.getBlock()),
                            player.getItemInHand().getTypeId()), false, false)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Called when a block flows (water/lava)
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockFlow(BlockFromToEvent event) {
        World world = event.getBlock().getWorld();
        Block blockFrom = event.getBlock();
        Block blockTo = event.getToBlock();
        
        boolean isWater = blockFrom.getTypeId() == 8 || blockFrom.getTypeId() == 9;
        boolean isLava = blockFrom.getTypeId() == 10 || blockFrom.getTypeId() == 11;

        if (plugin.simulateSponge && isWater) {
            int ox = blockTo.getX();
            int oy = blockTo.getY();
            int oz = blockTo.getZ();

            for (int cx = -plugin.spongeRadius; cx <= plugin.spongeRadius; cx++) {
                for (int cy = -plugin.spongeRadius; cy <= plugin.spongeRadius; cy++) {
                    for (int cz = -plugin.spongeRadius; cz <= plugin.spongeRadius; cz++) {
                        if (world.getBlockTypeIdAt(ox + cx, oy + cy, oz + cz) == 19) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        if (plugin.classicWater && isWater) {
            int blockBelow = world.getBlockTypeIdAt(blockFrom.getX(), blockFrom.getY() - 1, blockFrom.getZ());
            if (blockBelow != 0 && blockBelow != 8 && blockBelow != 9) {
                world.getBlockAt(blockFrom.getX(), blockFrom.getY(), blockFrom.getZ()).setTypeId(9);
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.preventWaterDamage.size() > 0 && isWater) {
            int targetId = world.getBlockTypeIdAt(
                    blockTo.getX(), blockTo.getY(), blockTo.getZ());
            if (plugin.preventWaterDamage.contains(targetId)) {
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.allowedLavaSpreadOver.size() > 0 && isLava) {
            int targetId = world.getBlockTypeIdAt(
                    blockTo.getX(), blockTo.getY() - 1, blockTo.getZ());
            if (!plugin.allowedLavaSpreadOver.contains(targetId)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Called when a block gets ignited
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockIgnite(BlockIgniteEvent event) {
        IgniteCause cause = event.getCause();
        Block block = event.getBlock();
        Player player = event.getPlayer();
        World world = block.getWorld();
        boolean isFireSpread = cause == IgniteCause.SLOW_SPREAD
                || cause == IgniteCause.SPREAD;
        
        if (plugin.preventLavaFire && cause == IgniteCause.LAVA) {
            event.setCancelled(true);
            return;
        }

        if (plugin.disableFireSpread && isFireSpread) {
            event.setCancelled(true);
            return;
        }
        
        if (plugin.blockLighter && cause == IgniteCause.FLINT_AND_STEEL) {
            event.setCancelled(true);
            return;
        }

        if (plugin.fireSpreadDisableToggle && isFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (plugin.disableFireSpreadBlocks.size() > 0 && isFireSpread) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            
            if (plugin.disableFireSpreadBlocks.contains(world.getBlockTypeIdAt(x, y - 1, z))
                    || plugin.disableFireSpreadBlocks.contains(world.getBlockTypeIdAt(x + 1, y, z))
                    || plugin.disableFireSpreadBlocks.contains(world.getBlockTypeIdAt(x - 1, y, z))
                    || plugin.disableFireSpreadBlocks.contains(world.getBlockTypeIdAt(x, y, z - 1))
                    || plugin.disableFireSpreadBlocks.contains(world.getBlockTypeIdAt(x, y, z + 1))) {
                event.setCancelled(true);
                return;
            }
        }
        
        if (plugin.useRegions && player != null && !plugin.hasPermission(player, "/regionbypass")) {
            Vector pt = toVector(block);
            LocalPlayer localPlayer = plugin.wrapPlayer(player);
            
            if (cause == IgniteCause.FLINT_AND_STEEL
                    && !plugin.regionManager.getApplicableRegions(pt).canBuild(localPlayer)) {
                event.setCancelled(true);
                return;
            }
            
            if (cause == IgniteCause.FLINT_AND_STEEL
                    && !plugin.regionManager.getApplicableRegions(pt).allowsLighter()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Called when block physics occurs
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockPhysics(BlockPhysicsEvent event) {
        int id = event.getChangedTypeId();

        if (id == 13 && plugin.noPhysicsGravel) {
            event.setCancelled(true);
            return;
        }

        if (id == 12 && plugin.noPhysicsSand) {
            event.setCancelled(true);
            return;
        }

        if (id == 90 && plugin.allowPortalAnywhere) {
            event.setCancelled(true);
            return;
        }
    }
    
    /**
     * Called when a block is interacted with
     * 
     * @param event Relevant event details
     */
    public void onBlockInteract(BlockInteractEvent event) {
        Block block = event.getBlock();
        LivingEntity entity = event.getEntity();
        
        if (entity instanceof Player && block.getType() == Material.CHEST) {
            Player player = (Player)entity;
            if (plugin.useRegions) {
                Vector pt = toVector(block);
                LocalPlayer localPlayer = plugin.wrapPlayer(player);
    
                if (!plugin.hasPermission(player, "/regionbypass")
                        && !plugin.regionManager.getApplicableRegions(pt).canBuild(localPlayer)) {
                    player.sendMessage(ChatColor.DARK_RED + "You don't have permission for this area.");
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        if (plugin.blacklist != null && entity instanceof Player) {
            Player player = (Player)entity;
            
            if (!plugin.blacklist.check(
                    new BlockInteractBlacklistEvent(plugin.wrapPlayer(player), toVector(block),
                            block.getTypeId()), false, false)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Called when a player places a block
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        Block blockPlaced = event.getBlock();
        Player player = event.getPlayer();
        World world = blockPlaced.getWorld();
        
        if (plugin.useRegions) {
            Vector pt = toVector(blockPlaced);
            LocalPlayer localPlayer = plugin.wrapPlayer(player);

            if (!plugin.hasPermission(player, "/regionbypass")
                    && !plugin.regionManager.getApplicableRegions(pt).canBuild(localPlayer)) {
                player.sendMessage(ChatColor.DARK_RED + "You don't have permission for this area.");
                event.setCancelled(true);
                return;
            }
        }
        
        if (plugin.blacklist != null) {
            if (!plugin.blacklist.check(
                    new BlockPlaceBlacklistEvent(plugin.wrapPlayer(player), toVector(blockPlaced),
                            blockPlaced.getTypeId()), false, false)) {
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.simulateSponge && blockPlaced.getTypeId() == 19) {
            int ox = blockPlaced.getX();
            int oy = blockPlaced.getY();
            int oz = blockPlaced.getZ();

            for (int cx = -plugin.spongeRadius; cx <= plugin.spongeRadius; cx++) {
                for (int cy = -plugin.spongeRadius; cy <= plugin.spongeRadius; cy++) {
                    for (int cz = -plugin.spongeRadius; cz <= plugin.spongeRadius; cz++) {
                        int id = world.getBlockTypeIdAt(ox + cx, oy + cy, oz + cz);
                        if (id == 8 || id == 9) {
                            world.getBlockAt(ox + cx, oy + cy, oz + cz)
                                    .setTypeId(0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when a player right clicks a block
     *
     * @param event Relevant event details
     */
    @Override
    public void onBlockRightClick(BlockRightClickEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.useRegions && event.getItemInHand().getTypeId() == plugin.regionWand) {
            Vector pt = toVector(event.getBlock());
            ApplicableRegionSet app = plugin.regionManager.getApplicableRegions(pt);
            List<String> regions = plugin.regionManager.getApplicableRegionsIDs(pt);
            
            if (regions.size() > 0) {
                player.sendMessage(ChatColor.YELLOW + "Can you build? "
                        + (app.canBuild(plugin.wrapPlayer(player)) ? "Yes" : "No"));
                
                StringBuilder str = new StringBuilder();
                for (Iterator<String> it = regions.iterator(); it.hasNext(); ) {
                    str.append(it.next());
                    if (it.hasNext()) {
                        str.append(", ");
                    }
                }
                
                player.sendMessage(ChatColor.YELLOW + "Applicable regions: " + str.toString());
            } else {
                player.sendMessage(ChatColor.YELLOW + "WorldGuard: No defined regions here!");
            }
        }
    }
}
