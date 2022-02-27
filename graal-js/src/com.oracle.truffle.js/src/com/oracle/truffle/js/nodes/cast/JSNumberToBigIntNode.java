/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.cast;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSRuntime;

public abstract class JSNumberToBigIntNode extends JavaScriptBaseNode {

    public abstract Object execute(Object value);

    public final BigInt executeBigInt(Object value) {
        return (BigInt) execute(value);
    }

    public static JSNumberToBigIntNode create() {
        return JSNumberToBigIntNodeGen.create();
    }

    @Specialization
    protected BigInt doInteger(int value) {
        return BigInt.valueOf(value);
    }

    protected boolean doubleRepresentsSameValueAsLong(double value) {
        // (long) Math.pow(2, 63) == Long.MAX_VALUE
        // and (double) Long.MAX_VALUE == Math.pow(2, 63)
        // but Long.MAX_VALUE is one less than 2^63
        return JSRuntime.doubleIsRepresentableAsLong(value) && value != Long.MAX_VALUE;
    }

    @Specialization(guards = "doubleRepresentsSameValueAsLong(value)")
    protected BigInt doDoubleAsLong(double value) {
        return BigInt.valueOf((long) value);
    }

    @TruffleBoundary
    @Specialization(guards = "!doubleRepresentsSameValueAsLong(value)")
    protected BigInt doDoubleOther(double value) {
        if (!JSRuntime.isInteger(value)) {
            throw Errors.createRangeError("BigInt out of range");
        }
        long bits = Double.doubleToRawLongBits(value);
        boolean negative = (bits & 0x8000000000000000L) != 0;
        int exponentOffset = 1023;
        int mantissaLength = 52;
        int exponent = (int) ((bits & 0x7ff0000000000000L) >> mantissaLength) - exponentOffset - mantissaLength;
        long mantissa = (bits & 0x000fffffffffffffL) | 0x0010000000000000L;
        BigInteger bigInteger = BigInteger.valueOf(negative ? -mantissa : mantissa).shiftLeft(exponent);
        return new BigInt(bigInteger);
    }

    @Specialization(guards = "isJSNull(value)")
    protected static BigInt doNull(@SuppressWarnings("unused") Object value) {
        return BigInt.ZERO;
    }
}
