package com.armacloud;

public enum BECommandType {
    Init {
        public String toString() {
            return "#init";
        }
    },

    Shutdown {
        public String toString() {
            return "#shutdown";
        }
    },

    Reassign {
        public String toString() {
            return "#reassign";
        }
    },

    Restart {
        public String toString() {
            return "#restart";
        }
    },

    Lock {
        public String toString() {
            return "#lock";
        }
    },

    Unlock {
        public String toString() {
            return "#unlock";
        }
    },

    Mission {
        public String toString() {
            return "#mission ";
        }
    },

    Missions {
        public String toString() {
            return "missions";
        }
    },

    RConPassword {
        public String toString() {
            return "rconpassword ";
        }
    },

    MaxPing {
        public String toString() {
            return "maxping ";
        }
    },

    Kick {
        public String toString() {
            return "kick ";
        }
    },

    Players {
        public String toString() {
            return "players";
        }
    },

    Say {
        public String toString() {
            return "say ";
        }
    },

    LoadBans {
        public String toString() {
            return "loadbans";
        }
    },

    LoadScripts {
        public String toString() {
            return "loadScripts";
        }
    },

    LoadEvents {
        public String toString() {
            return "loadevents";
        }
    },

    Bans {
        public String toString() {
            return "bans";
        }
    },

    Ban {
        public String toString() {
            return "ban ";
        }
    },

    AddBan {
        public String toString() {
            return "addban ";
        }
    },

    RemoveBan {
        public String toString() {
            return "removeban ";
        }
    },

    WriteBans {
        public String toString() {
            return "writebans";
        }
    },

    Admins {
        public String toString() {
            return "admins";
        }
    },

    Empty {
        public String toString() {
            return "";
        }
    }
}
