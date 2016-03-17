package com.armacloud;

/**
 * Created by adrien on 2016-03-15.
 */
public class BECommand {
    public BEMessageType messageType;
    public String command;

    public BECommand(BEMessageType messageType, String command) {
        this.messageType = messageType;
        this.command = command;
    }

    public BEMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(BEMessageType messageType) {
        this.messageType = messageType;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
