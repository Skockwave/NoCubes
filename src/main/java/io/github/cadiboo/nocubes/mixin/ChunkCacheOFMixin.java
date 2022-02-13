package io.github.cadiboo.nocubes.mixin;

import io.github.cadiboo.nocubes.client.ClientUtil;
import io.github.cadiboo.nocubes.config.NoCubesConfig;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.optifine.override.ChunkCacheOF")
public abstract class ChunkCacheOFMixin {

	/**
	 * ChunkCacheOF is OptiFine's override of {@link RenderChunkRegion}
	 * See the documentation on {@link RenderChunkRegionMixin#getExtendedFluidState}
	 */
	@Inject(
		method = "getFluidState",
		at = @At("HEAD"),
		cancellable = true,
		require = -1, // Don't fail if OptiFine isn't present
		remap = false // OptiFine added method
	)
	public void getExtendedFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
		if (NoCubesConfig.Server.extendFluidsRange > 0)
			ci.setReturnValue(ClientUtil.getExtendedFluidState(pos));
	}
}
