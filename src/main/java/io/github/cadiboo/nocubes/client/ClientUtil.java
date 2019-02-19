package io.github.cadiboo.nocubes.client;

import io.github.cadiboo.nocubes.config.ModConfig;
import io.github.cadiboo.nocubes.util.IIsSmoothable;
import io.github.cadiboo.nocubes.util.ReflectionHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockModelRenderer.AmbientOcclusionFace;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.ChunkRenderTask;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ReportedException;
import net.minecraft.fluid.IFluidState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper.UnknownConstructorException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Random;

import static io.github.cadiboo.renderchunkrebuildchunkhooks.util.Utils.renderChunk_preRenderBlocks;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static net.minecraft.util.BlockRenderLayer.CUTOUT;
import static net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
import static net.minecraft.util.EnumFacing.DOWN;
import static net.minecraft.util.EnumFacing.EAST;
import static net.minecraft.util.EnumFacing.NORTH;
import static net.minecraft.util.EnumFacing.SOUTH;
import static net.minecraft.util.EnumFacing.UP;
import static net.minecraft.util.EnumFacing.WEST;
import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.getPositionRandom;

/**
 * Util that is only used on the Physical Client i.e. Rendering code
 *
 * @author Cadiboo
 */
@SuppressWarnings("WeakerAccess")
@OnlyIn(Dist.CLIENT)
public final class ClientUtil {

	/**
	 * The order of {@link EnumFacing} and null used in {@link #getQuad(IBlockState, BlockPos, BlockRendererDispatcher)}
	 */
	public static final EnumFacing[] ENUMFACING_QUADS_ORDERED = {
			UP, null, DOWN, NORTH, EAST, SOUTH, WEST,
	};

	// add or subtract from the sprites UV location to remove transparent lines in between textures
	public static final float UV_CORRECT = 1 / 10000F;

//	private static final ThreadLocal<HashMap<BlockPos, HashMap<BlockPos, Object[]>>> RENDER_LIQUID_POSITIONS = ThreadLocal.withInitial(HashMap::new);

	private static final Constructor<AmbientOcclusionFace> ambientOcclusionFace;
	private static final boolean ambientOcclusionFaceNeedsBlockModelRenderer;
	static {
		Constructor<AmbientOcclusionFace> ambientOcclusionFaceConstructor = null;
		boolean needsBlockModelRenderer = false;
		try {
			try {
				//TODO: stop using ReflectionHelper
				ambientOcclusionFaceConstructor = ReflectionHelper.findConstructor(AmbientOcclusionFace.class);
			} catch (UnknownConstructorException e) {
				//TODO: stop using ReflectionHelper
				ambientOcclusionFaceConstructor = ReflectionHelper.findConstructor(AmbientOcclusionFace.class, BlockModelRenderer.class);
				needsBlockModelRenderer = true;
			}
		} catch (Exception e) {
			final CrashReport crashReport = new CrashReport("Unable to find constructor for BlockModelRenderer$AmbientOcclusionFace", e);
			crashReport.makeCategory("Finding Constructor");
			throw new ReportedException(crashReport);
		}
		ambientOcclusionFace = ambientOcclusionFaceConstructor;
		ambientOcclusionFaceNeedsBlockModelRenderer = needsBlockModelRenderer;
	}

	/**
	 * @param red   the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @param green the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @param blue  the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @return the color in ARGB format
	 */
	public static int color(int red, int green, int blue) {

		red = clamp(red, 0x00, 0xFF);
		green = clamp(green, 0x00, 0xFF);
		blue = clamp(blue, 0x00, 0xFF);

		final int alpha = 0xFF;

		// 0x alpha red green blue
		// 0xaarrggbb

		// int colorRGBA = 0;
		// colorRGBA |= red << 16;
		// colorRGBA |= green << 8;
		// colorRGBA |= blue << 0;
		// colorRGBA |= alpha << 24;

		return blue | red << 16 | green << 8 | alpha << 24;

	}

	/**
	 * @param red   the red value of the color, 0F and 1F
	 * @param green the green value of the color, 0F and 1F
	 * @param blue  the blue value of the color, 0F and 1F
	 * @return the color in ARGB format
	 */
	public static int colorf(final float red, final float green, final float blue) {
		final int redInt = max(0, min(255, round(red * 255)));
		final int greenInt = max(0, min(255, round(green * 255)));
		final int blueInt = max(0, min(255, round(blue * 255)));
		return color(redInt, greenInt, blueInt);
	}

	public static int getLightmapSkyLightCoordsFromPackedLightmapCoords(int packedLightmapCoords) {
		return (packedLightmapCoords >> 16) & 0xFFFF; // get upper 4 bytes
	}

	public static int getLightmapBlockLightCoordsFromPackedLightmapCoords(int packedLightmapCoords) {
		return packedLightmapCoords & 0xFFFF; // get lower 4 bytes
	}

	/**
	 * Gets The first quad of a model for a pos & state or null if the model has no quads
	 *
	 * @param state                   the state
	 * @param pos                     the position used in {@link MathHelper#getPositionRandom(Vec3i)}
	 * @param blockRendererDispatcher the {@link BlockRendererDispatcher} to get the model from
	 * @return The first quad or null if the model has no quads
	 */
	@Nullable
	public static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final BlockRendererDispatcher blockRendererDispatcher) {
		final Random posRand = new Random(getPositionRandom(pos));
		final IBakedModel model = blockRendererDispatcher.getModelForState(state);
		return getQuad(state, pos, posRand, model, ENUMFACING_QUADS_ORDERED);
	}

	/**
	 * Gets The first quad of a model for a pos & state or null if the model has no quads
	 *
	 * @param state                   the state
	 * @param pos                     the position used in {@link MathHelper#getPositionRandom(Vec3i)}
	 * @param blockRendererDispatcher the {@link BlockRendererDispatcher} to get the model from
	 * @param facing                  the {@link EnumFacing to check first}
	 * @return The first quad or null if the model has no quads
	 */
	@Nullable
	public static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final BlockRendererDispatcher blockRendererDispatcher, EnumFacing facing) {
		final Random posRand = new Random(getPositionRandom(pos));
		final IBakedModel model = blockRendererDispatcher.getModelForState(state);
		final BakedQuad quad = getQuad(state, pos, posRand, model, facing);
		if (quad != null) {
			return quad;
		} else {
			return getQuad(state, pos, posRand, model, ENUMFACING_QUADS_ORDERED);
		}
	}

	/**
	 * helper method to actually get the quads
	 */
	@Nullable
	private static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final Random posRand, final IBakedModel model, final EnumFacing... facings) {
		for (EnumFacing facing : facings) {
			final List<BakedQuad> quads = model.getQuads(state, facing, posRand);
			if (!quads.isEmpty()) {
				return quads.get(0);
			}
		}
		return null;
	}

	/**
	 * Gets the color of a quad through a block at a pos
	 *
	 * @param quad  the quad
	 * @param state the state
	 * @param cache the cache
	 * @param pos   the pos
	 * @return the color
	 */
	public static int getColor(final BakedQuad quad, final IBlockState state, final IWorldReader cache, final BlockPos pos) {
		final int red;
		final int green;
		final int blue;

		if (quad.hasTintIndex()) {
			final int colorMultiplier = Minecraft.getInstance().getBlockColors().getColor(state, cache, pos, 0);
			red = (colorMultiplier >> 16) & 255;
			green = (colorMultiplier >> 8) & 255;
			blue = colorMultiplier & 255;
		} else {
			red = 0xFF;
			green = 0xFF;
			blue = 0xFF;
		}
		return color(red, green, blue);
	}

	/**
	 * Gets the fixed minimum U coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed minimum U coordinate to use when rendering the sprite
	 */
	public static float getMinU(final TextureAtlasSprite sprite) {
		return sprite.getMinU() + UV_CORRECT;
	}

	/**
	 * Gets the fixed maximum U coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed maximum U coordinate to use when rendering the sprite
	 */
	public static float getMaxU(final TextureAtlasSprite sprite) {
		return sprite.getMaxU() - UV_CORRECT;
	}

	/**
	 * Gets the fixed minimum V coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed minimum V coordinate to use when rendering the sprite
	 */
	public static float getMinV(final TextureAtlasSprite sprite) {
		return sprite.getMinV() + UV_CORRECT;
	}

	/**
	 * Gets the fixed maximum V coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed maximum V coordinate to use when rendering the sprite
	 */
	public static float getMaxV(final TextureAtlasSprite sprite) {
		return sprite.getMaxV() - UV_CORRECT;
	}

	//TODO
	private static final int[][] OFFSETS_ORDERED = {
			// check 6 immediate neighbours
			{+0, -1, +0},
			{+0, +1, +0},
			{+1, +0, +0},
			{-1, +0, +0},
			{+0, +0, +1},
			{+0, +0, -1},
			// check 8 corner neighbours
			{+1, +1, +1},
			{+1, +1, -1},
			{-1, +1, +1},
			{-1, +1, -1},
			{+1, -1, +1},
			{+1, -1, -1},
			{-1, -1, +1},
			{-1, -1, -1},
//			// check 6 immediate neighbours
//			{0, -1, 0},
//			{0, +1, 0},
//			{-1, 0, 0},
//			{+1, 0, 0},
//			{0, 0, -1},
//			{0, 0, +1},
//			// check 8 corner neighbours
//			{-1, -1, -1},
//			{-1, -1, +1},
//			{+1, -1, -1},
//			{+1, -1, +1},
//			{-1, +1, -1},
//			{-1, +1, +1},
//			{+1, +1, -1},
//			{+1, +1, +1},
	};

	/**
	 * @param cache
	 * @param pos
	 * @param state
	 * @param pooledMutableBlockPos
	 * @return a state and a texture pos which is guaranteed to be immutable
	 */
	//TODO: state cache?
	public static Object[] getTexturePosAndState(
			@Nonnull final IWorldReader cache,
			@Nonnull final BlockPos pos,
			@Nonnull final IBlockState state,
			@Nonnull final IIsSmoothable isStateSmoothable,
			@Nonnull PooledMutableBlockPos pooledMutableBlockPos
	) {

		IBlockState textureState = state;
		BlockPos texturePos = pos;

		//check pos first
		if (isStateSmoothable.isSmoothable(cache.getBlockState(pos))) {
			return new Object[]{
					texturePos,
					textureState
			};
		}

		final int x = pos.getX();
		final int y = pos.getY();
		final int z = pos.getZ();

//			if (ModConfig.beautifyTexturesLevel == FANCY) {
//
//				for (int[] withOffset : OFFSETS_ORDERED) {
//					final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + withOffset[0], y + withOffset[1], z + withOffset[2]));
//					if (tempState.getBlock() == Blocks.SNOW_LAYER) {
//						textureState = tempState;
//						texturePos = pooledMutableBlockPos.toImmutable();
//						break;
//					}
//				}
//
//				for (int[] withOffset : OFFSETS_ORDERED) {
//					final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + withOffset[0], y + withOffset[1], z + withOffset[2]));
//					if (tempState.getBlock() == Blocks.GRASS) {
//						textureState = tempState;
//						texturePos = pooledMutableBlockPos.toImmutable();
//						break;
//					}
//				}
//			}

		for (int[] offset : OFFSETS_ORDERED) {
			final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + offset[0], y + offset[1], z + offset[2]));
			if (isStateSmoothable.isSmoothable(tempState)) {
				textureState = tempState;
				texturePos = pooledMutableBlockPos.toImmutable();
				break;
			}
		}

		return new Object[]{
				texturePos,
				textureState
		};

	}

	public static AmbientOcclusionFace makeAmbientOcclusionFace() {
		try {
			if (ambientOcclusionFaceNeedsBlockModelRenderer) {
				return ambientOcclusionFace.newInstance(Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelRenderer());
			} else {
				return ambientOcclusionFace.newInstance();
			}
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			CrashReport crashReport = new CrashReport("Instantiating BlockModelRenderer$AmbientOcclusionFace!", e);
			final CrashReportCategory crashReportCategory = crashReport.makeCategory("Reflectively Accessing BlockModelRenderer$AmbientOcclusionFace");
			crashReportCategory.addDetail("Needs BlockModelRenderer", ambientOcclusionFaceNeedsBlockModelRenderer);
			throw new ReportedException(crashReport);
		}
	}

	public static BlockRenderLayer getRenderLayer(IBlockState state) {
		final BlockRenderLayer blockRenderLayer = state.getBlock().getRenderLayer();
		switch (blockRenderLayer) {
			default:
			case SOLID:
			case TRANSLUCENT:
				return blockRenderLayer;
			case CUTOUT_MIPPED:
				return Minecraft.getInstance().gameSettings.mipmapLevels == 0 ? CUTOUT : CUTOUT_MIPPED;
			case CUTOUT:
				return Minecraft.getInstance().gameSettings.mipmapLevels != 0 ? CUTOUT_MIPPED : CUTOUT;
		}
	}

	public static BlockRenderLayer getRenderLayer(IFluidState state) {
		final BlockRenderLayer blockRenderLayer = state.getRenderLayer();
		switch (blockRenderLayer) {
			default:
			case SOLID:
			case TRANSLUCENT:
				return blockRenderLayer;
			case CUTOUT_MIPPED:
				return Minecraft.getInstance().gameSettings.mipmapLevels == 0 ? CUTOUT : CUTOUT_MIPPED;
			case CUTOUT:
				return Minecraft.getInstance().gameSettings.mipmapLevels != 0 ? CUTOUT_MIPPED : CUTOUT;
		}
	}

	public static BufferBuilder startOrContinueBufferBuilder(final ChunkRenderTask generator, final int blockRenderLayerOrdinal, final CompiledChunk compiledChunk, final BlockRenderLayer blockRenderLayer, RenderChunk renderChunk, BlockPos renderChunkPosition) {
		final BufferBuilder bufferBuilder = generator.getRegionRenderCacheBuilder().getBuilder(blockRenderLayerOrdinal);
		if (!compiledChunk.isLayerStarted(blockRenderLayer)) {
			compiledChunk.setLayerStarted(blockRenderLayer);
			renderChunk_preRenderBlocks(renderChunk, bufferBuilder, renderChunkPosition);
		}
		return bufferBuilder;
	}

	public static int getExtendLiquidsRange() {
		switch (ModConfig.extendLiquids) {
			default:
			case Off:
				return 0;
			case OneBlock:
				return 1;
			case TwoBlocks:
				return 2;
		}
	}

}
