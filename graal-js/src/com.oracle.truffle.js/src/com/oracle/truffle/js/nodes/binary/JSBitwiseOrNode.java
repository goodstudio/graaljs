/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.binary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.Truncatable;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantIntegerNode;
import com.oracle.truffle.js.nodes.cast.JSToInt32Node;
import com.oracle.truffle.js.nodes.cast.JSToNumericNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.SafeInteger;

import java.util.Set;

@NodeInfo(shortName = "|")
public abstract class JSBitwiseOrNode extends JSBinaryNode {

    protected JSBitwiseOrNode(JavaScriptNode left, JavaScriptNode right) {
        super(left, right);
    }

    public static JavaScriptNode create(JavaScriptNode left, JavaScriptNode right) {
        Truncatable.truncate(left);
        if (right instanceof JSConstantIntegerNode) {
            int rightValue = ((JSConstantIntegerNode) right).executeInt(null);
            if (rightValue == 0) {
                return JSToInt32Node.create(left, true);
            } else if (JSConfig.UseSuperOperations) {
                return JSBitwiseOrConstantNode.create(left, rightValue);
            }
        }
        Truncatable.truncate(right);
        return JSBitwiseOrNodeGen.create(left, right);
    }

    public abstract Object executeObject(Object a, Object b);

    @Specialization
    protected int doInteger(int a, int b) {
        return a | b;
    }

    @Specialization
    protected int doSafeIntegerInt(SafeInteger a, int b) {
        return doInteger(a.intValue(), b);
    }

    @Specialization
    protected int doIntSafeInteger(int a, SafeInteger b) {
        return doInteger(a, b.intValue());
    }

    @Specialization
    protected int doSafeInteger(SafeInteger a, SafeInteger b) {
        return doInteger(a.intValue(), b.intValue());
    }

    @Specialization
    protected int doDouble(double a, double b,
                    @Cached("create()") JSToInt32Node leftInt32,
                    @Cached("create()") JSToInt32Node rightInt32) {
        return doInteger(leftInt32.executeInt(a), rightInt32.executeInt(b));
    }

    @Specialization
    protected BigInt doBigInt(BigInt a, BigInt b) {
        return a.or(b);
    }

    @Specialization(guards = {"aHasOverloadedOperatorsNode.execute(a) || bHasOverloadedOperatorsNode.execute(b)"})
    protected Object doOverloaded(Object a, Object b,
                    @Cached("create()") @SuppressWarnings("unused") HasOverloadedOperatorsNode aHasOverloadedOperatorsNode,
                    @Cached("create()") @SuppressWarnings("unused") HasOverloadedOperatorsNode bHasOverloadedOperatorsNode,
                    @Cached("createNumeric(getOverloadedOperatorName())") JSOverloadedBinaryNode overloadedOperatorNode) {
        return overloadedOperatorNode.execute(a, b);
    }

    protected String getOverloadedOperatorName() {
        return "|";
    }

    @Specialization(guards = {"!aHasOverloadedOperatorsNode.execute(a)", "!bHasOverloadedOperatorsNode.execute(b)"}, replaces = {"doInteger", "doIntSafeInteger", "doSafeIntegerInt", "doSafeInteger",
                    "doDouble", "doBigInt"})
    protected Object doGeneric(Object a, Object b,
                    @Cached("create()") @SuppressWarnings("unused") HasOverloadedOperatorsNode aHasOverloadedOperatorsNode,
                    @Cached("create()") @SuppressWarnings("unused") HasOverloadedOperatorsNode bHasOverloadedOperatorsNode,
                    @Cached("create()") JSToNumericNode leftNumeric,
                    @Cached("create()") JSToNumericNode rightNumeric,
                    @Cached("createInner()") JSBitwiseOrNode or,
                    @Cached("create()") BranchProfile mixedNumericTypes) {
        Object left = leftNumeric.execute(a);
        Object right = rightNumeric.execute(b);
        ensureBothSameNumericType(left, right, mixedNumericTypes);
        return or.executeObject(left, right);
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        return JSBitwiseOrNodeGen.create(cloneUninitialized(getLeft(), materializedTags), cloneUninitialized(getRight(), materializedTags));
    }

    public static final JSBitwiseOrNode createInner() {
        return JSBitwiseOrNodeGen.create(null, null);
    }
}
