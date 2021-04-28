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
package com.oracle.truffle.js.builtins;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.DAYS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.HOURS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MICROSECONDS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MILLISECONDS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MINUTES;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTHS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.NANOSECONDS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.SECONDS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.WEEKS;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.YEARS;
import static com.oracle.truffle.js.runtime.util.TemporalUtil.getLong;

import java.util.Collections;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.builtins.TemporalPlainDatePrototypeBuiltinsFactory.JSTemporalPlainDateAddNodeGen;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsLongNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSTemporalDuration;
import com.oracle.truffle.js.runtime.builtins.JSTemporalPlainDate;
import com.oracle.truffle.js.runtime.builtins.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTimePluralRecord;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

public class TemporalPlainDatePrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalPlainDatePrototypeBuiltins.TemporalPlainDatePrototype> {

    public static final JSBuiltinsContainer BUILTINS = new TemporalPlainDatePrototypeBuiltins();

    protected TemporalPlainDatePrototypeBuiltins() {
        super(JSTemporalPlainDate.PROTOTYPE_NAME, TemporalPlainDatePrototype.class);
    }

    public enum TemporalPlainDatePrototype implements BuiltinEnum<TemporalPlainDatePrototype> {
        add(1);
        // subtract(1);
        // with(2),
        // until(2),
        // since(2),
        // round(1),
        // equals(1),
        // toPlainDate(1),
        // toZonedDate(1),
        // getISOFields(0),
        // toString(1),
        // toLocaleString(0),
        // toJSON(0),
        // valueOf(0);

        private final int length;

        TemporalPlainDatePrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalPlainDatePrototype builtinEnum) {
        switch (builtinEnum) {
            case add:
                return JSTemporalPlainDateAddNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            // case subtract:
            // return JSTemporalPlainDateSubtractNodeGen.create(context, builtin,
            // args().withThis().fixedArgs(2).createArgumentNodes(context));
// case with:
// return JSTemporalPlainDateWithNodeGen.create(context, builtin,
// args().withThis().fixedArgs(2).createArgumentNodes(context));
// case until:
// return JSTemporalPlainDateUntilNodeGen.create(context, builtin,
// args().withThis().fixedArgs(2).createArgumentNodes(context));
// case since:
// return JSTemporalPlainDateSinceNodeGen.create(context, builtin,
// args().withThis().fixedArgs(2).createArgumentNodes(context));
// case round:
// return JSTemporalPlainDateRoundNodeGen.create(context, builtin,
// args().withThis().fixedArgs(1).createArgumentNodes(context));
// case equals:
// return JSTemporalPlainDateEqualsNodeGen.create(context, builtin,
// args().withThis().fixedArgs(1).createArgumentNodes(context));
// case toPlainDate:
// return JSTemporalPlainDateToPlainDateNodeGen.create(context, builtin,
// args().withThis().fixedArgs(1).createArgumentNodes(context));
// case toZonedDate:
// return JSTemporalPlainDateToZonedDateNodeGen.create(context, builtin,
// args().withThis().fixedArgs(1).createArgumentNodes(context));
// case getISOFields:
// return JSTemporalPlainDateGetISOFieldsNodeGen.create(context, builtin,
// args().withThis().createArgumentNodes(context));
// case toString:
// return JSTemporalPlainDateToStringNodeGen.create(context, builtin,
// args().withThis().fixedArgs(1).createArgumentNodes(context));
// case toLocaleString:
// case toJSON:
// return JSTemporalPlainDateToLocaleStringNodeGen.create(context, builtin,
// args().withThis().createArgumentNodes(context));
// case valueOf:
// return JSTemporalPlainDateValueOfNodeGen.create(context, builtin,
// args().withThis().createArgumentNodes(context));
        }
        return null;
    }

    // 4.3.10
    public abstract static class JSTemporalPlainDateAdd extends JSBuiltinNode {

        protected JSTemporalPlainDateAdd(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public DynamicObject add(DynamicObject thisObj, DynamicObject temporalDurationLike, Object optParam,
                        @Cached("create()") IsObjectNode isObject,
                        @Cached("create()") JSToStringNode toString,
                        @Cached("create()") JSToIntegerAsLongNode toInt) {
            JSTemporalPlainDateObject date = (JSTemporalPlainDateObject) thisObj;
            DynamicObject duration = JSTemporalDuration.toLimitedTemporalDuration(temporalDurationLike,
                            Collections.emptySet(), getContext(), isObject, toString, toInt);
            JSTemporalDuration.rejectDurationSign(
                            getLong(duration, YEARS),
                            getLong(duration, MONTHS),
                            getLong(duration, WEEKS),
                            getLong(duration, DAYS),
                            getLong(duration, HOURS),
                            getLong(duration, MINUTES),
                            getLong(duration, SECONDS),
                            getLong(duration, MILLISECONDS),
                            getLong(duration, MICROSECONDS),
                            getLong(duration, NANOSECONDS));
            JSTemporalPlainDateTimePluralRecord balanceResult = JSTemporalDuration.balanceDuration(
                            getLong(duration, DAYS),
                            getLong(duration, HOURS),
                            getLong(duration, MINUTES),
                            getLong(duration, SECONDS),
                            getLong(duration, MILLISECONDS),
                            getLong(duration, MICROSECONDS),
                            getLong(duration, NANOSECONDS), "days", Undefined.instance);
            DynamicObject balancedDuration = JSTemporalDuration.createTemporalDuration(getLong(duration, YEARS),
                            getLong(duration, MONTHS),
                            getLong(duration, WEEKS),
                            balanceResult.getDays(), 0, 0, 0, 0, 0, 0, getContext());
            DynamicObject options = TemporalUtil.getOptionsObject(optParam, getContext());
            return TemporalUtil.calendarDateAdd(date.getCalendar(), thisObj, balancedDuration, options, Undefined.instance);
        }
    }

}
