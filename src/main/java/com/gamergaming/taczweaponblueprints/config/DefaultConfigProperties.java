package com.gamergaming.taczweaponblueprints.config;

public class DefaultConfigProperties<T> {
    private T defaultValue;
    private T minValue;
    private T maxValue;

    public DefaultConfigProperties(T defaultValue, T minValue, T maxValue) {
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public T getDefault() {
        return defaultValue;
    }

    public T getMin() {
        return minValue;
    }

    public T getMax() {
        return maxValue;
    }

    public void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setMinValue(T minValue) {
        this.minValue = minValue;
    }

    public void setMaxValue(T maxValue) {
        this.maxValue = maxValue;
    }
}
