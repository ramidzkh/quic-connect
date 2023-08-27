package me.ramidzkh.qc.server;

import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicConnectionEvent;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.ramidzkh.qc.QuicConnect;
import me.ramidzkh.qc.mixin.ConnectionAccessor;
import me.ramidzkh.qc.token.KeyedConnectionIdGenerator;
import me.ramidzkh.qc.token.KeyedTokenHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class QuicServerConnectionListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void startQuicServerListener(MinecraftServer server, List<ChannelFuture> channels,
            List<Connection> connections, @Nullable InetAddress address, int port) {
        var useNativeTransport = false && Epoll.isAvailable() && server.isEpollEnabled();

        var config = FabricLoader.getInstance().getConfigDir().resolve("quic-connect");
        var keyFile = config.resolve("key.pem");
        var certificateFile = config.resolve("certificate.pem");
        var caCertificateFile = config.resolve("ca_certificate.pem");

        var context = QuicSslContextBuilder.forServer(keyFile.toFile(), null, certificateFile.toFile())
                .applicationProtocols(QuicConnect.APPLICATION_NAME);

        if (Files.exists(caCertificateFile)) {
            context.trustManager(caCertificateFile.toFile());
        }

        var inheritAddresses = new WeakHashMap<Channel, SocketAddress>();

        var codec = new QuicServerCodecBuilder()
                .sslContext(context.build())
                .maxIdleTimeout(QuicConnect.IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .tokenHandler(new KeyedTokenHandler(Util.make(new byte[32], new SecureRandom()::nextBytes)))
                .connectionIdAddressGenerator(
                        new KeyedConnectionIdGenerator(Util.make(new byte[32], new SecureRandom()::nextBytes)))
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof QuicConnectionEvent event) {
                            var newAddress = event.newAddress();
                            inheritAddresses.put(ctx.channel(), newAddress);

                            for (var connection : connections) {
                                var currentAddress = connection.getRemoteAddress();
                                var accessor = (ConnectionAccessor) connection;

                                if (!newAddress.equals(currentAddress)
                                        && accessor.getChannel() instanceof QuicStreamChannel
                                        && accessor.getChannel().parent() == ctx.channel()) {
                                    accessor.setAddress(newAddress);
                                    LOGGER.info("{}[{}] was migrated to {}", connection, currentAddress, newAddress);
                                }
                            }
                        }

                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
                        inheritAddresses.remove(ctx.channel());
                        ctx.fireChannelInactive();
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NotNull Channel channel) {
                        var pipeline = channel.pipeline();
                        Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND);
                        var pps = server.getRateLimitPacketsPerSecond();
                        var connection = pps > 0 ? new RateKickingConnection(pps)
                                : new Connection(PacketFlow.SERVERBOUND);
                        ((ConnectionAccessor) connection).setEncrypted(true);
                        connections.add(connection);
                        pipeline.addLast("packet_handler", connection);
                        connection.setListener(new ServerHandshakePacketListenerImpl(server, connection));

                        var address = inheritAddresses.get(channel.parent());

                        if (address != null) {
                            ((ConnectionAccessor) connection).setAddress(address);
                        }
                    }
                })
                .build();

        channels.add(new Bootstrap()
                .group(useNativeTransport ? Connection.NETWORK_EPOLL_WORKER_GROUP.get()
                        : Connection.NETWORK_WORKER_GROUP.get())
                .channel(useNativeTransport ? EpollDatagramChannel.class : NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(address, port))
                .syncUninterruptibly());
    }
}
