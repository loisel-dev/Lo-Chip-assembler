/*
 * Copyright 2022 Elias Taufer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.loisel.chip.assembler;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssemblerFileTest {

    private final String SMPL_FILE = "error-test.asm";

    String resourcePath;

    AssemblerFileTest() {
        String path = "src" + File.separator + "test" + File.separator + "resources" + File.separator;

        File file = new File(path);
        resourcePath = file.getAbsolutePath() + File.separator;
    }

    @Test
    void simpleAsmTest() {
        Assembler assembler = new Assembler();
        byte[] program = assembler.assemble(resourcePath + SMPL_FILE);
    }

}
