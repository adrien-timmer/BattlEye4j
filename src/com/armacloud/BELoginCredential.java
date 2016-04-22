package com.armacloud;

import java.net.InetSocketAddress;

public class BELoginCredential {

    public InetSocketAddress hostAddress;
    public String hostPassword;

    public BELoginCredential(InetSocketAddress hostSocketAddress, String hostPassword) {
        this.hostAddress = hostSocketAddress;
        this.hostPassword = hostPassword;
    }

    public InetSocketAddress getHostAddress() {
        return hostAddress;
    }

    public String getHostPassword() {
        return hostPassword;
    }
}
