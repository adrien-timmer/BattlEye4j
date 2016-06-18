package com.armacloud;

/**
 * Created by adrien on 2016-06-17.
 */
public interface ConnectionHandler {
    void onConnected(BEConnectType connectType);

    void onDisconnected(BEDisconnectType disconnectType);
}
