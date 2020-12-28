package io.github.cadiboo.nocubes.future;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * @author Cadiboo
 */
public final class S2CConfigData implements IMessage, IMessageHandler<S2CConfigData, IMessage> {

	private /*final*/ String fileName;
	private /*final*/ byte[] fileData;

	@SuppressWarnings("unused")
	public S2CConfigData() {
	}

	public S2CConfigData(final String fileName, final byte[] fileData) {
		if (fileName.length() > 128) throw new IllegalStateException();
		this.fileName = fileName;
		this.fileData = fileData;
	}

	@Override
	public void fromBytes(final ByteBuf buf) {
		final PacketBuffer packetBuffer = new PacketBuffer(buf);
		this.fileName = packetBuffer.readString(128);
		this.fileData = packetBuffer.readByteArray();
	}

	@Override
	public void toBytes(final ByteBuf buf) {
		final PacketBuffer packetBuffer = new PacketBuffer(buf);
		packetBuffer.writeString(fileName);
		packetBuffer.writeByteArray(fileData);
	}

	@Override
	public IMessage onMessage(final S2CConfigData msg, final MessageContext context) {
		DistExecutor.runWhenOn(Side.CLIENT, () -> () -> Minecraft.getMinecraft().addScheduledTask(() -> {
			ConfigTracker.INSTANCE.receiveSyncedConfig(msg);
		}));
		return null;
	}

	public String getFileName() {
		return fileName;
	}

	public byte[] getBytes() {
		return fileData;
	}

}