/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

import {kindToViewState, viewStateToKind} from "../../client/common/link-utils";
import {assert} from "chai";

describe("common/link-utils", () => {
    describe("kindToViewState", () => {
        it("can resolve known entity kinds", () => {
            assert.equal(kindToViewState("APPLICATION"), "main.app.view");
        });
        it("can throws exception if an unknown kind is given", () => {
            assert.throws(() => kindToViewState("WIBBLE"));
        });
    });

    describe("viewStateToKind", () => {
        it("can resolve known states", () => {
            assert.equal(viewStateToKind("main.app.view"), "APPLICATION");
        });
        it("can throws exception if an unknown state is given", () => {
            assert.throws(() => kindToViewState("main.wibble.wobble"));
        });
    });
});
