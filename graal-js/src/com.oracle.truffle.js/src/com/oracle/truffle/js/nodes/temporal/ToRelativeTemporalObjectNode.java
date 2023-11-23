/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.temporal;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.CONSTRAIN;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.OFFSET;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.OVERFLOW;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.RELATIVE_TO;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.TIME_ZONE;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.access.PropertyGetNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalCalendarHolder;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDateTimeRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDate;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.ParseISODateTimeResult;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

/**
 * Implementation of the toRelativeTemporalObject operation.
 */
public abstract class ToRelativeTemporalObjectNode extends JavaScriptBaseNode {

    @Child private PropertyGetNode getRelativeToNode;
    @Child private PropertyGetNode getOffsetNode;
    @Child private PropertyGetNode getTimeZoneNode;

    protected ToRelativeTemporalObjectNode() {
        JSContext ctx = JavaScriptLanguage.get(null).getJSContext();
        this.getRelativeToNode = PropertyGetNode.create(RELATIVE_TO, ctx);
        this.getOffsetNode = PropertyGetNode.create(OFFSET, ctx);
        this.getTimeZoneNode = PropertyGetNode.create(TIME_ZONE, ctx);
    }

    public abstract JSTemporalCalendarHolder execute(JSDynamicObject options);

    @Specialization
    protected JSTemporalCalendarHolder toRelativeTemporalObject(JSDynamicObject options,
                    @Cached InlinedBranchProfile errorBranch,
                    @Cached InlinedConditionProfile valueIsObject,
                    @Cached InlinedConditionProfile valueIsUndefined,
                    @Cached InlinedBranchProfile valueIsPlainDate,
                    @Cached InlinedBranchProfile valueIsZonedDateTime,
                    @Cached InlinedConditionProfile valueIsPlainDateTime,
                    @Cached InlinedConditionProfile timeZoneAvailable,
                    @Cached JSToStringNode toStringNode,
                    @Cached IsObjectNode isObjectNode,
                    @Cached ToTemporalCalendarWithISODefaultNode toTemporalCalendarWithISODefaultNode,
                    @Cached TemporalCalendarFieldsNode calendarFieldsNode,
                    @Cached TemporalCalendarDateFromFieldsNode dateFromFieldsNode,
                    @Cached ToTemporalTimeZoneNode toTemporalTimeZoneNode,
                    @Cached GetTemporalCalendarWithISODefaultNode getTemporalCalendarWithISODefaultNode,
                    @Cached TemporalGetOptionNode getOptionNode) {
        Object value = getRelativeToNode.getValue(options);
        if (valueIsUndefined.profile(this, value == Undefined.instance)) {
            return null;
        }
        JSTemporalDateTimeRecord result;
        JSDynamicObject timeZone = Undefined.instance;
        JSDynamicObject calendar;
        Object offset;
        TemporalUtil.OffsetBehaviour offsetBehaviour = TemporalUtil.OffsetBehaviour.OPTION;
        TemporalUtil.MatchBehaviour matchBehaviour = TemporalUtil.MatchBehaviour.MATCH_EXACTLY;
        JSContext ctx = getLanguage().getJSContext();
        JSRealm realm = getRealm();
        if (value instanceof JSTemporalPlainDateObject plainDate) {
            valueIsPlainDate.enter(this);
            return plainDate;
        } else if (value instanceof JSTemporalZonedDateTimeObject zonedDateTime) {
            valueIsZonedDateTime.enter(this);
            return zonedDateTime;
        }
        if (valueIsObject.profile(this, isObjectNode.executeBoolean(value) && value instanceof JSObject)) {
            JSObject valueObj = (JSObject) value;
            if (valueIsPlainDateTime.profile(this, valueObj instanceof JSTemporalPlainDateTimeObject)) {
                JSTemporalPlainDateTimeObject pd = (JSTemporalPlainDateTimeObject) valueObj;
                return JSTemporalPlainDate.create(ctx, realm, pd.getYear(), pd.getMonth(), pd.getDay(), pd.getCalendar(), this, errorBranch);
            }
            calendar = getTemporalCalendarWithISODefaultNode.execute(valueObj);
            List<TruffleString> fieldNames = calendarFieldsNode.execute(calendar, TemporalUtil.listDHMMMMMNSY);
            JSDynamicObject fields = TemporalUtil.prepareTemporalFields(ctx, valueObj, fieldNames, TemporalUtil.listEmpty);

            JSDynamicObject dateOptions = JSOrdinary.createWithNullPrototype(ctx);
            JSObjectUtil.putDataProperty(dateOptions, OVERFLOW, CONSTRAIN);
            result = TemporalUtil.interpretTemporalDateTimeFields(calendar, fields, dateOptions, getOptionNode, dateFromFieldsNode);
            offset = getOffsetNode.getValue(valueObj);
            Object timeZoneTemp = getTimeZoneNode.getValue(valueObj);
            if (timeZoneTemp != Undefined.instance) {
                timeZone = toTemporalTimeZoneNode.execute(timeZoneTemp);
            }
            if (offset == Undefined.instance) {
                offsetBehaviour = TemporalUtil.OffsetBehaviour.WALL;
            }
        } else {
            TruffleString string = toStringNode.executeString(value);
            ParseISODateTimeResult resultZDT = TemporalUtil.parseTemporalRelativeToString(string);
            result = resultZDT;
            calendar = toTemporalCalendarWithISODefaultNode.execute(result.getCalendar());

            offset = resultZDT.getTimeZoneResult().getOffsetString();
            TruffleString timeZoneName = resultZDT.getTimeZoneResult().getName();
            if (timeZoneName != null) {
                // If ParseText(! StringToCodePoints(timeZoneName), TimeZoneNumericUTCOffset)
                // is not a List of errors
                if (!TemporalUtil.isValidTimeZoneName(timeZoneName)) {
                    errorBranch.enter(this);
                    throw TemporalErrors.createRangeErrorInvalidTimeZoneString();
                }
                timeZoneName = TemporalUtil.canonicalizeTimeZoneName(timeZoneName);
                timeZone = TemporalUtil.createTemporalTimeZone(ctx, realm, timeZoneName);
            }

            if (resultZDT.getTimeZoneResult().isZ()) {
                offsetBehaviour = TemporalUtil.OffsetBehaviour.EXACT;
            } else {
                offsetBehaviour = TemporalUtil.OffsetBehaviour.WALL;
            }
            matchBehaviour = TemporalUtil.MatchBehaviour.MATCH_MINUTES;
        }
        assert timeZone != null;
        if (timeZoneAvailable.profile(this, timeZone != Undefined.instance)) {
            Object offsetNs = 0;
            if (offsetBehaviour == TemporalUtil.OffsetBehaviour.OPTION) {
                offsetNs = TemporalUtil.parseTimeZoneOffsetString(toStringNode.executeString(offset));
            } else {
                offsetNs = Undefined.instance;
            }
            BigInt epochNanoseconds = TemporalUtil.interpretISODateTimeOffset(ctx, realm,
                            result.getYear(), result.getMonth(), result.getDay(), result.getHour(), result.getMinute(), result.getSecond(), result.getMillisecond(),
                            result.getMicrosecond(), result.getNanosecond(), offsetBehaviour, offsetNs, timeZone, TemporalUtil.Disambiguation.COMPATIBLE, TemporalUtil.OffsetOption.REJECT,
                            matchBehaviour);
            return JSTemporalZonedDateTime.create(ctx, realm, epochNanoseconds, timeZone, calendar);
        }
        return JSTemporalPlainDate.create(ctx, realm, result.getYear(), result.getMonth(), result.getDay(), calendar, this, errorBranch);
    }
}
