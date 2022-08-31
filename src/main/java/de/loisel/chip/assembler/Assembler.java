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

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * A simple Assembler for the LoChip's Assembly language
 */
public class Assembler {

    private static final String HEX_REGEX = "[$][0-9a-fA-F]+";

    private List<String> asmFile;

    Map<String, Label> labels = new HashMap<>();
    List<Byte> programBuff = new ArrayList<>();

    private final List<String> mnemonics = List.of(
            "DB", "define" // Only for assembler. Puts the following hex byte in program
            // instructions:
            , "CLS", "RET", "JP", "CALL", "JE"
            , "JNE", "LD", "ADD", "OR", "AND"
            , "XOR", "SUB", "SHR", "SUBN", "SHL"
            , "RND", "DRW", "JKP", "JKNP", "EXIT"
    );

    public byte[] assemble(String inputFileName) {
        this.asmFile = readInputFile(inputFileName);
        assemble();
        return copyProgram();
    }

    public byte[] assemble(String inputFileName, String outputFileName) {
        this.asmFile = readInputFile(inputFileName);
        assemble();
        byte[] program = copyProgram();
        writeOutputFile(outputFileName, program);
        return program;
    }

    public byte[] assemble(List<String> file) {
        this.asmFile = file;
        assemble();
        return copyProgram();
    }

    public byte[] assemble(List<String> file, String outputFileName) {
        this.asmFile = file;
        assemble();
        byte[] program = copyProgram();
        writeOutputFile(outputFileName, program);
        return program;
    }

    public byte[] copyProgram() {
        byte[] program = new byte[programBuff.size()];

        for (int i = 0; i < programBuff.size(); i++) {
            program[i] = programBuff.get(i);
        }

        return program;
    }

    private void assemble() {
        boolean firstCommand = true;

        // clean lines for later parsing
        List<String> cleaned = cleanLines();

        // get all labels from file
        getLabels(cleaned);

        // make room to add the reset vector later
        programBuff.add((byte) 0x0); programBuff.add((byte) 0x0);

        // parse commands line by line
        for(int lineNum = 1; lineNum <= cleaned.size(); lineNum++) {
            String line = cleaned.get(lineNum - 1);

            if(mnemonics.contains(line.split(" ")[0])) {

                // set reset vector to first opcode
                if(firstCommand
                        && !line.split(" ")[0].equals("DB")
                        && !line.split(" ")[0].equals("define")
                ) {
                    programBuff.set(0, (byte)(programBuff.size() >>> 8));
                    programBuff.set(1, (byte)(programBuff.size() & 0xFF));
                    firstCommand = false;
                }

                // next command
                writeBinForCmd(line, lineNum);

            } else if (!line.equals("") && line.charAt(line.length() - 1) == ':') {

                // new label
                String name = line.replace(":", "").strip();
                labels.get(name).destAddr = (short) (programBuff.size());

            } else if(!line.equals("") && line.charAt(line.length() - 1) != ':') {

                // command not found
                System.err.println("The command \"" + line + "\" at line " + lineNum + " was not found and ignored.");

            } // else empty line

            if(programBuff.size() > 0x10000) {
                System.err.println("Error: the assembled binary is too large to fit in Lo-Chip's memory.\n"
                + "Assembling was disrupted!.");
                break;
            }
        }

        // put the label addresses into the binary
        insertLabelAddresses();

        // Assembling should be finished
    }

    private void insertLabelAddresses() {
        labels.forEach((name, label) -> {
            for (Short callLocation : label.callLocations) {
                programBuff.set(callLocation, (byte)(label.destAddr >>> 8));
                programBuff.set(callLocation + 1, (byte)(label.destAddr & 0xFF));
            }
        });
    }

    private void writeBinForCmd(String line, int lineNum) {
        String command = line.split(" ")[0];
        String[] args = getArgs(line);

        switch (command) {
            case "DB": {        // put hex bytes into program
                if(hasArgs(args, lineNum, 1, command))
                    for (String strVal : args) {
                        if(strVal.matches(HEX_REGEX))
                            addByte(strVal);
                        else
                            System.err.println("Could not parse hex value \"" + strVal + "\" at line" + lineNum + ".");
                    }
                break;
            }
            case "CLS": {
                if(noArgs(args, lineNum))
                    addOpcode(0xE0);                                                  // $E0 - CLS
                break;
            }
            case "RET": {
                if(noArgs(args, lineNum))
                    addOpcode(0xEE);                                                  // $EE - RET
                break;
            }
            case "JP": {
                if(hasArgs(args, lineNum, 1, command)) {
                    if(args[0].equals("Rx")) {
                        addOpcode(0xB0);                                              // $B0 - JP Rx, addr
                        addWord(args[1]);
                    } else if(labels.containsKey(args[0])) {
                        addOpcode(0x10);                                              // $10 - JP label
                        addLabel(args[0]);
                    } else {
                        addOpcode(0x10);                                              // $10 - JP addr
                        addWord(args[0]);
                    }
                }
                break;
            }
            case "CALL": {
                if(hasArgs(args, lineNum, 1, command)) {
                    addOpcode(0x20);                                       
                    if(labels.containsKey(args[0]))                                   // $20 - CALL label
                        addLabel(args[0]);
                    else                                                              // $20 - CALL addr
                        addWord(args[0]);
                }
                break;
            }
            case "JE": {
                if(hasArgs(args, lineNum, 2, command)) {
                    if(argsMatch(args, "Rx", "Ry"))                              // $50 - JE Rx, Ry
                        addOpcode(0x50);
                    else {                                                              // $30 - JE Rx, b1
                        addOpcode(0x30);
                        addByte(args[0]);
                    }
                }
                break;
            }
            case "JNE": {
                if(hasArgs(args, lineNum, 2, command)) {
                    if(argsMatch(args, "Rx", "Ry"))
                        addOpcode(0x51);                                                // $51 - JNE Rx, Ry
                    else {
                        addOpcode(0x31);                                                // $31 - JNE Rx, b1
                        addByte(args[0]);
                    }
                }
                break;
            }
            case "LD": {
                if(hasArgs(args, lineNum, 2, command)) {

                    if(     args.length > 2
                            && args[0].equals("I")
                            && args[1].equals("Rx")
                            && args[2].equals("Ry")) {                                  // $FD - LD I, Rx, Ry
                        addOpcode(0xFD);
                    }
                    else if(args.length > 2
                            && args[0].equals("Rx")
                            && args[1].equals("Ry")
                            && args[2].equals("I")) {                                  // $FE - LD Rx, Ry, I
                        addOpcode(0xFE);
                    }
                    else if(argsMatch(args, "Rx", HEX_REGEX)) {                     // $60 - LD Rx, b1
                        addOpcode(0x60);
                        addByte(args[1]);
                    }
                    else if(argsMatch(args, "Ry", HEX_REGEX)) {                     // $61 - LD Ry, b1
                        addOpcode(0x61);
                        addByte(args[1]);
                    }
                    else if(argsMatch(args, "Rx", "I")) {                       // $62 - LD Rx, I
                        addOpcode(0x62);
                    }
                    else if(argsMatch(args, "Ry", "I")) {                       // $63 - LD Ry, I
                        addOpcode(0x63);
                    }
                    else if(argsMatch(args, "I", "Rx")) {                       // $64 - LD I, Rx
                        addOpcode(0x64);
                    }
                    else if(argsMatch(args, "I", "Ry")) {                       // $65 - LD I, Ry
                        addOpcode(0x65);
                    }
                    else if(argsMatch(args, "Rx", "Ry")) {                      // $80 - LD Rx, Ry
                        addOpcode(0x80);
                    }
                    else if(argsMatch(args, "Ry", "Rx")) {                      // $8A - LD Ry, Rx
                        addOpcode(0x8A);
                    }
                    else if(argsMatch(args, "I", HEX_REGEX)) {                     // $A0 - LD I, addr
                        addOpcode(0xA0);
                        addWord(args[1]);
                    }
                    else if(args[0].matches("I")
                            && labels.containsKey(args[1])) {                         // $A0 - LD I, label
                        addOpcode(0xA0);
                        addLabel(args[1]);
                    }
                    else if(argsMatch(args, "I", "RxRy")) {                     // $A1 - LD I, RxRy
                        addOpcode(0xA1);
                    }
                    else if(argsMatch(args, "Rx", "DT")) {                      // $F1 - LD Rx, DT
                        addOpcode(0xF1);
                    }
                    else if(argsMatch(args, "Rx", "K")) {                       // $F2 - LD Rx, K
                        addOpcode(0xF2);
                    }
                    else if(argsMatch(args, "DT", "Rx")) {                      // $F3 - LD DT, Rx
                        addOpcode(0xF3);
                    }
                    else if(argsMatch(args, "ST", "Rx")) {                      // $F4 - LD ST, Rx
                        addOpcode(0xF4);
                    }
                    else if(argsMatch(args, "B", "Rx")) {                       // $FC - LD B, Rx
                        addOpcode(0xFC);
                    }

                }
                break;
            }
            case "ADD": {
                if(hasArgs(args, lineNum, 2, command)) {
                    if(argsMatch(args, "Rx", HEX_REGEX)) {                          // $70 - ADD Rx, b1
                        addOpcode(0x70);
                        addByte(args[1]);
                    }
                    else if (argsMatch(args, "Ry", HEX_REGEX)) {                   // $71 - ADD Ry, b1
                        addOpcode(0x71);
                        addByte(args[1]);
                    }
                    else if (argsMatch(args, "Rx", "Ry")) {                     // $84 - ADD Rx, Ry
                        addOpcode(0x84);
                    }
                    else if (argsMatch(args, "I", "Rx")) {                      // $FA - ADD I, Rx
                        addOpcode(0xFA);
                    }
                }
                break;
            }
            case "OR": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "Ry")) {                       // $81 - OR Rx, Ry
                    addOpcode(0x81);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "Ry")) {
                    System.err.println("Wrong args for command \"OR\" in line " + lineNum + ".");
                }
                break;
            }
            case "AND": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "Ry")) {                       // $82 - AND Rx, Ry
                    addOpcode(0x82);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "Ry")) {
                    System.err.println("Wrong args for command \"AND\" in line " + lineNum + ".");
                }
                break;
            }
            case "XOR": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "Ry")) {                       // $83 - XOR Rx, Ry
                    addOpcode(0x83);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "Ry")) {
                    System.err.println("Wrong args for command \"XOR\" in line " + lineNum + ".");
                }
                break;
            }
            case "SUB": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "Ry")) {                       // $85 - SUB Rx, Ry
                    addOpcode(0x85);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "Ry")) {
                    System.err.println("Wrong args for command \"SUB\" in line " + lineNum + ".");
                }
                break;
            }
            case "SHR": {   // Todo: implement multiple shifts in one command
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "1")) {                        // $86 - SHR Rx, 1
                    addOpcode(0x86);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "1")) {
                    System.err.println("Wrong args for command \"SHR\" in line " + lineNum + ".");
                }
                break;
            }
            case "SUBN": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "Ry")) {                       // $87 - SUBN Rx, Ry
                    addOpcode(0x87);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "Ry")) {
                    System.err.println("Wrong args for command \"SUBN\" in line " + lineNum + ".");
                }
                break;
            }
            case "SHL": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", "1")) {                        // $8E - SHL Rx, 1
                    addOpcode(0x8E);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", "1")) {
                    System.err.println("Wrong args for command \"SHL\" in line " + lineNum + ".");
                }
                break;
            }
            case "RND": {
                if(hasArgs(args, lineNum, 2, command)
                        && argsMatch(args, "Rx", HEX_REGEX)) {                      // $C0 - RND Rx, b1
                    addOpcode(0xC0);
                    addByte(args[1]);
                } else if(hasArgs(args, lineNum, 2, command)
                        && !argsMatch(args, "Rx", HEX_REGEX)) {
                    System.err.println("Wrong args for command \"RND\" in line " + lineNum + ".");
                }
                break;
            }
            case "DRW": {
                if(hasArgs(args, lineNum, 2, command)) {
                    if(   args.length > 2
                            && argsMatch(args, "Rx", "Ry")
                            && args[2].matches(HEX_REGEX)) {                            // $D0 - DRW Rx, Ry, b1
                        addOpcode(0xD0);
                        addByte(args[2]);
                    }
                    else if(argsMatch(args, "Rx", "Ry")) {                       // $D1 - DRW Rx, Ry
                        addOpcode(0xD1);
                    }
                }
                break;
            }
            case "JKP": {
                if(hasArgs(args, lineNum, 1, command)
                        && args[0].matches("Rx")) {                               // $E1 - JKP Rx
                    addOpcode(0xE1);
                }
                break;
            }
            case "JKNP": {
                if(hasArgs(args, lineNum, 1, command)
                        && args[0].matches("Rx")) {                               // $E2 - JKNP Rx
                    addOpcode(0xE2);
                }
                break;
            }
            case "EXIT": {
                if(noArgs(args, lineNum)) {                                            // $AA - EXIT
                    addOpcode(0xAA);
                }
                break;
            }
            default:
                System.err.println("Command \"" + command + "\" at line "
                        + lineNum +" is not implemented and was ignored");
                break;
        }
    }

    private boolean argsMatch(String[] args, String a1, String a2) {
        return args[0].matches(a1) && args[1].matches(a2);
    }
    
    private void addOpcode(int opcode) {
        programBuff.add((byte) opcode);
    }

    private void addLabel(String name) {
        addOpcode(0x00); addOpcode(0x00);
        labels.get(name).callLocations.add((short) (programBuff.size() - 1));
    }

    private void addByte(String byteStr) {
        byte byt = HexFormat.of().withPrefix("$").parseHex(byteStr)[0];
        programBuff.add(byt);
    }

    private void addWord(String wordStr) {
        wordStr = wordStr.substring(wordStr.indexOf('$') + 1);
        byte[] addr = HexFormat.of().parseHex(wordStr);
        if(addr.length == 1) {
            addOpcode(0x0);
            programBuff.add(addr[0]);
        } else if(addr.length == 2) {
            programBuff.add(addr[0]);
            programBuff.add(addr[1]);
        }
    }

    private static boolean hasArgs(String[] args, int lineNum, int amount, String command) {
        if(args != null && args.length >= amount) {
            return true;
        } else {
            System.err.println("Missing args for command \"" + command
            + "\" in line: " + lineNum);
            return false;
        }
    }

    private static boolean noArgs(String[] args, int lineNum) {
        if(args != null && args.length > 0) {
            System.err.println("Unexpected arguments \"" + Arrays.toString(args) + "\" at line " + lineNum + ".");
            return false;
        } else {
            return true;
        }
    }

    private static String[] getArgs(String line) {
        if(line.indexOf(' ') == -1)
            return new String[0];
        String[] args = line.substring(line.indexOf(' ')).split(",");
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].trim();
        }
        return args;
    }

    private void getLabels(List<String> cleaned) {
        labels = new HashMap<>();

        for (int i = 1; i <= cleaned.size(); i++) {
            String line = cleaned.get(i - 1);

            if(line.indexOf(':') == line.length() - 1 && line.length() > 1) {
                String name = line.split(" ")[0].split(":")[0];
                labels.put(
                        name,
                        new Label(name, i
                ));
            }
        }
    }

    /**
     * clean the lines from comments, leading and trailing whitespaces,
     * multiple whitespaces and indentations.
     */
    private List<String> cleanLines() {
        List<String> cleaned = new ArrayList<>();

        for (String line : asmFile) {
            if(line.indexOf(';') > -1)
                line = line.substring(0, line.indexOf(';'));
            line = line.strip();
            line = line.replace('\t', ' ');
            line = line.replaceAll("\s{2,}", " ");

            cleaned.add(line);
        }

        return cleaned;
    }

    private static List<String> readInputFile(String inputFileName) {
        List<String> strProgram = new ArrayList<>();

        try(BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            for(String line; (line = br.readLine()) != null; ) {
                strProgram.add(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return strProgram;
    }

    private static void writeOutputFile(String outputFile, byte[] program) {

        try {
            File delFile = new File(outputFile);
            Files.deleteIfExists(delFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(program);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * takes the input and output file name from args and
     * generates a binary file for the Lo-Chip.
     * Ignores every additional argument
     * @throws ArgumentsMissingException when there are less than 2 arguments
     */
    public static void main(String[] args) throws ArgumentsMissingException {
        if(args != null && args.length >= 1) {
            Assembler assembler = new Assembler();
            assembler.assemble(args[0], args[1]);
        } else {
            throw new ArgumentsMissingException("Input file and output file missing in arguments!");
        }
    }
}
