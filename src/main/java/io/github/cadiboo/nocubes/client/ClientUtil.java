package io.github.cadiboo.nocubes.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;

public class ClientUtil {

	public static void reloadAllChunks(Minecraft minecraft) {
		WorldRenderer worldRenderer = minecraft.levelRenderer;
		if (worldRenderer != null)
			worldRenderer.allChanged();
	}

	public static IVertexBuilder vertex(IVertexBuilder buffer, Matrix4f matrix, float x, float y, float z) {
		// Calling 'buffer.vertex(matrix, x, y, z)' allocates a Vector4f
		// To avoid allocating so many short lived vectors we do the transform ourselves instead
		float transformedX = matrix.getTransformX(x, y, z, 1);
		float transformedY = matrix.getTransformY(x, y, z, 1);
		float transformedZ = matrix.getTransformZ(x, y, z, 1);
		return buffer.vertex(transformedX, transformedY, transformedZ);
	}

	public static void vertex(IVertexBuilder buffer, MatrixStack matrix, float x, float y, float z, float red, float green, float blue, float alpha, float texU, float texV, int overlayUV, int lightmapUV, float normalX, float normalY, float normalZ) {
		// Calling 'buffer.vertex(matrix, x, y, z)' allocates a Vector4f
		// To avoid allocating so many short lived vectors we do the transform ourselves instead
		MatrixStack.Entry currentTransform = matrix.last();
		Matrix4f pose = currentTransform.pose();
		Matrix3f normal = currentTransform.normal();

		float transformedX = pose.getTransformX(x, y, z, 1);
		float transformedY = pose.getTransformY(x, y, z, 1);
		float transformedZ = pose.getTransformZ(x, y, z, 1);

		float transformedNormalX = normal.getTransformX(normalX, normalY, normalZ);
		float transformedNormalY = normal.getTransformY(normalX, normalY, normalZ);
		float transformedNormalZ = normal.getTransformZ(normalX, normalY, normalZ);

		buffer.vertex(transformedX, transformedY, transformedZ, red, green, blue, alpha, texU, texV, overlayUV, lightmapUV, transformedNormalX, transformedNormalY, transformedNormalZ);
	}
}
