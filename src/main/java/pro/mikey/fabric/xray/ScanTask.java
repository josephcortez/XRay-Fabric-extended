package pro.mikey.fabric.xray;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.Nullable;
import pro.mikey.fabric.xray.cache.BlockSearchEntry;
import pro.mikey.fabric.xray.records.BasicColor;
import pro.mikey.fabric.xray.records.BlockPosWithColor;
import pro.mikey.fabric.xray.render.RenderOutlines;
import pro.mikey.fabric.xray.storage.Stores;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ScanTask implements Runnable {
    private static AtomicBoolean isScanning = new AtomicBoolean(false);

    ScanTask() {
    }

    /**
     * Check if we're valid and push back the blocks color for the render queue
     */
    @Nullable
    public static BasicColor isValidBlock(BlockPos pos, World world, Set<BlockSearchEntry> blocks) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }

        if (Stores.SETTINGS.get().isShowLava() && state.getFluidState().getFluid() instanceof LavaFluid) {
            return new BasicColor(210, 10, 10);
        }

        BlockState defaultState = state.getBlock().getDefaultState();

        return blocks.stream()
                .filter(localState -> localState.isDefault() && defaultState == localState.getState() || !localState.isDefault() && state == localState.getState())
                .findFirst()
                .map(BlockSearchEntry::getColor)
                .orElse(null);
    }

    @Override
    public void run() {
        if (isScanning.get()) {
            return;
        }

        isScanning.set(true);
        Set<BlockPosWithColor> c = this.collectBlocks();
        ScanController.renderQueue.clear();
        ScanController.renderQueue.addAll(c);
        isScanning.set(false);
        RenderOutlines.requestedRefresh.set(true);
    }

    /**
     * This is an "exact" copy from the forge version of the mod but with the optimisations that the
     * rewrite (Fabric) version has brought like chunk location based cache, etc.
     *
     * <p>This is only run if the cache is invalidated.
     *
     * @implNote Using the {@link BlockPos#iterate(BlockPos, BlockPos)} may be a better system for the
     * scanning.
     */
    private Set<BlockPosWithColor> collectBlocks() {
        Set<BlockSearchEntry> blocks = Stores.BLOCKS.getCache().get();

        // If we're not looking for blocks, don't run.
        if (blocks.isEmpty() && !Stores.SETTINGS.get().isShowLava()) {
            if (!ScanController.renderQueue.isEmpty()) {
                ScanController.renderQueue.clear();
            }
            return new HashSet<>();
        }

        MinecraftClient instance = MinecraftClient.getInstance();

        final World world = instance.world;
        final PlayerEntity player = instance.player;

        // Just stop if we can't get the player or world.
        if (world == null || player == null) {
            return new HashSet<>();
        }

        final Set<BlockPosWithColor> renderQueue = new HashSet<>();

        int pX = player.getBlockX();
        int pZ = player.getBlockZ();

        int range = StateSettings.getHalfRange();

        int height = Arrays.stream(world.getChunk(player.getBlockPos()).getSectionArray())
                .filter(Objects::nonNull)
                .mapToInt(ChunkSection::getYOffset)
                .max()
                .orElse(0);

        for (int h = world.getBottomY(); h < height + (1 << 4); h++) {
            for (int i = pX - range; i <= pX + range; i++) {
                for(int j = pZ - range; j <= pZ + range; j++) {
                    BlockPos pos = new BlockPos(i, h, j);
                    BasicColor validBlock = isValidBlock(pos, world, blocks);
                    if (validBlock != null) {
                        renderQueue.add(new BlockPosWithColor(pos, validBlock));
                    }
                    // XRay.LOGGER.info("Height: " + h + ", validBlock: " + validBlock);
                }
            }
        }

        // O(N^5) wtf
        /*for (int i = cX - range; i <= cX + range; i++) {
            int chunkStartX = i << 4;
            for (int j = cZ - range; j <= cZ + range; j++) {
                int chunkStartZ = j << 4;

                int height = Arrays.stream(world.getChunk(i, j).getSectionArray())
                        .filter(Objects::nonNull)
                        .mapToInt(ChunkSection::getYOffset)
                        .max()
                        .orElse(0);

                for (int k = chunkStartX; k < chunkStartX + 16; k++) {
                    for (int l = chunkStartZ; l < chunkStartZ + 16; l++) {
                        for (int m = world.getBottomY(); m < height + (1 << 4); m++) {
                            BlockPos pos = new BlockPos(k, m, l);
                            BasicColor validBlock = isValidBlock(pos, world, blocks);
                            if (validBlock != null) {
                                renderQueue.add(new BlockPosWithColor(pos, validBlock));
                            }
                        }
                    }
                }
            }
        }*/

        return renderQueue;
    }
}
