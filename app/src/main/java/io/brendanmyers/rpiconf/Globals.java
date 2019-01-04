package io.brendanmyers.rpiconf;

public final class Globals {
    public static String messages = "";

    public static String getMessages() {
        return Globals.messages;
    }

    public static void setMessages(String messages) {
        Globals.messages = messages;
    }
}
