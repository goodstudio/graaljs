/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.cast.JSToBooleanNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;
import com.oracle.truffle.js.runtime.util.TemporalUtil.OptionType;

/**
 * Implementation of GetOption() operation.
 */
public abstract class TemporalGetOptionNode extends JavaScriptBaseNode {

    private final ConditionProfile isFallbackProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorBranch = BranchProfile.create();

    @Child private JSToStringNode toStringNode;
    @Child private JSToBooleanNode toBooleanNode;
    @Child private JSToNumberNode toNumberNode;

    protected TemporalGetOptionNode() {
    }

    public static TemporalGetOptionNode create() {
        return TemporalGetOptionNodeGen.create();
    }

    public abstract Object execute(DynamicObject options, TruffleString property, TemporalUtil.OptionType types, List<?> values, Object fallback);

    @Specialization
    protected Object getOption(DynamicObject options, TruffleString property, TemporalUtil.OptionType types, List<?> values, Object fallback) {
        assert JSRuntime.isObject(options);
        Object value = JSObject.get(options, property);
        if (isFallbackProfile.profile(value == Undefined.instance)) {
            return fallback;
        }
        OptionType type;
        if (value instanceof Boolean && types.allowsBoolean()) {
            type = TemporalUtil.OptionType.BOOLEAN;
        } else if (Strings.isTString(value) && types.allowsString()) {
            type = TemporalUtil.OptionType.STRING;
        } else if (JSRuntime.isNumber(value) && types.allowsNumber()) {
            type = OptionType.NUMBER;
        } else {
            type = types.getLast();
        }
        if (type.allowsBoolean()) {
            value = toBoolean(value);
        } else if (type.allowsNumber()) {
            value = toNumber(value);
            if (Double.isNaN(((Number) value).doubleValue())) {
                throw TemporalErrors.createRangeErrorNumberIsNaN();
            }
        } else if (type.allowsString()) {
            value = toStringNode(value);
        }
        if (value != Undefined.instance && !Boundaries.listContainsUnchecked(values, value)) {
            errorBranch.enter();
            throw TemporalErrors.createRangeErrorOptionsNotContained(values, value);
        }
        return value;
    }

    private TruffleString toStringNode(Object value) {
        if (toStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStringNode = insert(JSToStringNode.create());
        }
        return toStringNode.executeString(value);
    }

    private boolean toBoolean(Object value) {
        if (toBooleanNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toBooleanNode = insert(JSToBooleanNode.create());
        }
        return toBooleanNode.executeBoolean(value);
    }

    private Number toNumber(Object value) {
        if (toNumberNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toNumberNode = insert(JSToNumberNode.create());
        }
        return toNumberNode.executeNumber(value);
    }
}
