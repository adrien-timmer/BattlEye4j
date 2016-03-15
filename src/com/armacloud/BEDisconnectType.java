package com.armacloud;

/**
 * Created by Adrien on 3/14/2016.
 */
public enum BEDisconnectType {
    Manual {
        public String toString() {
            return "Manual disconnect";
        }
    },

    ConnectionLost {
        public String toString() {
            return "Connection lost (possible timeout)";
        }
    },

    SocketException {
        public String toString() {
            return "Connection lost due to exception";
        }
    }
}
