package com.biomatters.plugins.moorea.assembler.verify;

/**
 * @author Richard
 * @version $Id$
 */
public class Pair<A, B> {

    private final A itemA;
    private final B itemB;

    public Pair(A itemA, B itemB) {
        this.itemA = itemA;
        this.itemB = itemB;
    }

    public A getItemA() {
        return itemA;
    }

    public B getItemB() {
        return itemB;
    }
}