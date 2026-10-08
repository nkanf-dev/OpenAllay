package dev.openallay.integration.jei;

import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalLong;

/** Optional ingredient family ingress using the actual publication projection owners. */
public final class NativeJeiClosedValueIngressFixture {
    private static final class ForeignValue implements JeiIngredientValue { }
    public static void main(String[] args) {
        JeiIngredientValue first=new JeiIngredientValue.Fluid("water",OptionalLong.of(7));
        JeiIngredientValue second=new JeiIngredientValue.Unsupported("not_registered");
        JeiIngredientSlot slot=new JeiIngredientSlot(JeiIngredientSlot.Role.INPUT,Arrays.asList(first,second));
        if(slot.values().get(0)!=first || slot.values().get(1)!=second)throw new AssertionError("Native ingredient order/identity changed");
        try {slot.values().add(second);throw new AssertionError("Mutable copied ingredient list");}
        catch(UnsupportedOperationException expected){ }
        try {new JeiIngredientSlot(JeiIngredientSlot.Role.INPUT,Collections.singletonList(new ForeignValue()));
            throw new AssertionError("Foreign optional ingredient admitted");}
        catch(IncompatibleClassChangeError expected){
            if(!"Unknown native JeiIngredientValue subtype".equals(expected.getMessage()))throw new AssertionError("Foreign family message differs",expected);
        }
        try {new JeiIngredientSlot(JeiIngredientSlot.Role.INPUT,Collections.singletonList(null));
            throw new AssertionError("Null ingredient admitted");}
        catch(NullPointerException expected){ }
        System.out.println("PASS native ingredient exact family/list order/immutability/foreign/null ingress");
    }
}
