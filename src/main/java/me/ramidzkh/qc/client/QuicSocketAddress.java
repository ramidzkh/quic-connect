package me.ramidzkh.qc.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class QuicSocketAddress extends InetSocketAddress {

    private final QuicTier quicTier;

    public QuicSocketAddress(InetAddress addr, int port, QuicTier tier) {
        super(addr, port);
        quicTier = tier;
    }

    public QuicTier getQuicTier() {
        return quicTier;
    }
}
