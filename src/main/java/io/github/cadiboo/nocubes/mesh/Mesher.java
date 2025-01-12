package io.github.cadiboo.nocubes.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cadiboo.nocubes.collision.ShapeConsumer;
import io.github.cadiboo.nocubes.util.Area;
import io.github.cadiboo.nocubes.util.Face;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

import static net.minecraft.core.BlockPos.MutableBlockPos;

public interface Mesher {

	default void generateGeometry(Area area, Predicate<BlockState> isSmoothable, FaceAction action) {
		try {
			generateGeometryInternal(area, isSmoothable, action);
		} catch (Throwable t) {
			Util.pauseInIde(t);
			throw t;
		}
	}

	default void generateCollisions(Area area, Predicate<BlockState> isSmoothable, ShapeConsumer action) {
		try {
			generateCollisionsInternal(area, isSmoothable, action);
		} catch (Throwable t) {
			Util.pauseInIde(t);
			throw t;
		}
	}

	void generateGeometryInternal(Area area, Predicate<BlockState> isSmoothable, FaceAction action);

	void generateCollisionsInternal(Area area, Predicate<BlockState> isSmoothable, ShapeConsumer action);

	Vec3i getPositiveAreaExtension();

	Vec3i getNegativeAreaExtension();

	interface FaceAction {

		/**
		 * @param relativePos The position of the face, positioned relatively to the start of the area
		 * @param face        The face, positioned relatively to the start of the area
		 * @return false if no more faces need to be generated
		 */
		boolean apply(MutableBlockPos relativePos, Face face);

	}

	/* protected */
	default boolean isOutsideMesh(int x, int y, int z, BlockPos size) {
		var negativeExtension = getNegativeAreaExtension();
		var positiveExtension = getPositiveAreaExtension();
		// Block is outside where we are generating it for, we only query it for its neighbouring faces
		return x >= size.getX() - positiveExtension.getX() || x < negativeExtension.getX() ||
			y >= size.getY() - positiveExtension.getY() || y < negativeExtension.getY() ||
			z >= size.getZ() - positiveExtension.getZ() || z < negativeExtension.getZ();
	}

	/**
	 * The vertices in meshes are generated relative to {@link Area#start}.
	 * {@link Area#start} is not necessarily the place where the final mesh should be rendered.
	 * The difference between the start of the area and the position we are generating for
	 * This exists because:
	 * To render a 16x16x16 area you need the data of a 18x18x18 area (+1 voxel on each axis)
	 * So the area is going to start at chunkPos - 1 (and extend 18 blocks)
	 * And the vertices are going to be relative to the start of the area
	 * We need to add an offset to the vertices because we want them to be relative to the start of the chunk, not the area
	 */
	static void translateToMeshStart(PoseStack matrix, BlockPos areaStart, BlockPos renderStartPos) {
		matrix.translate(
			getMeshOffset(areaStart.getX(), renderStartPos.getX()),
			getMeshOffset(areaStart.getY(), renderStartPos.getY()),
			getMeshOffset(areaStart.getZ(), renderStartPos.getZ())
		);
	}

	static int getMeshOffset(int areaStart, int desiredStart) {
		return validateMeshOffset(areaStart - desiredStart);
	}

	/* private */
	static int validateMeshOffset(int meshOffset) {
		assert meshOffset <= 0 : "Meshers won't require a smaller area than they are generating a mesh for";
		assert meshOffset > -3 : "Meshers won't require more than 2 extra blocks on each axis";
		return meshOffset;
	}


}
