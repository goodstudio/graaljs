/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.instrumentation;

import com.oracle.truffle.js.nodes.instrumentation.JSTags;
import org.junit.Test;

public class AwaitTest extends FineGrainedAccessTest {

    @Test
    public void testAwaitLiteral() {
        String src = "(async function() { var x = await 42; return x + 1; })();";
        evalWithTags(src, new Class<?>[]{JSTags.ControlFlowBranchTag.class});

        // First, execution is suspended. A promise is created and returned.
        enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
            assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
            b.input(42);
            b.input(assertJSPromiseInput);
        }).exitExceptional();
        // When the promise resolves, the `await` node re-executes, and its return value is
        // returned.
        enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
            assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
            b.input(42);
        }).exit();
        // We intercept another control-flow-event, which is _not_ await
        enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
            assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Return.name());
            b.input(43);
        }).exit();
    }

    @Test
    public void testAwaitAsInput() {
        String src = "(async () => { return await 42 + await 43; })();";
        evalWithTags(src, new Class<?>[]{JSTags.ControlFlowBranchTag.class, JSTags.BinaryOperationTag.class});

        // return (suspend 1)
        enter(JSTags.ControlFlowBranchTag.class, (e0, b0) -> {
            assertAttribute(e0, TYPE, JSTags.ControlFlowBranchTag.Type.Return.name());
            // await 42 (suspend)
            enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
                assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b.input(42);
                b.input(assertJSPromiseInput);
            }).exitExceptional();

        }).exitExceptional();

        // return (suspend 2)
        enter(JSTags.ControlFlowBranchTag.class, (e0, b0) -> {
            assertAttribute(e0, TYPE, JSTags.ControlFlowBranchTag.Type.Return.name());
            // await 42
            enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
                assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b.input(42);
            }).exit();
            // await 43 (suspend)
            enter(JSTags.ControlFlowBranchTag.class, (e1, b1) -> {
                assertAttribute(e1, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b1.input(43);
                b1.input(assertJSPromiseInput);
            }).exitExceptional();
        }).exitExceptional();

        // return
        enter(JSTags.ControlFlowBranchTag.class, (e0, b0) -> {
            assertAttribute(e0, TYPE, JSTags.ControlFlowBranchTag.Type.Return.name());
            // await 43
            enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
                assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b.input(43);
            }).exit();
            // await 42
            enter(JSTags.BinaryOperationTag.class, (e1, b1) -> {
                b1.input(42);
                b1.input(43);
            }).exit();
            b0.input(85);
        }).exit();
    }

    @Test
    public void generatorWrapperAsInput() {
        String src = "(async () => { let val = await 42;})();";
        evalWithTags(src, new Class<?>[]{JSTags.WriteVariableTag.class, JSTags.ControlFlowBranchTag.class});

        // write (suspend)
        enter(JSTags.WriteVariableTag.class, (e0, b0) -> {
            assertAttribute(e0, NAME, "val");
            // await (suspend)
            enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
                assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b.input(42);
                b.input(assertJSPromiseInput);
            }).exitExceptional();
        }).exitExceptional();

        // write
        enter(JSTags.WriteVariableTag.class, (e0, b0) -> {
            assertAttribute(e0, NAME, "val");
            // await
            enter(JSTags.ControlFlowBranchTag.class, (e, b) -> {
                assertAttribute(e, TYPE, JSTags.ControlFlowBranchTag.Type.Await.name());
                b.input(42);
            }).exit();
            b0.input(42);
        }).exit();
    }
}
