package com.oracle.truffle.js.runtime.builtins;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.builtins.TemporalDurationPrototypeBuiltins;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsLongNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSContext.BuiltinFunctionKey;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;

public class JSTemporalDuration extends JSNonProxy implements JSConstructorFactory.Default.WithSpecies,
        PrototypeSupplier {

    public static final JSTemporalDuration INSTANCE = new JSTemporalDuration();

    public static final String CLASS_NAME = "TemporalDuration";
    public static final String PROTOTYPE_NAME = "TemporalDuration.prototype";

    public static final String YEARS = "years";
    public static final String MONTHS = "months";
    public static final String WEEKS = "weeks";
    public static final String DAYS = "days";
    public static final String HOURS = "hours";
    public static final String MINUTES = "minutes";
    public static final String SECONDS = "seconds";
    public static final String MILLISECONDS = "milliseconds";
    public static final String MICROSECONDS = "microseconds";
    public static final String NANOSECONDS = "nanoseconds";
    public static final String SIGN = "sign";
    public static final String BLANK = "blank";

    public static final String[] PROPERTIES = new String[] {
            DAYS, HOURS, MICROSECONDS, MILLISECONDS, MINUTES, MONTHS, NANOSECONDS, SECONDS, WEEKS, YEARS
    };

    private JSTemporalDuration() {
    }

    public static DynamicObject create(JSContext context, long years, long months, long weeks, long days, long hours,
                                       long minutes, long seconds, long milliseconds, long microseconds, long nanoseconds) {
        if(!validateTemporalDuration(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds,
                nanoseconds)) {
            throw Errors.createRangeError("Given duration outside range.");
        }
        JSRealm realm = context.getRealm();
        JSObjectFactory factory = context.getTemporalDurationFactory();
        DynamicObject obj = factory.initProto(new JSTemporalDurationObject(factory.getShape(realm),
                years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds), realm);
        return context.trackAllocation(obj);
    }

    @Override
    public String getClassName(DynamicObject object) {
        return "Temporal.Duration";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    private static DynamicObject createGetterFunction(JSRealm realm, BuiltinFunctionKey functionKey, String property) {
        JSFunctionData getterData = realm.getContext().getOrCreateBuiltinFunctionData(functionKey, (c) -> {
            CallTarget callTarget = Truffle.getRuntime().createCallTarget(new JavaScriptRootNode(c.getLanguage(), null, null) {
                private final BranchProfile errorBranch = BranchProfile.create();

                @Override
                public Object execute(VirtualFrame frame) {
                    Object obj = frame.getArguments()[0];
                    if(JSTemporalDuration.isJSTemporalDuration(obj)) {
                        JSTemporalDurationObject temporalDuration = (JSTemporalDurationObject) obj;
                        switch (property) {
                            case YEARS:
                                return temporalDuration.getYears();
                            case MONTHS:
                                return temporalDuration.getMonths();
                            case WEEKS:
                                return temporalDuration.getWeeks();
                            case DAYS:
                                return temporalDuration.getDays();
                            case HOURS:
                                return temporalDuration.getHours();
                            case MINUTES:
                                return temporalDuration.getMinutes();
                            case SECONDS:
                                return temporalDuration.getSeconds();
                            case MILLISECONDS:
                                return temporalDuration.getMilliseconds();
                            case MICROSECONDS:
                                return temporalDuration.getMicroseconds();
                            case NANOSECONDS:
                                return temporalDuration.getNanoseconds();
                            default:
                                errorBranch.enter();
                                throw Errors.createTypeErrorTemporalDurationExpected();
                        }
                    } else {
                        errorBranch.enter();
                        throw Errors.createTypeErrorTemporalDurationExpected();
                    }
                }
            });
            return JSFunctionData.createCallOnly(c, callTarget, 0, "get " + property);
        });
        DynamicObject getter = JSFunction.create(realm, getterData);
        return getter;
    }

    private static DynamicObject createGetSignFunction(JSRealm realm) {
        JSFunctionData getterData = realm.getContext().getOrCreateBuiltinFunctionData(
                BuiltinFunctionKey.TemporalDurationSign, (c) -> {
                    CallTarget callTarget = Truffle.getRuntime().createCallTarget(new JavaScriptRootNode(c.getLanguage(), null, null) {
                        private final BranchProfile errorBranch = BranchProfile.create();

                        @Override
                        public Object execute(VirtualFrame frame) {
                            Object obj = frame.getArguments()[0];
                            if (JSTemporalDuration.isJSTemporalDuration(obj)) {
                                JSTemporalDurationObject temporalDuration = (JSTemporalDurationObject) obj;
                                return durationSign(temporalDuration.getYears(), temporalDuration.getMonths(),
                                        temporalDuration.getWeeks(), temporalDuration.getDays(),
                                        temporalDuration.getHours(), temporalDuration.getMinutes(),
                                        temporalDuration.getSeconds(), temporalDuration.getMilliseconds(),
                                        temporalDuration.getMicroseconds(), temporalDuration.getNanoseconds());
                            } else {
                                errorBranch.enter();
                                throw Errors.createTypeErrorTemporalDurationExpected();
                            }
                        }
                    });
                    return JSFunctionData.createCallOnly(c, callTarget, 0, "get sign");
                });
        DynamicObject getter = JSFunction.create(realm, getterData);
        return getter;
    }

    private static DynamicObject createGetBlankFunction(JSRealm realm) {
        JSFunctionData getterData = realm.getContext().getOrCreateBuiltinFunctionData(
                BuiltinFunctionKey.TemporalDurationBlank, (c) -> {
                    CallTarget callTarget = Truffle.getRuntime().createCallTarget(new JavaScriptRootNode(c.getLanguage(), null, null) {
                        private final BranchProfile errorBranch = BranchProfile.create();

                        @Override
                        public Object execute(VirtualFrame frame) {
                            Object obj = frame.getArguments()[0];
                            if (JSTemporalDuration.isJSTemporalDuration(obj)) {
                                JSTemporalDurationObject temporalDuration = (JSTemporalDurationObject) obj;
                                int sign = durationSign(temporalDuration.getYears(), temporalDuration.getMonths(),
                                        temporalDuration.getWeeks(), temporalDuration.getDays(),
                                        temporalDuration.getHours(), temporalDuration.getMinutes(),
                                        temporalDuration.getSeconds(), temporalDuration.getMilliseconds(),
                                        temporalDuration.getMicroseconds(), temporalDuration.getNanoseconds());
                                return sign == 0;
                            } else {
                                errorBranch.enter();
                                throw Errors.createTypeErrorTemporalDurationExpected();
                            }
                        }
                    });
                    return JSFunctionData.createCallOnly(c, callTarget, 0, "get blank");
                });
        DynamicObject getter = JSFunction.create(realm, getterData);
        return getter;
    }

    @Override
    public DynamicObject createPrototype(JSRealm realm, DynamicObject constructor) {
        JSContext ctx = realm.getContext();
        DynamicObject prototype = JSObjectUtil.createOrdinaryPrototypeObject(realm);
        JSObjectUtil.putConstructorProperty(ctx, prototype, constructor);

        JSObjectUtil.putBuiltinAccessorProperty(prototype, YEARS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationYears, YEARS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, MONTHS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationMonths, MONTHS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, WEEKS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationWeeks, WEEKS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, DAYS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationDays, DAYS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, HOURS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationHours, HOURS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, MINUTES,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationMinutes, MINUTES), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, SECONDS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationSeconds, SECONDS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, MILLISECONDS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationMilliseconds, MILLISECONDS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, MICROSECONDS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationMicroseconds, MICROSECONDS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, NANOSECONDS,
                createGetterFunction(realm, BuiltinFunctionKey.TemporalDurationNanoseconds, NANOSECONDS), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, SIGN,
                createGetSignFunction(realm), Undefined.instance);
        JSObjectUtil.putBuiltinAccessorProperty(prototype, BLANK,
                createGetBlankFunction(realm), Undefined.instance);
        JSObjectUtil.putFunctionsFromContainer(realm, prototype, TemporalDurationPrototypeBuiltins.BUILTINS);
        JSObjectUtil.putToStringTag(prototype, "Temporal.Duration");

        return prototype;
    }

    @Override
    public Shape makeInitialShape(JSContext context, DynamicObject prototype) {
        Shape initialShape = JSObjectUtil.getProtoChildShape(prototype, JSTemporalDuration.INSTANCE, context);
        return initialShape;
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getTemporalDurationPrototype();
    }

    public static JSConstructor createConstructor(JSRealm realm) {
        return INSTANCE.createConstructorAndPrototype(realm);
    }

    public static boolean isJSTemporalDuration(Object obj) {
        return obj instanceof JSTemporalDurationObject;
    }

    //region Abstract methods
    // 7.5.3
    public static int durationSign(long years, long months, long weeks, long days, long hours, long minutes,
                                   long seconds, long milliseconds, long microseconds, long nanoseconds) {
        if (years < 0) {
            return -1;
        }
        if (years > 0) {
            return 1;
        }
        if (months < 0) {
            return -1;
        }
        if (months > 1) {
            return 1;
        }
        if (weeks < 0) {
            return -1;
        }
        if (weeks > 0) {
            return 1;
        }
        if (days < 0) {
            return -1;
        }
        if (days > 0) {
            return 1;
        }
        if (hours < 0) {
            return -1;
        }
        if (hours > 0) {
            return 1;
        }
        if (minutes < 0) {
            return -1;
        }
        if (minutes > 0) {
            return 1;
        }
        if (seconds < 0) {
            return -1;
        }
        if (seconds > 0) {
            return 1;
        }
        if (milliseconds < 0) {
            return -1;
        }
        if (milliseconds > 0) {
            return 1;
        }
        if (microseconds < 0) {
            return -1;
        }
        if (microseconds > 0) {
            return 1;
        }
        if (nanoseconds < 0) {
            return -1;
        }
        if (nanoseconds > 0) {
            return 1;
        }
        return 0;
    }

    // 7.5.5
    public static boolean validateTemporalDuration(long years, long months, long weeks, long days, long hours,
                                                   long minutes, long seconds, long milliseconds, long microseconds,
                                                   long nanoseconds) {
        int sign = durationSign(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds,
                nanoseconds);
        if (years < 0 && sign > 0) {
            return false;
        }
        if (years > 0 && sign < 0) {
            return false;
        }
        if (months < 0 && sign > 0) {
            return false;
        }
        if (months > 0 && sign < 0) {
            return false;
        }
        if (weeks < 0 && sign > 0) {
            return false;
        }
        if (weeks > 0 && sign < 0) {
            return false;
        }
        if (days < 0 && sign > 0) {
            return false;
        }
        if (days > 0 && sign < 0) {
            return false;
        }
        if (hours < 0 && sign > 0) {
            return false;
        }
        if (hours > 0 && sign < 0) {
            return false;
        }
        if (minutes < 0 && sign > 0) {
            return false;
        }
        if (minutes > 0 && sign < 0) {
            return false;
        }
        if (seconds < 0 && sign > 0) {
            return false;
        }
        if (seconds > 0 && sign < 0) {
            return false;
        }
        if (milliseconds < 0 && sign > 0) {
            return false;
        }
        if (milliseconds > 0 && sign < 0) {
            return false;
        }
        if (microseconds < 0 && sign > 0) {
            return false;
        }
        if (microseconds > 0 && sign < 0) {
            return false;
        }
        if (nanoseconds < 0 && sign > 0) {
            return false;
        }
        if (nanoseconds > 0 && sign < 0) {
            return false;
        }
        return true;
    }

    // 7.5.7
    public static DynamicObject toPartialDuration(DynamicObject temporalDurationLike, JSRealm realm,
                                            IsObjectNode isObjectNode, DynamicObjectLibrary dol,
                                            JSToIntegerAsLongNode toInt) {
        if(!isObjectNode.executeBoolean(temporalDurationLike)) {
            throw Errors.createTypeError("Given duration like is not a object.");
        }
        DynamicObject result = JSObjectUtil.createOrdinaryPrototypeObject(realm);
        boolean any = false;
        for(String property : PROPERTIES) {
            Object value = dol.getOrDefault(temporalDurationLike, property, null);
            if (value != null) {
                any = true;
                JSObjectUtil.putDataProperty(realm.getContext(), result, property, toInt.executeLong(value));
            }
        }
        if(!any) {
            throw Errors.createTypeError("Given duration like object has no duration properties.");
        }
        return result;
    }

    // 7.5.9
    public static JSTemporalDurationObject createTemporalDurationFromInstance(JSTemporalDurationObject duration,
                                                                   long years, long months, long weeks, long days,
                                                                   long hours, long minutes, long seconds,
                                                                   long milliseconds, long microseconds,
                                                                   long nanoseconds, JSRealm realm,
                                                                   JSFunctionCallNode callNode) {
        assert validateTemporalDuration(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds);
        DynamicObject constructor = realm.getTemporalDurationConstructor();
        Object[] ctorArgs = new Object[]{years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds};
        Object[] args = JSArguments.createInitial(JSFunction.CONSTRUCT, constructor, ctorArgs.length);
        System.arraycopy(ctorArgs, 0, args, JSArguments.RUNTIME_ARGUMENT_COUNT, ctorArgs.length);
        Object result = callNode.executeCall(args);
        return (JSTemporalDurationObject) result;
    }

    // 7.5.23
    public static String temporalDurationToString(JSTemporalDurationObject duration) {
        long years = duration.getYears();
        long months = duration.getMonths();
        long weeks = duration.getWeeks();
        long days = duration.getDays();
        long hours = duration.getHours();
        long minutes = duration.getMinutes();
        long seconds = duration.getSeconds();
        long milliseconds = duration.getMilliseconds();
        long microseconds = duration.getMicroseconds();
        long nanoseconds = duration.getNanoseconds();
        int sign = durationSign(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds);
        microseconds += nanoseconds / 1000;
        nanoseconds = nanoseconds % 1000;
        milliseconds += microseconds / 1000;
        microseconds = microseconds % 1000;
        seconds += milliseconds / 1000;
        milliseconds = milliseconds % 1000;
        if (years == 0 && months == 0 && weeks == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0
                && milliseconds == 0 && microseconds == 0 && nanoseconds == 0) {
            return "PT0S";
        }
        StringBuilder datePart = new StringBuilder();
        if (years != 0) {
            datePart.append(Math.abs(years));
            datePart.append("Y");
        }
        if (months != 0) {
            datePart.append(Math.abs(months));
            datePart.append("M");
        }
        if (weeks != 0) {
            datePart.append(Math.abs(weeks));
            datePart.append("W");
        }
        if (days != 0) {
            datePart.append(Math.abs(days));
            datePart.append("D");
        }
        StringBuilder timePart = new StringBuilder();
        if (hours != 0) {
            timePart.append(Math.abs(hours));
            timePart.append("H");
        }
        if (minutes != 0) {
            timePart.append(Math.abs(minutes));
            timePart.append("M");
        }
        if (seconds != 0 || milliseconds != 0 || microseconds != 0 || nanoseconds != 0) {
            String nanosecondPart = "", microsecondPart = "", millisecondPart = "";
            if (nanoseconds != 0) {
                nanosecondPart = String.format("%1$3d", Math.abs(nanoseconds)).replace(" ", "0");
                microsecondPart = "000";
                millisecondPart = "000";
            }
            if (microseconds != 0) {
                microsecondPart = String.format("%1$3d", Math.abs(microseconds)).replace(" ", "0");
                millisecondPart = "000";
            }
            if (milliseconds != 0) {
                millisecondPart = String.format("%1$3d", Math.abs(milliseconds)).replace(" ", "0");
            }
            String decimalPart = millisecondPart + microsecondPart + nanosecondPart;
            String secondsPart = String.format("%d", Math.abs(seconds));
            if(!decimalPart.equals("")) {
                secondsPart += "." + decimalPart;
            }
            timePart.append(secondsPart);
            timePart.append("S");
        }
        String signPart = sign < 0 ? "-" : "";
        StringBuilder result = new StringBuilder();
        result.append(signPart).append("P").append(datePart);
        if(!timePart.toString().equals("")) {
            result.append("T").append(timePart);
        }
        return result.toString();
    }
    //endregion
}
