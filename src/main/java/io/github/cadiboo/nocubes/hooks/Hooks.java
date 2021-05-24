package io.github.cadiboo.nocubes.hooks;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.client.render.MeshRenderer;
import io.github.cadiboo.nocubes.collision.CollisionHandler;
import io.github.cadiboo.nocubes.config.NoCubesConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.ChunkRender.RebuildTask;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * @author Cadiboo
 */
@SuppressWarnings("unused") // Hooks are called with ASM
public final class Hooks {

	/**
	 * Called from: {@link RebuildTask#compile} right before the BlockPos.getAllInBoxMutable iteration
	 * Calls: {@link MeshRenderer#renderChunk} to render our <del>fluids and</del> smooth terrain
	 */
	@OnlyIn(Dist.CLIENT)
	public static void preIteration(RebuildTask rebuildTask, ChunkRenderDispatcher.ChunkRender chunkRender, ChunkRenderDispatcher.CompiledChunk compiledChunkIn, RegionRenderCacheBuilder builderIn, BlockPos blockpos, IBlockDisplayReader chunkrendercache, MatrixStack matrixstack, Random random, BlockRendererDispatcher blockrendererdispatcher) {
		SelfCheck.preIteration = true;
		MeshRenderer.renderChunk(rebuildTask, chunkRender, compiledChunkIn, builderIn, blockpos, chunkrendercache, matrixstack, random, blockrendererdispatcher);
	}

	/**
	 * Called from: {@link RebuildTask#compile} right before BlockState#getRenderType is called
	 * <p>
	 * Hooking this disables vanilla rendering for smoothable BlockStates.
	 *
	 * @return If the state can render
	 */
	@OnlyIn(Dist.CLIENT)
	public static boolean canBlockStateRender(BlockState state) {
		SelfCheck.canBlockStateRender = true;
		return !NoCubesConfig.Client.render || !NoCubes.smoothableHandler.isSmoothable(state);
	}

	/**
	 * Called from: {@link BlockRendererDispatcher#renderBlockDamage} before any other logic
	 * Calls: {@link MeshRenderer#renderSmoothBlockDamage} if the blockstate is smoothable
	 * <p>
	 * Renders our own smoothed cracking/breaking/damage animation.
	 *
	 * @return If normal rendering should be cancelled (i.e. normal rendering should NOT happen)
	 */
	@OnlyIn(Dist.CLIENT)
	public static boolean renderBlockDamage(BlockRendererDispatcher dispatcher, BlockState state, BlockPos pos, IBlockDisplayReader world, MatrixStack matrix, IVertexBuilder buffer, IModelData modelData) {
		SelfCheck.renderBlockDamage = true;
		if (!NoCubesConfig.Client.render || !NoCubes.smoothableHandler.isSmoothable(state))
			return false;
		MeshRenderer.renderSmoothBlockDamage(dispatcher, state, pos, world, matrix, buffer, modelData);
		return true;
	}

	/**
	 * Called from: {@link BlockState#getCollisionShape(IBlockReader, BlockPos)}} before any other logic
	 * <p>
	 * Hooking this makes that collisions work for blockstates with a cache.
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable VoxelShape getCollisionShapeOverride(BlockState state, IBlockReader world, BlockPos pos) {
		SelfCheck.getCollisionShapeNoContextOverride = true;
		if (NoCubesConfig.Server.collisionsEnabled && NoCubes.smoothableHandler.isSmoothable(state))
			return CollisionHandler.getCollisionShape(state, world, pos, ISelectionContext.empty());
		return null;
	}

	/**
	 * Called from: {@link BlockState#getCollisionShape(IBlockReader, BlockPos, ISelectionContext)}} before any other logic
	 * <p>
	 * Hooking this makes collisions work.
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable VoxelShape getCollisionShapeOverride(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
		SelfCheck.getCollisionShapeWithContextOverride = true;
		if (NoCubesConfig.Server.collisionsEnabled && NoCubes.smoothableHandler.isSmoothable(state))
			return CollisionHandler.getCollisionShape(state, world, pos, context);
		return null;
	}

	/**
	 * Called from: {@link BlockState#isCollisionShapeFullBlock(IBlockReader, BlockPos)}} before any other logic
	 * <p>
	 * Hooking this makes collisions work for normally solid blocks like stone.
	 * <p>
	 * TODO: This is used by {@link Block#getShadeBrightness(BlockState, IBlockReader, BlockPos)} so always returning false breaks AO when collisions are on.
	 * Possible fix: Check if we are on the server or the client thread before running the check?
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable Boolean isCollisionShapeFullBlockOverride(BlockState state, IBlockReader reader, BlockPos pos) {
		SelfCheck.isCollisionShapeFullBlockOverride = true;
		if (NoCubesConfig.Server.collisionsEnabled && NoCubes.smoothableHandler.isSmoothable(state))
			return false;
		return null;
	}

	/**
	 * Called from: {@link BlockState#hasLargeCollisionShape()} before any other logic
	 * <p>
	 * Hooking this somehow stops us falling through 1 block wide holes and under the ground.
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable Boolean hasLargeCollisionShapeOverride(BlockState state) {
		SelfCheck.hasLargeCollisionShapeOverride = true;
		if (NoCubesConfig.Server.collisionsEnabled && NoCubes.smoothableHandler.isSmoothable(state))
			return false;
		return null;
	}

	/**
	 * Called from: {@link BlockState#isSuffocating(IBlockReader, BlockPos)} before any other logic
	 * <p>
	 * Hooking this stops grass path collisions being broken.
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable Boolean isSuffocatingOverride(BlockState state, IBlockReader world, BlockPos pos) {
		SelfCheck.isSuffocatingOverride = true;
		if (NoCubesConfig.Server.collisionsEnabled && NoCubes.smoothableHandler.isSmoothable(state))
			return false;
		return null;
	}

	/**
	 * Called from: {@link BlockState#canOcclude()} before any other logic
	 * Called from: {@link BlockState#isCacheOpaqueCube()} (OptiFine) before any other logic
	 * <p>
	 * Hooking this makes {@link Block#shouldRenderFace(BlockState, IBlockReader, BlockPos, Direction)} return true and
	 * causes cubic terrain (including fluids) to be rendered when they are up against smooth terrain, stopping us from
	 * being able to see through the ground near smooth terrain.
	 *
	 * @return A value to override vanilla's handing or <code>null</code> to use vanilla's handing
	 */
	public static @Nullable Boolean canOccludeOverride(BlockState state) {
		SelfCheck.canOccludeOverride = true;
		if (NoCubesConfig.Client.render && NoCubes.smoothableHandler.isSmoothable(state))
			return false;
		return null;
	}

	/**
	 * Called from: {@link ClientWorld#setBlocksDirty(BlockPos, BlockState, BlockState) setBlocksDirty (MCP markForRerender)} before any other logic
	 * <p>
	 * The method 'setBlocksDirty' gets called when a block is updated and marked for re-render.
	 * Calls {@link WorldRenderer#setBlocksDirty(int, int, int, int, int, int)}  with a range of 2 instead of the normal 1.
	 * This fixes seams that appear when meshes along chunk borders change.
	 */
	@OnlyIn(Dist.CLIENT)
	public static void markForRerender(Minecraft minecraft, WorldRenderer worldRenderer, BlockPos pos, BlockState oldState, BlockState newState) {
		SelfCheck.markForRerender = true;
		if (minecraft.getModelManager().requiresRender(oldState, newState)) {
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			int extension = NoCubesConfig.Client.render ? 2 : 1;
			worldRenderer.setBlocksDirty(x - extension, y - extension, z - extension, x + extension, y + extension, z + extension);
		}
	}

//	/**
//	 * Called from: World#getFluidState after the bounds check in place of the normal getFluidState logic
//	 * Calls: ModUtil.getFluidState to handle extended fluids
//	 *
//	 * @return An IFluidState
//	 */
//	public static IFluidState getFluidState(final World world, final BlockPos pos) {
//		return ModUtil.getFluidState(world, pos);
//	}
//
//	/**
//	 * Called from: IWorldReader#getCollisionShapes(Entity, AxisAlignedBB) after the ISelectionContext is generated
//	 * Calls: CollisionHandler.getCollisionShapes to handle mesh, repose and vanilla collisions
//	 *
//	 * @return The collisions for the entity
//	 */
//	public static Stream<VoxelShape> getCollisionShapes(final IWorldReader _this, final Entity p_217352_1_, final AxisAlignedBB p_217352_2_, final int i, final int j, final int k, final int l, final int i1, final int j1, final ISelectionContext iselectioncontext) {
//		return CollisionHandler.getCollisionShapes(_this, p_217352_1_, p_217352_2_, i, j, k, l, i1, j1, iselectioncontext);
//	}
//
//	/**
//	 * Called from: ChunkRenderCache#<init> right after ChunkRenderCache#cacheStartPos is set
//	 * Calls: ClientUtil.setupChunkRenderCache to set up the cache in an optimised way
//	 */
//	@OnlyIn(Dist.CLIENT)
//	public static void initChunkRenderCache(final ChunkRenderCache _this, final int chunkStartX, final int chunkStartZ, final Chunk[][] chunks, final BlockPos start, final BlockPos end) {
//		ClientUtil.setupChunkRenderCache(_this, chunkStartX, chunkStartZ, chunks, start, end);
//	}
//
//	/**
//	 * Called from: VoxelShapes.getAllowedOffset(AxisAlignedBB, IWorldReader, double, ISelectionContext, AxisRotation, Stream) before the MutableBlockPos is created
//	 * Calls: VoxelShapesHandler.getAllowedOffset to handle mesh, repose and vanilla collisions offsets
//	 */
//	public static double getAllowedOffset(final AxisAlignedBB collisionBox, final IWorldReader worldReader, final double desiredOffset, final ISelectionContext selectionContext, final AxisRotation rotationAxis, final Stream<VoxelShape> possibleHits, final AxisRotation reversedRotation, final Direction.Axis rotX, final Direction.Axis rotY, final Direction.Axis rotZ) {
//		return VoxelShapesHandler.getAllowedOffset(collisionBox, worldReader, desiredOffset, selectionContext, rotationAxis, possibleHits, reversedRotation, rotX, rotY, rotZ);
//	}

	/**
	 * Load classes that we modify to get errors sooner.
	 */
	public static void loadClasses(final Dist dist) {
		loadClass("net.minecraft.block.AbstractBlock$AbstractBlockState");
		loadClass("net.minecraft.block.BlockState");
		if (dist.isClient()) {
			loadClass("net.minecraft.client.renderer.BlockRendererDispatcher");
			loadClass("net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$ChunkRender$RebuildTask");
//		} else {

		}
	}

	private static void loadClass(final String className) {
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try {
			Class.forName(className, false, loader);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to load class \"" + className + "\", probably a coremod issue", e);
		}
		try {
			Class.forName(className, true, loader);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to initialise class \"" + className + "\", probably a coremod issue", e);
		}
	}

}
