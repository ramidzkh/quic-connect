package me.ramidzkh.qc.mixin.server;

import com.mojang.authlib.GameProfile;
import io.netty.incubator.codec.quic.QuicChannel;
import me.ramidzkh.qc.server.GameProfileCodec;
import me.ramidzkh.qc.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin {

    @Shadow
    @Final
    static Logger LOGGER;

    @Shadow
    @Final
    Connection connection;

    @Shadow
    @Nullable
    GameProfile gameProfile;

    @Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean usesAuthentication(MinecraftServer server, ServerboundHelloPacket packet) {
        if (((ConnectionAccessor) connection).getChannel().parent() instanceof QuicChannel channel) {
            var session = channel.sslEngine().getSession();

            try {
                if (session.getPeerCertificates()[0] instanceof X509Certificate x509) {
                    var extension = x509.getExtensionValue(GameProfileCodec.OID);
                    var profile = GameProfileCodec.read(ByteBuffer.wrap(extension));

                    if (profile.getId().equals(packet.profileId().orElse(null))
                            && profile.getName().equals(packet.name())) {
                        this.gameProfile = profile;
                        return false;
                    }
                }
            } catch (Exception e) {
                LOGGER.trace("{} did not have a valid Minecraft profile association", channel);
            }
        }

        return server.usesAuthentication();
    }
}
