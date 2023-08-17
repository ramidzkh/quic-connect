package me.ramidzkh.qc.mixin.server;

import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerConnectionListener.class)
public interface ServerConnectionListenerAccessor {

    @Accessor
    List<ChannelFuture> getChannels();

    @Accessor
    List<Connection> getConnections();
}
