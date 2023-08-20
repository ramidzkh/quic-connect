package me.ramidzkh.qc.client;

import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class QuicSocketAddress extends InetSocketAddress {

    private final ServerAddress origin;

    public QuicSocketAddress(InetAddress addr, int port, ServerAddress origin) {
        super(addr, port);
        this.origin = origin;
    }

    public ServerAddress getOrigin() {
        return origin;
    }
}
