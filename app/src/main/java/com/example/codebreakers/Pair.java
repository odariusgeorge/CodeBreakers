package com.example.codebreakers;

public class Pair<A, B> {
    public final A a;
    public final B b;
    public Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    A getA(){
        return a;
    }

    B getB(){
        return b;
    }
}