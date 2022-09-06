/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Module imports, exports and custom sections
 *
 * @option webassembly
 */

load('../js/assert.js');

// (module
//    (type (func (result i32)))
//    (func (export "f") (type 0)
//       i32.const 0
//    )
//    (table (export "t") 0 0 funcref)
//    (memory (export "m") 0 0)
//    (global (export "g") (mut i32) (i32.const 0))
// )

var bytes = [0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
    0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7F,
    0x03, 0x02, 0x01, 0x00,
    0x04, 0x05, 0x01, 0x70, 0x01, 0x00, 0x00,
    0x05, 0x04, 0x01, 0x01, 0x00, 0x00,
    0x06, 0x06, 0x01, 0x7F, 0x01, 0x41, 0x00, 0x0B,
    0x07, 0x11, 0x04, 0x01, 0x66, 0x00, 0x00,
                      0x01, 0x74, 0x01, 0x00,
                      0x01, 0x6D, 0x02, 0x00,
                      0x01, 0x67, 0x03, 0x00,
    0x0A, 0x06, 0x01, 0x04, 0x00, 0x41, 0x00, 0x0B];
var module = new WebAssembly.Module(new Uint8Array(bytes));
var exports = WebAssembly.Module.exports(module);
assertSame(4, exports.length);
assertSame("f", exports[0].name);
assertSame("t", exports[1].name);
assertSame("m", exports[2].name);
assertSame("g", exports[3].name);

// (module
//    (type (func (result i32)))
//    (import "m" "f" (func (type 0)))
//    (import "m" "t" (table 0 0 funcref))
//    (import "m" "m" (memory 0 0))
//    (import "m" "g" (global (mut i32)))
// )
bytes = [0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
    0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7F,
    0x02, 0x1E, 0x04, 0x01, 0x6D, 0x01, 0x66, 0x00, 0x00,
                      0x01, 0x6D, 0x01, 0x74, 0x01, 0x70, 0x01, 0x00, 0x00,
                      0x01, 0x6D, 0x01, 0x6D, 0x02, 0x00, 0x00,
                      0x01, 0x6D, 0x01, 0x67, 0x03, 0x7F, 0x01];

module = new WebAssembly.Module(new Uint8Array(bytes));
var imports = WebAssembly.Module.imports(module);
assertSame(4, imports.length);
assertSame("f", imports[0].name);
assertSame("t", imports[1].name);
assertSame("m", imports[2].name);
assertSame("g", imports[3].name);

// (module
//    ("t" "\00")
// )
bytes = [0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
    0x00, 0x03, 0x01, 0x74, 0x00];
module = new WebAssembly.Module(new Uint8Array(bytes));
var customSections = WebAssembly.Module.customSections(module, "t");
assertSame(1, customSections.length);