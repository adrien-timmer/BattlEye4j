package com.armacloud;

enum BEConnectType {
    Failure(),
    Success(),
    Unknown();

    BEConnectType() {
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
