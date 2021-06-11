/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.runtime.builtins.temporal;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.CALENDAR;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.DAY;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.GREGORY;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.ID;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.ISO8601;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.JAPANESE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTH;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTH_CODE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.YEAR;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarFunctionBuiltins;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltins;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.binary.JSIdenticalNode;
import com.oracle.truffle.js.nodes.cast.JSStringToNumberNode;
import com.oracle.truffle.js.nodes.cast.JSToBooleanNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.JSConstructor;
import com.oracle.truffle.js.runtime.builtins.JSConstructorFactory;
import com.oracle.truffle.js.runtime.builtins.JSNonProxy;
import com.oracle.truffle.js.runtime.builtins.JSObjectFactory;
import com.oracle.truffle.js.runtime.builtins.PrototypeSupplier;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

public final class JSTemporalCalendar extends JSNonProxy implements JSConstructorFactory.Default.WithFunctionsAndSpecies,
                PrototypeSupplier {

    public static final JSTemporalCalendar INSTANCE = new JSTemporalCalendar();

    public static final String CLASS_NAME = "Calendar";
    public static final String PROTOTYPE_NAME = "Calendar.prototype";

    private JSTemporalCalendar() {

    }

    public static DynamicObject create(JSContext context, String id) {
        if (!isBuiltinCalendar(id)) {
            throw TemporalErrors.createRangeErrorCalendarNotSupported();
        }
        JSRealm realm = context.getRealm();
        JSObjectFactory factory = context.getTemporalCalendarFactory();
        DynamicObject obj = factory.initProto(new JSTemporalCalendarObject(factory.getShape(realm), id), realm);
        return context.trackAllocation(obj);
    }

    @Override
    public String getClassName(DynamicObject object) {
        return "Temporal.Calendar";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public DynamicObject createPrototype(JSRealm realm, DynamicObject constructor) {
        JSContext ctx = realm.getContext();
        DynamicObject prototype = JSObjectUtil.createOrdinaryPrototypeObject(realm);
        JSObjectUtil.putConstructorProperty(ctx, prototype, constructor);

        JSObjectUtil.putBuiltinAccessorProperty(prototype, ID, realm.lookupAccessor(TemporalCalendarPrototypeBuiltins.BUILTINS, ID));
        JSObjectUtil.putFunctionsFromContainer(realm, prototype, TemporalCalendarPrototypeBuiltins.BUILTINS);
        JSObjectUtil.putToStringTag(prototype, "Temporal.Calendar");

        return prototype;
    }

    @Override
    public Shape makeInitialShape(JSContext context, DynamicObject prototype) {
        Shape initialShape = JSObjectUtil.getProtoChildShape(prototype, JSTemporalCalendar.INSTANCE, context);
        return initialShape;
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getTemporalCalendarPrototype();
    }

    public static JSConstructor createConstructor(JSRealm realm) {
        return INSTANCE.createConstructorAndPrototype(realm, TemporalCalendarFunctionBuiltins.BUILTINS);
    }

    public static boolean isJSTemporalCalendar(Object obj) {
        return obj instanceof JSTemporalCalendarObject;
    }

    // 12.1.2
    public static boolean isBuiltinCalendar(String id) {
        return id.equals(ISO8601) || id.equals(GREGORY) || id.equals(JAPANESE);
    }

    // 12.1.3
    public static JSTemporalCalendarObject getBuiltinCalendar(String id, JSContext ctx) {
        if (!isBuiltinCalendar(id)) {
            throw TemporalErrors.createRangeErrorCalendarNotSupported();
        }
        return (JSTemporalCalendarObject) create(ctx, id);
    }

    // 12.1.4
    public static JSTemporalCalendarObject getISO8601Calendar(JSContext ctx) {
        return getBuiltinCalendar(ISO8601, ctx);
    }

    private static Object executeFunction(DynamicObject obj, String fnName, Object... funcArgs) {
        DynamicObject fn = (DynamicObject) JSObject.getMethod(obj, fnName);
        return JSRuntime.call(fn, obj, funcArgs);
    }

    // 12.1.9
    public static long calendarYear(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "year", dateLike);
    }

    // 12.1.10
    public static long calendarMonth(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "month", dateLike);
    }

    // 12.1.11
    public static String calendarMonthCode(DynamicObject calendar, DynamicObject dateLike) {
        Object result = executeFunction(calendar, "monthCode", dateLike);
        if (result == Undefined.instance) {
            throw Errors.createRangeError("");
        }
        return JSRuntime.toString(result);
    }

    // 12.1.12
    public static long calendarDay(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "day", dateLike);
    }

    // 12.1.13
    public static long calendarDayOfWeek(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "dayOfWeek", dateLike);
    }

    // 12.1.14
    public static long calendarDayOfYear(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "dayOfYear", dateLike);
    }

    // 12.1.15
    public static long calendarWeekOfYear(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "weekOfYear", dateLike);
    }

    // 12.1.16
    public static long calendarDaysInWeek(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "daysInWeek", dateLike);
    }

    // 12.1.17
    public static long calendarDaysInMonth(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "daysInMonth", dateLike);
    }

    // 12.1.18
    public static long calendarDaysInYear(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "daysInYear", dateLike);
    }

    // 12.1.19
    public static long calendarMonthsInYear(DynamicObject calendar, DynamicObject dateLike) {
        return (long) executeFunction(calendar, "monthsInYear", dateLike);
    }

    // 12.1.20
    public static boolean calendarInLeapYear(DynamicObject calendar, DynamicObject dateLike) {
        return (boolean) executeFunction(calendar, "inLeapYear", dateLike);
    }

    // 12.1.24
    public static Object toTemporalCalendar(Object itemParam, IsObjectNode isObject, JSToStringNode toString, JSContext ctx) {
        Object item = itemParam;
        if (isObject.executeBoolean(item)) {
            if (item instanceof TemporalCalendar) {
                return ((TemporalCalendar) item).getCalendar();
            }
            if (!JSObject.hasProperty((DynamicObject) item, CALENDAR)) {
                return item;
            }
            item = JSObject.get((DynamicObject) item, CALENDAR);
            if (isObject.executeBoolean(item) && !JSObject.hasProperty((DynamicObject) item, CALENDAR)) {
                return item;
            }
        }
        String string = toString.executeString(item);
        if (!isBuiltinCalendar(string)) {
            string = TemporalUtil.parseTemporalCalendarString(string);
        }
        return create(ctx, string);
    }

    // 12.1.32
    public static boolean isISOLeapYear(long year) {
        if (year % 4 != 0) {
            return false;
        }
        if (year % 400 == 0) {
            return true;
        }
        if (year % 100 == 0) {
            return false;
        }
        return true;
    }

    // 12.1.33
    public static long isoDaysInYear(long year) {
        if (isISOLeapYear(year)) {
            return 366;
        }
        return 365;
    }

    // 12.1.34
    public static long isoDaysInMonth(long year, long month) {
        assert month >= 1 && month <= 12;
        if (month == 1 || month == 3 || month == 5 || month == 7 || month == 8 || month == 10 || month == 12) {
            return 31;
        }
        if (month == 4 || month == 6 || month == 9 || month == 11) {
            return 30;
        }
        if (isISOLeapYear(year)) {
            return 29;
        }
        return 28;
    }

    // 12.1.35
    // Formula: https://cs.uwaterloo.ca/~alopez-o/math-faq/node73.html
    public static long toISODayOfWeek(long year, long month, long day) {
        long m = month - 2;
        if (m == -1) {  // Jan
            m = 11;
        } else if (m == 0) { // Feb
            m = 12;
        }
        long c = Math.floorDiv(year, 100);
        long y = Math.floorMod(year, 100);
        if (m == 11 || m == 12) {
            y = y - 1;
        }
        long weekDay = Math.floorMod((day + (long) Math.floor((2.6 * m) - 0.2) - (2 * c) + y + Math.floorDiv(y, 4) + Math.floorDiv(c, 4)), 7);
        if (weekDay == 0) { // Sunday
            return 7;
        }
        return weekDay;
    }

    // 12.1.36
    public static long toISODayOfYear(long year, long month, long day) {
        long days = 0;
        for (int m = 1; m < month; m++) {
            days += isoDaysInMonth(year, m);
        }
        return days + day;
    }

    // 12.1.37
    public static long toISOWeekOfYear(long year, long month, long day) {
        long doy = toISODayOfYear(year, month, day);
        long dow = toISODayOfWeek(year, month, day);
        long doj = toISODayOfWeek(year, 1, 1);

        long week = Math.floorDiv(doy - dow + 10, 7);
        if (week < 1) {
            if (doj == 5 || (doj == 6 && isISOLeapYear(year - 1))) {
                return 53;
            } else {
                return 52;
            }
        }
        if (week == 53) {
            if (isoDaysInYear(year) - doy < 4 - dow) {
                return 1;
            }
        }

        return week;
    }

    // 12.1.38
    public static Object resolveISOMonth(DynamicObject fields, JSStringToNumberNode stringToNumber, JSIdenticalNode identicalNode) {
        Object month = JSObject.get(fields, MONTH);
        Object monthCode = JSObject.get(fields, MONTH_CODE);
        if (TemporalUtil.isNullish(monthCode)) {
            if (TemporalUtil.isNullish(month)) {
                throw Errors.createTypeError("No month or month code present.");
            }
            return month;
        }
        assert monthCode instanceof String;
        int monthLength = ((String) monthCode).length();
        if (monthLength != 3) {
            throw Errors.createRangeError("Month code should be in 3 character code.");
        }
        String numberPart = ((String) monthCode).substring(1);
        double numberPart2 = stringToNumber.executeString(numberPart);
        if (Double.isNaN(numberPart2)) {
            throw Errors.createRangeError("The last character of the monthCode should be a number.");
        }

        long m1 = TemporalUtil.isNullish(month) ? -1 : TemporalUtil.asLong(month);
        long m2 = (long) numberPart2;

        if (!TemporalUtil.isNullish(month) && m1 != m2) {
            throw Errors.createRangeError("Month does not equal the month code.");
        }
        if (!identicalNode.executeBoolean(monthCode, TemporalUtil.buildISOMonthCode(numberPart))) {
            throw Errors.createRangeError("Not same value");
        }

        return (long) numberPart2;
    }

    // 12.1.39
    public static JSTemporalDateTimeRecord isoDateFromFields(DynamicObject fields, DynamicObject options, JSContext ctx, IsObjectNode isObject,
                    JSToBooleanNode toBoolean, JSToStringNode toString, JSStringToNumberNode stringToNumber,
                    JSIdenticalNode identicalNode) {
        assert isObject.executeBoolean(fields);
        String overflow = TemporalUtil.toTemporalOverflow(options, toBoolean, toString);
        DynamicObject preparedFields = TemporalUtil.prepareTemporalFields(fields, TemporalUtil.setDMMCY, TemporalUtil.toSet(), ctx);
        Object year = JSObject.get(preparedFields, YEAR);
        if (year == Undefined.instance) {
            throw TemporalErrors.createTypeErrorTemporalYearNotPresent();
        }
        Object month = resolveISOMonth(preparedFields, stringToNumber, identicalNode);
        Object day = JSObject.get(preparedFields, DAY);
        if (day == Undefined.instance) {
            throw TemporalErrors.createTypeErrorTemporalDayNotPresent();
        }
        return TemporalUtil.regulateISODate((Long) year, (Long) month, (Long) day, overflow);
    }

    // 12.1.40
    public static JSTemporalYearMonthDayRecord isoYearMonthFromFields(DynamicObject fields, DynamicObject options, JSContext ctx, IsObjectNode isObject,
                    JSToBooleanNode toBoolean,
                    JSToStringNode toString, JSStringToNumberNode stringToNumber,
                    JSIdenticalNode identicalNode) {
        assert isObject.executeBoolean(fields);
        String overflow = TemporalUtil.toTemporalOverflow(options, toBoolean, toString);
        DynamicObject preparedFields = TemporalUtil.prepareTemporalFields(fields, TemporalUtil.toSet(MONTH, MONTH_CODE, YEAR), TemporalUtil.toSet(), ctx);
        Object year = JSObject.get(preparedFields, YEAR);
        if (year == Undefined.instance) {
            throw TemporalErrors.createTypeErrorTemporalYearNotPresent();
        }
        Object month = resolveISOMonth(preparedFields, stringToNumber, identicalNode);

        JSTemporalYearMonthDayRecord result = TemporalUtil.regulateISOYearMonth(TemporalUtil.asLong(year), TemporalUtil.asLong(month), overflow);
        return JSTemporalYearMonthDayRecord.create(result.getYear(), result.getMonth(), 1);
    }

    // 12.1.41
    public static JSTemporalYearMonthDayRecord isoMonthDayFromFields(DynamicObject fields, DynamicObject options, JSContext ctx, IsObjectNode isObject,
                    JSToBooleanNode toBoolean, JSToStringNode toString, JSStringToNumberNode stringToNumber, JSIdenticalNode identicalNode) {
        assert isObject.executeBoolean(fields);
        String overflow = TemporalUtil.toTemporalOverflow(options, toBoolean, toString);
        DynamicObject preparedFields = TemporalUtil.prepareTemporalFields(fields, TemporalUtil.setDMMCY, TemporalUtil.toSet(), ctx);
        Object month = JSObject.get(preparedFields, MONTH);
        Object monthCode = JSObject.get(preparedFields, MONTH_CODE);
        Object year = JSObject.get(preparedFields, YEAR);
        if (!TemporalUtil.isNullish(month) && TemporalUtil.isNullish(monthCode) && TemporalUtil.isNullish(year)) {
            throw Errors.createTypeError("A year or a month code should be present.");
        }
        month = resolveISOMonth(preparedFields, stringToNumber, identicalNode);
        Object day = JSObject.get(preparedFields, DAY);
        if (day == Undefined.instance) {
            throw Errors.createTypeError("Day not present.");
        }
        long referenceISOYear = 1972;
        JSTemporalDateTimeRecord result = null;
        if (monthCode == Undefined.instance) {
            result = TemporalUtil.regulateISODate((Long) year, (Long) month, (Long) day, overflow);
        } else {
            result = TemporalUtil.regulateISODate(referenceISOYear, (Long) month, (Long) day, overflow);
        }
        return JSTemporalYearMonthDayRecord.create(referenceISOYear, result.getMonth(), result.getDay());
    }

    public static String isoMonthCode(TemporalMonth date) {
        long month = date.getISOMonth();
        return buildISOMonthCode(month);
    }

    @TruffleBoundary
    private static String buildISOMonthCode(long month) {
        String monthCode = String.format("%1$2d", month).replace(" ", "0");
        return "M".concat(monthCode);
    }

    // 12.1.45
    public static long isoDay(DynamicObject temporalObject) {
        TemporalDay day = (TemporalDay) temporalObject;
        return day.getISODay();
    }
}
