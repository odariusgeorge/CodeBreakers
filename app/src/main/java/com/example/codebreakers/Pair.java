package com.example.codebreakers;

public class Pair<A, B> {
    public  A a;
    public  B b;
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

    boolean equals (Pair<A,B> x) {
        if (x.a == this.a && x.b == this.b) return true;
        return false;
    }
}