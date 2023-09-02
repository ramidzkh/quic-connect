package me.ramidzkh.qc.client;

public interface ServerAddressProperties {

    boolean getUseQuic();

    void setUseQuic(boolean quic);

    default void copy(ServerAddressProperties other) {
        setUseQuic(other.getUseQuic());
    }
}
