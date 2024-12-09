// Run with `java Main.java test`
// Version 2024-11-18
// Changes:
//  2024-12-04 Explicitly close output stream after file was assembled.
//  2024-11-18 Fix splitting of lines to be independent of OS
//  2024-11-18 First version
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static boolean iUsedAi() {
        return false;
    }

    public static String aiExplanation() {
        return "I did not use any AI, only the resources available from Nand2Tetris, Youtube & Moodle.";
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main test | $file.nha");
            return;
        }

        String inputFile = args[0];
        if (!inputFile.endsWith(".nha") && !inputFile.equals("test")) {
            System.err.println("Unrecognized command or file type: " + inputFile);
            return;
        }

        if (inputFile.equals("test")) {
            test();
            return;
        }

        // looks like inputFile is indeed a file with an .nha ending
        int suffix = inputFile.lastIndexOf(".");
        String outputFile = inputFile.substring(0, suffix) + ".bin";
        try {
            Assembler asm = new Assembler(inputFile, outputFile);
            asm.assemble();
            asm.close();
        } catch (IOException ex) {
            System.err.println("Exception parsing: " + inputFile);
            System.err.println(ex.toString());
        }
    }

    private static void test() {
        String[] testNames = new String[]{"AInst21", "CInst", "Add"};
        String[] testInput = new String[]{
                TestInput.AInst21, TestInput.CInstAsm,
                TestInput.AddAsm};
        String[] testOutput = new String[]{
                TestOutput.AInst21Bin, TestOutput.CInstBin,
                TestOutput.AddBin};

        for (int i = 0; i < testNames.length; i += 1) {
            runTest(testNames[i], testInput[i].trim(), testOutput[i].trim());
        }

        System.out.println("\n");

        try {
            boolean usedAi = iUsedAi();
            System.out.println("I used AI: " + usedAi);
        } catch (RuntimeException ex) {
            System.err.println("Main.iUsedAi() method not yet adapted");
            System.err.println(ex.getMessage());
        }

        try {
            String reasoning = aiExplanation();
            System.out.println("My reasoning: " + reasoning);
        } catch (RuntimeException ex) {
            System.err.println("Main.aiExplanation() method not yet adapted");
            System.err.println(ex.getMessage());
        }
    }

    private static void runTest(String name, String input, String expected) {
        StringWriter output = new StringWriter();
        Assembler asm = new Assembler(input, output);

        try {
            asm.assemble();
        } catch (IOException ex) {
            System.err.println("Exception parsing test input for " + name);
            return;
        } catch (Throwable t) {
            System.err.println("Test failed with exception: " + name);
            System.err.println(t.toString());
            return;
        }

        String outputStr = output.toString().trim();

        if (expected.equals(outputStr)) {
            System.out.println("Test " + name + " passed.");
        } else {
            System.out.println("Test " + name + " failed.");
            printDiff(expected, outputStr, asm.getInput());
        }
    }

    private static void printDiff(String expected, String actual, List<String> input) {
        String[] expectedLines = expected.split("\n");
        String[] actualLines = actual.split("\n");

        int inputLine = 0;
        int i = 0;
        for (; i < expectedLines.length; i += 1, inputLine += 1) {
            while (inputLine < input.size() && input.get(inputLine).isEmpty()) {
                inputLine += 1;
            }

            String instruction = inputLine < input.size() ? input.get(inputLine) : "";

            if (actualLines.length <= i) {
                System.err.printf("line %3d: %s\t", i + 1, instruction);
                System.err.println(expectedLines[i] + " != missing");
                continue;
            }

            if (!expectedLines[i].equals(actualLines[i])) {
                System.err.printf("line %3d: %s\t", i + 1, instruction);
                System.err.println(expectedLines[i] + " != " + actualLines[i]);
            }
        }

        for (; i < actualLines.length; i += 1) {
            while (inputLine < input.size() && input.get(inputLine).isEmpty()) {
                inputLine += 1;
            }

            String instruction = inputLine < input.size() ? input.get(inputLine) : "";

            System.err.printf("line %3d: %s\t", i + 1, instruction);
            System.err.println(" != " + actualLines[i]);
        }
    }
}

class TestInput {
    public final static String AInst21 = "ldr A, $21";

    public final static String CInstAsm = """
            ldr D, (A)
            sub D, D, (A)
            jgt D
            ldr D, (A)
            jmp
            str (A), D
            """;

    public final static String AddAsm = """
            ldr A, $2
            ldr D, A
            ldr A, $3
            add D, D, A
            ldr A, $0
            str (A), D
            """;
}

class TestOutput {
    public static final String AInst21Bin = "0000000000010101";

    public final static String CInstBin = """
            1111110000010000
            1111010011010000
            1110001100000001
            1111110000010000
            1110101010000111
            1110001100001000
            """;

    public static final String AddBin = """
            0000000000000010
            1110110000010000
            0000000000000011
            1110000010010000
            0000000000000000
            1110001100001000""";
}

class Assembler {
    private final List<String> input;
    private final Writer output;
    private boolean isInvalid = false;

    public Assembler(String inputFile, String outputFile) throws IOException {
        input = Files.readAllLines(Paths.get(inputFile));
        output = new PrintWriter(new FileWriter(outputFile));
    }

    public Assembler(String input, StringWriter output) {
        this.input = Arrays.asList(input.split("(\n)|(\r\n)|(\r)"));
        this.output = output;
    }

    private void output(String bin) throws IOException {
        output.write(bin);
        output.write('\n');
    }

    public void close() throws IOException {
        output.close();
    }

    /**
     * Reads each line of the input and parses it as an instruction, and then it gets translated.
     * The binary output is written to the provided bin.
     * The binary is a string of 0 and 1s depending on the mnemonics passed, if an instruction that's not valid is passed it will end the processing
     * of the instructions in the input.
     * @throws IOException for if an I/O error occurs during read or write.
     */
    public void assemble() throws IOException {
        int lineNumber = 0;

        for (String line : input) {
            lineNumber++;
            if (isInvalid) break;

            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",");
            List<String> instructions = new java.util.ArrayList<>();

            for (String part : parts) {
                for (String temp : part.trim().split("\\s+")) {
                    if (!temp.isEmpty()) instructions.add(temp);
                }
            }

            if (instructions.isEmpty()) {
                continue;
            }

            String mnemonic = instructions.get(0).toLowerCase();
            List<String> operands = instructions.subList(1, instructions.size());
            String binary = null;

            switch (mnemonic) {
                case "ldr" -> binary = handleLdr(operands);
                case "str" -> binary = handleStr(operands);
                case "add" -> binary = handleAdd(operands);
                case "sub" -> binary = handleSub(operands);
                case "jmp" , "jgt", "jeq", "jge", "jlt", "jne", "jle" -> binary = handleJump(mnemonic, operands);
            }

            if (binary == null) {
                isInvalid = true;
                break;
            }

            output(binary);
        }
    }

    public List<String> getInput() {
        return input;
    }

    /**
     * Determines the c-bits for the source operand passed as the argument, the c-bits define which register/memory is read.
     * @param source The source operand which is either A, D, or (A).
     * @return A 6-bit string of 0 and 1 representing the c-bits if valid operand, else it will return null.
     */
    private String getCBits(String source) {
        switch (source) {
            case "A", "(A)" -> {return "110000";}
            case "D" -> {return "001100";}
        }
        return null;
    }

    /**
     * Determines the d-bits for the target operand passed as the argument, the d-bits define which register will receive the C-instruction.
     * @param target The target operand, which is either A or D.
     * @return A 3-bit binary string of 0 and 1 representing the d-bits if valid target, else it will return null.
     */
    private String getDBits(String target) {
        switch (target) {
            case "D" -> {return "010";}
            case "A" -> {return "100";}
        }
        return null;
    }

    /**
     * Assigns the j-bits for each jump mnemonic, the j-bits will be defining the jump condition.
     * @param mnemonic A jump mnemonic that should either be JGT, JEQ, JGE, JLT, JNE or JLE.
     * @return A 3-bit binary string of '0' and '1' representing the j-bits if valid mnemonic, else it will return null.
     */
    private String getJumpBits(String mnemonic) {
        switch (mnemonic) {
            case "JGT" -> {return "001";}
            case "JEQ" -> {return "010";}
            case "JGE" -> {return "011";}
            case "JLT" -> {return "100";}
            case "JNE" -> {return "101";}
            case "JLE" -> {return "110";}
        }
        return null;
    }

    /**
     * Handles the instructions for the load register which can be either loading values into A or loading from a register to the memory.
     * @param operands A list storing the operands of the Ldr instructions.
     * @return A 16-bit binary string if the instruction is valid, else it will return null.
     */
    private String handleLdr(List<String> operands) {
        if (operands.size() != 2) return null;

        String target = operands.get(0).toUpperCase();
        String source = operands.get(1).toUpperCase();

        if (target.equals("A") && source.startsWith("$")) {
            String numStr = source.substring(1);
            int value;
            try {
                value = Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 32767) return null;
            return String.format("0%15s", Integer.toBinaryString(value)).replace(' ', '0');
        }

        if (!target.equals("A") && !target.equals("D")) return null;
        if (!(source.equals("A") || source.equals("D") || source.equals("(A)"))) return null;

        int aBit = source.equals("(A)") ? 1 : 0;
        String cBits = getCBits(source);
        if (cBits == null) return null;

        String dBits = getDBits(target);
        if (dBits == null) return null;

        String eBits = "000";
        return "111" + aBit + cBits + dBits + eBits;
    }

    /**
     * Handles storing the contents of a register A or D into the memory location addressed by A.
     * @param operands A list storing the operands of the Str instruction, which should either be (A), A or (A), D.
     * @return A 16-bit binary string if the instruction is valid, else it will return null.
     */
    private String handleStr(List<String> operands) {
        if (operands.size() != 2) return null;
        String location = operands.get(0).toUpperCase();
        String source = operands.get(1).toUpperCase();

        if (!location.equals("(A)")) return null;
        if (!(source.equals("A") || source.equals("D"))) return null;
        int aBit = source.equals("(A)") ? 1 : 0;

        String cBits = getCBits(source);
        if (cBits == null) return null;

        String dBits = "001";
        String eBits = "000";
        return "111" + aBit + cBits + dBits + eBits;
    }

    /**
     * Handles addition between D and A or D and (A) then stores the result in either A or D, depending on the target operand.
     * @param operands A list storing the operands of the add instruction which should either be A, D, A or D, D, (A).
     * @return A 16-bit binary string if the instruction is valid, else it will return null.
     */
    private String handleAdd(List<String> operands) {
        if (operands.size() != 3) return null;
        String target = operands.get(0).toUpperCase();
        String source1 = operands.get(1).toUpperCase();
        String source2 = operands.get(2).toUpperCase();

        if (!target.equals("A") && !target.equals("D")) return null;
        if (!source1.equals("D")) return null;
        if (!(source2.equals("A") || source2.equals("(A)"))) return null;

        int aBit = source2.equals("(A)") ? 1 : 0;
        String cBits = "000010";
        String dBits = getDBits(target);

        if (dBits == null) return null;
        String eBits = "000";

        return "111" + aBit + cBits + dBits + eBits;
    }

    /**
     * Handles subtracting A or (A) from D and stores the result in A or D, depending on the target operand.
     * @param operands A list storing the operands of the sub instruction which should either be D, D, A or A, D, (A).
     * @return A 16-bit binary string if the instruction is valid, else it will return null.
     */
    private String handleSub(List<String> operands) {
        if (operands.size() != 3) return null;
        String target = operands.get(0).toUpperCase();
        String source1 = operands.get(1).toUpperCase();
        String source2 = operands.get(2).toUpperCase();

        if (!target.equals("A") && !target.equals("D")) return null;
        if (!source1.equals("D")) return null;
        if (!(source2.equals("A") || source2.equals("(A)"))) return null;

        int aBit = source2.equals("(A)") ? 1 : 0;
        String cBits = "010011";

        String dBits = getDBits(target);
        if (dBits == null) return null;
        String eBits = "000";

        return "111" + aBit + cBits + dBits + eBits;
    }

    /**
     * Handles jump instructions with a source register/value of A, D, or (A) to determine if the jump should happen or not.
     * @param mnemonic The jump instruction mnemonic which should either be jmp, jgt, jeq, jge, jlt, jne or jle.
     * @param operands A list storing the operands for the jump instruction.
     * @return A 16-bit binary string if the instruction is valid, else it will return null.
     */
    private String handleJump(String mnemonic, List<String> operands) {
        mnemonic = mnemonic.toUpperCase();

        if (mnemonic.equals("JMP")) {
            return "1110101010000111";
        }

        if (operands.size() != 1) return null;

        String operand = operands.get(0).toUpperCase();
        if (!(operand.equals("A") || operand.equals("D") || operand.equals("(A)"))) {
            return null;
        }

        String jBits = getJumpBits(mnemonic);
        if (jBits == null) return null;

        String cBits = getCBits(operand);
        if (cBits == null) return null;

        int aBit = operand.equals("(A)") ? 1 : 0;
        String dBits = "000";

        return "111" + aBit + cBits + dBits + jBits;
    }
}
