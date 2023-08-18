package me.ramidzkh.qc.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.net.SocketAddress;

@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Accessor
    Channel getChannel();

    @Accessor
    void setAddress(SocketAddress address);

    @Accessor
    void setEncrypted(boolean encrypted);
}
