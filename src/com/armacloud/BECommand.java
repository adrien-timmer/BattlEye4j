package com.armacloud;

class BECommand {
    BEMessageType messageType;
    String command;

    public BECommand(BEMessageType messageType, String command) {
        this.messageType = messageType;
        this.command = command;
    }
}
