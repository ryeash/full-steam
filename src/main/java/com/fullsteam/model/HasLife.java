package com.fullsteam.model;

public interface HasLife {

    double getHp();

    void setHp(double hp);

    double getMaxHp();

    boolean takeDamage(double damage);

    default void resetHp() {
        setHp(getMaxHp());
    }

}
