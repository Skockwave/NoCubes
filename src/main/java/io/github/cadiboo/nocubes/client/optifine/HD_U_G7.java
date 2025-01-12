package io.github.cadiboo.nocubes.client.optifine;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cadiboo.nocubes.client.render.RendererDispatcher;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static io.github.cadiboo.nocubes.client.optifine.HD_U_G7.Reflect.*;
import static io.github.cadiboo.nocubes.client.optifine.Reflector.tryGetField;
import static io.github.cadiboo.nocubes.client.optifine.Reflector.tryGetMethod;

class HD_U_G7 implements OptiFineProxy {

	// From OptiFine's BlockModelRenderer
	static final RenderType[] OVERLAY_LAYERS = new RenderType[]{RenderType.cutout(), RenderType.cutoutMipped(), RenderType.translucent()};

	// If refactoring this to make a common base class, use reflection from HD_U_H5 proxy from before it was deleted 8/3/2022
	@Override
	public @Nullable String notUsableBecause() {
		for (var field : Reflect.class.getDeclaredFields()) {
			try {
				if (field.get(null) == null)
					return "reflection was unable to find " + field.getName();
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Can't access my own fields...?", e);
			}
		}
		return null;
	}

	@Override
	public void preRenderChunk(RenderChunk chunkRender, BlockPos chunkPos, PoseStack matrix) {
		// Support Render Regions
		matrix.translate(
			ChunkRender_regionDX(chunkRender),
			ChunkRender_regionDY(chunkRender),
			ChunkRender_regionDZ(chunkRender)
		);
	}

	@Override
	public long getSeed(long originalSeed) {
		return Config_isAlternateBlocks() ? 0 : originalSeed;
	}

	@Override
	public Object preRenderBlock(RenderChunk chunkRender, ChunkBufferBuilderPack builder, BlockAndTintGetter chunkCacheOF, RenderType renderType, BufferBuilder buffer, BlockState state, BlockPos pos) {
		BufferBuilder_setBlockLayer(buffer, renderType);
		Object renderEnv = BufferBuilder_getRenderEnv(buffer, state, pos);
		RenderEnv_setRegionRenderCacheBuilder(renderEnv, builder);
		ChunkCacheOF_setRenderEnv(chunkCacheOF, renderEnv);

		if (Config_isShaders()) {
			prePushShaderEntity(chunkRender, buffer, pos);
			SVertexBuilder_pushEntity(state, buffer);
		}
		return renderEnv;
	}

	protected void prePushShaderEntity(RenderChunk chunkRender, BufferBuilder buffer, BlockPos pos) {
	}

	@Override
	public Object preRenderFluid(RenderChunk chunkRender, ChunkBufferBuilderPack buffers, BlockAndTintGetter chunkCache, RenderType layer, BufferBuilder buffer, BlockState block, FluidState fluid, BlockPos worldPos) {
		return this.preRenderBlock(chunkRender, buffers, chunkCache, layer, buffer, block, worldPos);
	}

	@Override
	public BakedModel getModel(Object renderEnv, BakedModel originalModel, BlockState state) {
		return BlockModelCustomizer_getRenderModel(originalModel, state, renderEnv);
	}

	@Override
	public void postRenderBlock(Object renderEnv, BufferBuilder buffer, RenderChunk chunkRender, ChunkBufferBuilderPack builder, Set<RenderType> usedLayers) {
		if (Config_isShaders())
			SVertexBuilder_popEntity(buffer);

		if (RenderEnv_isOverlaysRendered(renderEnv)) {
			ChunkRender_postRenderOverlays(chunkRender, builder, usedLayers);
			RenderEnv_setOverlaysRendered(renderEnv, false);
		}
	}

	@Override
	public void postRenderFluid(Object renderEnv, BufferBuilder buffer, RenderChunk chunkRender, ChunkBufferBuilderPack builder, Set<RenderType> usedLayers) {
		this.postRenderBlock(renderEnv, buffer, chunkRender, builder, usedLayers);
	}

	@Override
	public @Nullable BakedQuad getQuadEmissive(BakedQuad quad) {
		return BakedQuad_getQuadEmissive(quad);
	}

	@Override
	public void preRenderQuad(Object renderEnv, BakedQuad quad, BlockState state, BlockPos pos) {
		RenderEnv_reset(renderEnv, state, pos);
	}

	@Override
	public List<BakedQuad> getQuadsAndStoreOverlays(List<BakedQuad> quads, BlockAndTintGetter world, BlockState state, BlockPos worldPos, Direction direction, RenderType layer, long rand, Object renderEnv) {
		return BlockModelCustomizer_getRenderQuads(quads, world, state, worldPos, direction, layer, rand, renderEnv);
	}

	@Override
	public int forEachOverlayQuad(RendererDispatcher.ChunkRenderInfo renderer, BlockState state, BlockPos worldPos, RendererDispatcher.ChunkRenderInfo.ColorSupplier colorSupplier, RendererDispatcher.ChunkRenderInfo.QuadConsumer action, Object renderEnv) {
		int totalSize = 0;
		for (int i = 0; i < OVERLAY_LAYERS.length; i++) {
			RenderType overlayLayer = OVERLAY_LAYERS[i];
			Object overlay = RenderEnv_getListQuadsOverlay(renderEnv, overlayLayer);
			int size = ListQuadsOverlay_size(overlay);
			if (size <= 0)
				continue;
			totalSize += size;
			BufferBuilder overlayBuffer = renderer.getAndStartBuffer(overlayLayer);
			for (int j = 0; j < size; ++j) {
				List<BakedQuad> quads = ListQuadsOverlay_getListQuadsSingle(overlay, ListQuadsOverlay_getQuad(overlay, j));
				BlockState overlayState = ListQuadsOverlay_getBlockState(overlay, j);
				renderer.forEachQuad(quads, overlayState, worldPos, colorSupplier, overlayLayer, overlayBuffer, renderEnv, action);
				RenderEnv_reset(renderEnv, overlayState, worldPos);
			}
			ListQuadsOverlay_clear(overlay);
			renderer.markLayerUsed(overlayLayer);
		}
		return totalSize;
	}

	// All reflection stuff can be null but we check beforehand
	@SuppressWarnings("ConstantConditions")
	interface Reflect {

		MethodHandle isShaders = tryGetMethod("net.optifine.Config", "isShaders");
		MethodHandle isAlternateBlocks = tryGetMethod("net.optifine.Config", "isAlternateBlocks");

		MethodHandle pushEntity = tryGetMethod("net.optifine.shaders.SVertexBuilder", "pushEntity", BlockState.class, VertexConsumer.class);
		MethodHandle popEntity = tryGetMethod("net.optifine.shaders.SVertexBuilder", "popEntity", VertexConsumer.class);

		MethodHandle postRenderOverlays = tryGetMethod(RenderChunk.class.getName(), "postRenderOverlays", ChunkBufferBuilderPack.class, Set.class);
		Field regionDX = tryGetField(RenderChunk.class.getName(), "regionDX");
		Field regionDY = tryGetField(RenderChunk.class.getName(), "regionDY");
		Field regionDZ = tryGetField(RenderChunk.class.getName(), "regionDZ");

		MethodHandle getQuadEmissive = tryGetMethod(BakedQuad.class.getName(), "getQuadEmissive");
		MethodHandle setBlockLayer = tryGetMethod(BufferBuilder.class.getName(), "setBlockLayer", RenderType.class);
		MethodHandle getRenderEnv = tryGetMethod(BufferBuilder.class.getName(), "getRenderEnv", BlockState.class, BlockPos.class);

		MethodHandle reset = tryGetMethod("net.optifine.render.RenderEnv", "reset", BlockState.class, BlockPos.class);
		MethodHandle setRegionRenderCacheBuilder = tryGetMethod("net.optifine.render.RenderEnv", "setRegionRenderCacheBuilder", ChunkBufferBuilderPack.class);
		MethodHandle isOverlaysRendered = tryGetMethod("net.optifine.render.RenderEnv", "isOverlaysRendered");
		MethodHandle setOverlaysRendered = tryGetMethod("net.optifine.render.RenderEnv", "setOverlaysRendered", boolean.class);
		MethodHandle getListQuadsOverlay = tryGetMethod("net.optifine.render.RenderEnv", "getListQuadsOverlay", RenderType.class);

		MethodHandle size = tryGetMethod("net.optifine.model.ListQuadsOverlay", "size");
		MethodHandle clear = tryGetMethod("net.optifine.model.ListQuadsOverlay", "clear");
		MethodHandle getQuad = tryGetMethod("net.optifine.model.ListQuadsOverlay", "getQuad", int.class);
		MethodHandle getBlockState = tryGetMethod("net.optifine.model.ListQuadsOverlay", "getBlockState", int.class);
		MethodHandle getListQuadsSingle = tryGetMethod("net.optifine.model.ListQuadsOverlay", "getListQuadsSingle", BakedQuad.class);

		MethodHandle setRenderEnv = tryGetMethod("net.optifine.override.ChunkCacheOF", "setRenderEnv", "net.optifine.render.RenderEnv");
		MethodHandle getRenderModel = tryGetMethod("net.optifine.model.BlockModelCustomizer", "getRenderModel", BakedModel.class, BlockState.class, "net.optifine.render.RenderEnv");
		MethodHandle getRenderQuads = tryGetMethod("net.optifine.model.BlockModelCustomizer", "getRenderQuads", List.class, BlockAndTintGetter.class, BlockState.class, BlockPos.class, Direction.class, RenderType.class, long.class, "net.optifine.render.RenderEnv");

		static boolean Config_isShaders() {
			try {
				return (boolean) isShaders.invokeExact();
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static boolean Config_isAlternateBlocks() {
			try {
				return (boolean) isAlternateBlocks.invokeExact();
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static int ChunkRender_regionDX(RenderChunk chunkRender) {
			try {
				return regionDX.getInt(chunkRender);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static int ChunkRender_regionDY(RenderChunk chunkRender) {
			try {
				return regionDY.getInt(chunkRender);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static int ChunkRender_regionDZ(RenderChunk chunkRender) {
			try {
				return regionDZ.getInt(chunkRender);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static @Nullable BakedQuad BakedQuad_getQuadEmissive(BakedQuad quad) {
			try {
				return (BakedQuad) getQuadEmissive.invokeExact(quad);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void SVertexBuilder_pushEntity(BlockState state, VertexConsumer buffer) {
			try {
				pushEntity.invokeExact(state, buffer);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void SVertexBuilder_popEntity(VertexConsumer buffer) {
			try {
				popEntity.invokeExact(buffer);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void RenderEnv_reset(Object renderEnv, BlockState state, BlockPos pos) {
//			((RenderEnv) renderEnv).reset(state, pos);
			try {
				reset.invoke(renderEnv, state, pos);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static BakedModel BlockModelCustomizer_getRenderModel(BakedModel originalModel, BlockState state, Object renderEnv) {
//			return BlockModelCustomizer.getRenderModel(originalModel, state, (RenderEnv) renderEnv);
			try {
				return (BakedModel) getRenderModel.invoke(originalModel, state, renderEnv);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static List<BakedQuad> BlockModelCustomizer_getRenderQuads(List<BakedQuad> quads, BlockAndTintGetter world, BlockState state, BlockPos worldPos, Direction direction, RenderType layer, long rand, Object renderEnv) {
//			return BlockModelCustomizer.getRenderQuads(quads, world, state, worldPos, direction, layer, rand, renderEnv)
			try {
				return (List<BakedQuad>) getRenderQuads.invoke(quads, world, state, worldPos, direction, layer, rand, renderEnv);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void ChunkCacheOF_setRenderEnv(BlockAndTintGetter chunkCacheOF, Object renderEnv) {
//			((ChunkCacheOF) chunkCacheOF).setRenderEnv((RenderEnv) renderEnv);
			try {
				setRenderEnv.invoke(chunkCacheOF, renderEnv);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void RenderEnv_setRegionRenderCacheBuilder(Object renderEnv, ChunkBufferBuilderPack builder) {
//			((RenderEnv) renderEnv).setRegionRenderCacheBuilder(builder);
			try {
				setRegionRenderCacheBuilder.invoke(renderEnv, builder);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static Object BufferBuilder_getRenderEnv(BufferBuilder buffer, BlockState state, BlockPos pos) {
//			return buffer.getRenderEnv(state, pos);
			try {
				return getRenderEnv.invoke(buffer, state, pos);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void BufferBuilder_setBlockLayer(BufferBuilder buffer, RenderType renderType) {
//			buffer.setBlockLayer(renderType);
			try {
				setBlockLayer.invokeExact(buffer, renderType);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static boolean RenderEnv_isOverlaysRendered(Object renderEnv) {
//			return ((RenderEnv) renderEnv).isOverlaysRendered();
			try {
				return (boolean) isOverlaysRendered.invoke(renderEnv);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void ChunkRender_postRenderOverlays(RenderChunk chunkRender, ChunkBufferBuilderPack builder, Set<RenderType> usedLayers) {
//			chunkRender.postRenderOverlays(builder, compiledChunk);
			try {
				postRenderOverlays.invokeExact(chunkRender, builder, usedLayers);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void RenderEnv_setOverlaysRendered(Object renderEnv, boolean rendered) {
//			((RenderEnv) renderEnv).setOverlaysRendered(rendered);
			try {
				setOverlaysRendered.invoke(renderEnv, rendered);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static Object RenderEnv_getListQuadsOverlay(Object renderEnv, RenderType layer) {
//			return ((RenderEnv) renderEnv).getListQuadsOverlay(layer);
			try {
				return getListQuadsOverlay.invoke(renderEnv, layer);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static int ListQuadsOverlay_size(Object overlay) {
//			return overlay.size();
			try {
				return (int) size.invoke(overlay);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static List<BakedQuad> ListQuadsOverlay_getListQuadsSingle(Object overlay, BakedQuad quad) {
//			return overlay.getListQuadsSingle(quad);
			try {
				return (List<BakedQuad>) getListQuadsSingle.invoke(overlay, quad);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static BakedQuad ListQuadsOverlay_getQuad(Object overlay, int index) {
//			return overlay.getQuad(index);
			try {
				return (BakedQuad) getQuad.invoke(overlay, index);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static BlockState ListQuadsOverlay_getBlockState(Object overlay, int index) {
//			return overlay.getBlockState(index);
			try {
				return (BlockState) getBlockState.invoke(overlay, index);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		static void ListQuadsOverlay_clear(Object overlay) {
//			overlay.clear();
			try {
				clear.invoke(overlay);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

	}

}
