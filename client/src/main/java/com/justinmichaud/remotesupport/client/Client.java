package com.justinmichaud.remotesupport.client;

import com.justinmichaud.remotesupport.client.ui.ConnectForm;

public class Client {

    public static void main(String... args) {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");

        ConnectForm.main(args);
    }

}
