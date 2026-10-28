package de.exit.luckyoneblock.logic;

import de.exit.luckyoneblock.LuckyOneBlockMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
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
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    if (serverPlayer.getY() < 65.0) {
                        serverPlayer.teleport(serverWorld, 0.5, 65.0, 0.5, Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
                        serverPlayer.setVelocity(serverPlayer.getVelocity().x, 0, serverPlayer.getVelocity().z);
                        serverPlayer.fallDistance = 0;
                    }
                }
            }
        });

        // 2. Kontinuierlicher Schutz & Fallschutz in jedem Server-Tick
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                // Schutz gegen Explosionen (TNT / Creeper): sofortige Wiederherstellung falls Luft
                if (world.getBlockState(ONE_BLOCK_POS).isAir()) {
                    world.setBlockState(ONE_BLOCK_POS, getLuckyBlockState(), Block.NOTIFY_ALL);
                }

                // Fallschutz: Spieler, die unter Y = 64 fallen, werden sanft auf den Lucky Block gerettet
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (player.getY() < 64.0 && !player.isSpectator()) {
                        player.teleport(world, 0.5, 65.0, 0.5, Set.of(), player.getYaw(), player.getPitch());
                        player.setVelocity(0, 0, 0);
                        player.fallDistance = 0;
                        world.playSound(null, ONE_BLOCK_POS, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.2f);
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
