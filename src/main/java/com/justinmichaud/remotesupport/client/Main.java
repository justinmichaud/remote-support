package com.justinmichaud.remotesupport.client;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;

public class Main {

    public static void main(String... args) throws ExceptionUDT {
        System.out.println("Hello World!");

        final SocketUDT socket = new SocketUDT(TypeUDT.STREAM);
    }

}
