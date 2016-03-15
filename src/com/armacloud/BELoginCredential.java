package com.armacloud;

import java.net.InetSocketAddress;

/**
 * Created by Adrien on 3/14/2016.
 */
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

    public void setHostAddress(InetSocketAddress hostAddress) {
        this.hostAddress = hostAddress;
    }

    public String getHostPassword() {
        return hostPassword;
    }

    public void setHostPassword(String hostPassword) {
        this.hostPassword = hostPassword;
    }
}
