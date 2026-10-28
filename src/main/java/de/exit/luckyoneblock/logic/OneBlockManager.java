package de.exit.luckyoneblock.logic;

import de.exit.luckyoneblock.LuckyOneBlockMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.Set;

public final class OneBlockManager {
    public static final BlockPos ONE_BLOCK_POS = new BlockPos(0, 64, 0);
    private static BlockState cachedLuckyState = null;

    private OneBlockManager() {}

    public static void register() {
        // 1. Sofortiges Neu-Platzieren beim Abbau (im selben Server-Tick)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld && isOneBlock(serverWorld, pos)) {
                serverWorld.setBlockState(ONE_BLOCK_POS, getLuckyBlockState(), Block.NOTIFY_ALL);

                // Spieler-Position stabilisieren, falls nötig
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    if (serverPlayer.getY() < 65.0) {
                        serverPlayer.teleport(serverWorld, 0.5, 65.0, 0.5, Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
                        serverPlayer.setVelocity(serverPlayer.getVelocity().x, 0, serverPlayer.getVelocity().z);
                        serverPlayer.fallDistance = 0;
                    }
                }

                // Neu gespawnte Drops direkt zentriert über dem Block platzieren
                Box dropBox = new Box(ONE_BLOCK_POS).expand(2.5);
                for (ItemEntity item : serverWorld.getEntitiesByClass(ItemEntity.class, dropBox, Entity::isAlive)) {
                    item.setPosition(0.5, 65.4, 0.5);
                    item.setVelocity(0, 0.1, 0);
                }
            }
        });

        // 2. Kontinuierlicher Schutz, Item-Magnet (Saugen) & Fallschutz in jedem Server-Tick
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                // Schutz gegen Explosionen (TNT / Creeper): sofortige Wiederherstellung falls Luft
                if (world.getBlockState(ONE_BLOCK_POS).isAir()) {
                    world.setBlockState(ONE_BLOCK_POS, getLuckyBlockState(), Block.NOTIFY_ALL);
                }

                // Fallschutz für Spieler
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (player.getY() < 64.0 && !player.isSpectator()) {
                        player.teleport(world, 0.5, 65.0, 0.5, Set.of(), player.getYaw(), player.getPitch());
                        player.setVelocity(0, 0, 0);
                        player.fallDistance = 0;
                        world.playSound(null, ONE_BLOCK_POS, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.2f);
                    }
                }

                // Item-Sauger / Magnet um den OneBlock (Radius 12 Blöcke)
                Box magnetBox = new Box(ONE_BLOCK_POS).expand(12.0);
                for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, magnetBox, Entity::isAlive)) {
                    // Verhindern, dass Items jemals ins Void fallen
                    if (item.getY() < 64.0) {
                        item.setPosition(0.5, 65.4, 0.5);
                        item.setVelocity(0, 0.1, 0);
                    }

                    PlayerEntity target = world.getClosestPlayer(item.getX(), item.getY(), item.getZ(), 12.0, false);
                    if (target != null && !target.isSpectator()) {
                        double dx = target.getX() - item.getX();
                        double dy = (target.getY() + 0.6) - item.getY();
                        double dz = target.getZ() - item.getZ();
                        double distSq = dx * dx + dy * dy + dz * dz;

                        if (distSq > 0.01) {
                            double dist = Math.sqrt(distSq);
                            double speed = 0.45;
                            item.setVelocity(dx / dist * speed, dy / dist * speed, dz / dist * speed);
                        }
                    } else {
                        // Falls kein Spieler da: Items über dem Block halten
                        double dx = 0.5 - item.getX();
                        double dz = 0.5 - item.getZ();
                        if (dx * dx + dz * dz > 0.3) {
                            item.setVelocity(dx * 0.3, 0.05, dz * 0.3);
                        }
                    }
                }

                // Erfahrungspunkte (XP-Orbs) ebenfalls zum Spieler saugen
                for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, magnetBox, Entity::isAlive)) {
                    if (orb.getY() < 64.0) {
                        orb.setPosition(0.5, 65.4, 0.5);
                        orb.setVelocity(0, 0.1, 0);
                    }
                    PlayerEntity target = world.getClosestPlayer(orb.getX(), orb.getY(), orb.getZ(), 12.0, false);
                    if (target != null && !target.isSpectator()) {
                        double dx = target.getX() - orb.getX();
                        double dy = (target.getY() + 0.6) - orb.getY();
                        double dz = target.getZ() - orb.getZ();
                        double distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq > 0.01) {
                            double dist = Math.sqrt(distSq);
                            double speed = 0.45;
                            orb.setVelocity(dx / dist * speed, dy / dist * speed, dz / dist * speed);
                        }
                    }
                }
            }
        });

        // 3. Beim Laden der Welt sicherstellen, dass der Lucky Block existiert
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD && world.getBlockState(ONE_BLOCK_POS).isAir()) {
                world.setBlockState(ONE_BLOCK_POS, getLuckyBlockState(), Block.NOTIFY_ALL);
                LuckyOneBlockMod.LOGGER.info("[LuckyOneBlock] Initialized Simple Lucky Block at {}", ONE_BLOCK_POS);
            }
        });

        // 4. Beim Beitreten Spieler sicher auf dem Block positionieren
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ServerWorld world = player.getServerWorld();
            if (world.getRegistryKey() == World.OVERWORLD && player.getY() < 60.0) {
                player.teleport(world, 0.5, 65.0, 0.5, Set.of(), 0.0f, 0.0f);
            }
        });
    }

    public static boolean isOneBlock(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey() == World.OVERWORLD && ONE_BLOCK_POS.equals(pos);
    }

    public static BlockState getLuckyBlockState() {
        if (cachedLuckyState != null) {
            return cachedLuckyState;
        }
        try {
            Class<?> clazz = Class.forName("com.simpleluckyblock.ModBlocks");
            Field field = clazz.getField("LUCKY_BLOCK");
            Block block = (Block) field.get(null);
            if (block != null) {
                cachedLuckyState = block.getDefaultState();
                return cachedLuckyState;
            }
        } catch (Throwable t) {
            LuckyOneBlockMod.LOGGER.warn("[LuckyOneBlock] Could not load ModBlocks.LUCKY_BLOCK: {}", t.getMessage());
        }
        return Blocks.GOLD_BLOCK.getDefaultState();
    }
}
