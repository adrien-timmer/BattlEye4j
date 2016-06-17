package com.armacloud;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) {
        InetSocketAddress hostAddress = new InetSocketAddress("hostname", 2302);
        BELoginCredential loginCredential = new BELoginCredential(hostAddress, "password");
        BEClient beClient = new BEClient(loginCredential);
        try {
            beClient.connect();
            Thread.sleep(5000);
            beClient.sendCommand(BECommandType.Players);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
