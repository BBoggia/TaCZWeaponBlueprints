package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.util.ValidationResult;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.screen.widget.ValidationBackedNumberFieldWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.peanuuutz.tomlkt.TomlElement;

/** Edits percentages/seconds while preserving the existing numeric TOML and network values. */
public final class DisplayUnitNumber<N extends Number> extends ValidatedField<N> {
    private final ValidatedField<N> storage;
    private final DoubleSupplier unitsPerDisplayed;
    private final DoubleFunction<N> convert;
    private final boolean integral;
    private final double minimum, maximum;

    private DisplayUnitNumber(ValidatedField<N> storage, DoubleSupplier scale, DoubleFunction<N> convert,
            boolean integral, double minimum, double maximum) {
        super(storage.get(),storage.get());
        this.storage=storage; this.unitsPerDisplayed=scale; this.convert=convert;
        this.integral=integral; this.minimum=minimum; this.maximum=maximum;
    }
    public static DisplayUnitNumber<Double> percent(double value) {
        return new DisplayUnitNumber<>(new ValidatedDouble(value,1,0),()->.01,Double::valueOf,false,0,1);
    }
    public static DisplayUnitNumber<Integer> integer(int value,int max,int min,DoubleSupplier scale) {
        return new DisplayUnitNumber<>(new ValidatedInt(value,max,min),scale,d->(int)Math.round(d),true,min,max);
    }
    public double displayValue() { return get().doubleValue()/unitsPerDisplayed.getAsDouble(); }
    public ValidationResult<N> fromDisplay(double value) {
        double scale=unitsPerDisplayed.getAsDouble(), raw=value*scale;
        if (!Double.isFinite(raw) || scale<=0 || raw<minimum || raw>maximum)
            return ValidationResult.Companion.error(get(),"Enter a number from "+minimum/scale+" to "+maximum/scale);
        if (integral && Math.abs(raw-Math.rint(raw))>1e-7)
            return ValidationResult.Companion.error(get(),"Use increments of "+1/scale);
        return storage.validateEntry(convert.apply(raw),ValidationType.STRONG);
    }
    @Override public ValidationResult<N> deserialize(TomlElement toml,String name) { return storage.deserialize(toml,name); }
    @Override public ValidationResult<TomlElement> serialize(N value) { return storage.serialize(value); }
    @Override public ValidationResult<N> correctEntry(N value,ValidationType type) { return storage.correctEntry(value,type); }
    @Override public ValidationResult<N> validateEntry(N value,ValidationType type) { return storage.validateEntry(value,type); }
    @Override public boolean isValidEntry(Object value) { return storage.isValidEntry(value); }
    @Override public N copyValue(N value) { return value; }
    @Override public DisplayUnitNumber<N> instanceEntry() {
        return new DisplayUnitNumber<>(storage.instanceEntry(),unitsPerDisplayed,convert,integral,minimum,maximum);
    }
    @Override @OnlyIn(Dist.CLIENT)
    public AbstractWidget widgetEntry(ChoiceValidator<N> choices) {
        var widget = new ValidationBackedNumberFieldWidget<Double>(110,20,this::displayValue,ChoiceValidator.Companion.any(),value->{
            var checked=fromDisplay(value);
            if (checked.isValid()) checked=choices.validateEntry(checked.get(),ValidationType.STRONG);
            return checked.isValid()?ValidationResult.Companion.success(value):ValidationResult.Companion.error(value,checked.getError());
        },value->{ var checked=fromDisplay(value); if(checked.isValid()) accept(checked.get()); },true,0);
        // Keep the leading digits visible for high-precision values from existing files.
        widget.setCursorPosition(0);
        widget.setHighlightPos(0);
        return widget;
    }
}
