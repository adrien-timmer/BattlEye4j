package com.armacloud;

enum BEMessageType {
    Login((byte) 0x00),

    Command((byte) 0x01),

    Server((byte) 0x02),

    Unknown((byte) 0xFF);

    private final byte type;

    BEMessageType(byte type) {
        this.type = type;
    }

    public static BEMessageType convertByteToPacketType(byte byteToConvert){
        BEMessageType packetType;
        switch (byteToConvert){
            case 0x00:
                packetType = Login;
                break;
            case 0x01:
                packetType = Command;
                break;
            case 0x02:
                packetType = Server;
                break;
            default:
                packetType = Unknown;
                break;
        }

        return packetType;
    }

    public byte getType() {
        return type;
    }
}
