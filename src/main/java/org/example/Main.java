package org.example;

import java.util.concurrent.ConcurrentHashMap;

public class Main {
    public static void main(String[] args) {

        ConcurrentHashMap<String, String> c = new ConcurrentHashMap<>(100, 0.75f, 10);


        System.out.println("Hello world!");
    }
}