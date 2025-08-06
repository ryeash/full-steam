package com.fullsteam.model;

public interface HasId {

    default long getId() {
        return id();
    }

    long id();
}
