package com.armacloud;

/**
 * Created by Adrien on 3/15/2016.
 */
public enum BEConnectType {
    Failure((byte) 0x00),
    Success((byte) 0x01),
    Unknown((byte) 0xFF);

    private final byte type;

    BEConnectType(byte type) {
        this.type = type;
    }

    public byte getType() {
        return type;
    }

    public static BEConnectType convertByteToConnectType(byte byteToConvert){
        BEConnectType packetType;
        switch (byteToConvert){
            case 0x00:
                packetType = Failure;
                break;
            case 0x01:
                packetType = Success;
                break;
            default:
                packetType = Unknown;
                break;
        }

        return packetType;
    }
}
