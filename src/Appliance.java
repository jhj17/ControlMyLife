package com.jeffjosephs.controlMyLife;

public interface Appliance {
    boolean connectTo();
    boolean sendMessage(String message);
    void updateStatuses();
}
