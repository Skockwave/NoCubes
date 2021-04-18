package io.github.cadiboo.nocubes.network;

import io.github.cadiboo.nocubes.config.Config;
import io.github.cadiboo.nocubes.util.DistExecutor;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import static io.github.cadiboo.nocubes.NoCubes.MOD_ID;

/**
 * @author Cadiboo
 */
public final class S2CDisableTerrainCollisions implements IMessage, IMessageHandler<S2CDisableTerrainCollisions, IMessage> {

	@Override
	public IMessage onMessage(final S2CDisableTerrainCollisions msg, final MessageContext context) {
		Minecraft.getMinecraft().addScheduledTask(() -> DistExecutor.runWhenOn(Side.CLIENT, () -> () -> {
			Config.terrainCollisions = false;
			Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation(MOD_ID + ".terrainCollisionsDisabled"));
		}));
		return null;
	}

	@Override
	public void fromBytes(final ByteBuf buf) {
	}

	@Override
	public void toBytes(final ByteBuf buf) {
	}

}