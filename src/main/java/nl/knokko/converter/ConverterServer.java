package nl.knokko.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ConverterServer {

    private static void propagate(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        while (true) {
            int numReadBytes = input.read(buffer);
            if (numReadBytes == -1) return;
            output.write(buffer, 0, numReadBytes);
            output.flush();
        }
    }

    private static void convert(byte[] buffer, File converterDirectory) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(converterDirectory);
        builder.command(
                "./converter.sh", "pack.zip", "-w", "false", "-m", "null",
                "-a", "entity_alphatest_one_sided", "-b", "alpha_test", "-f", "null"
        );
        Process process = builder.start();
        
        int result;
        try {
            result = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (result != 0) {
            UUID id = UUID.randomUUID();
            File failures = new File(converterDirectory + "/failures");
            if (!failures.mkdirs()) throw new IOException("Failed to create failures directory");

            try (OutputStream output = Files.newOutputStream(new File(failures + "/out-" + id + ".txt").toPath())) {
                propagate(process.getInputStream(), output, buffer);
            }
            try (OutputStream output = Files.newOutputStream(new File(failures + "/err-" + id + ".txt").toPath())) {
                propagate(process.getErrorStream(), output, buffer);
            }
            throw new IOException("conversion failed: " + id);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) System.out.println("Failed to delete " + file);
    }

    private static void startServerThread(File converterScript) {
        Thread mainThread = Thread.currentThread();
        File converterDirectory = converterScript.getAbsoluteFile().getParentFile();
        File targetDirectory = new File(converterDirectory + "/target");
        File packFile = new File(converterDirectory + "/pack.zip");
        File targetFile = new File(converterDirectory + "/target/packaged/geyser_resources.mcpack");

        new Thread(() -> {
            byte[] buffer = new byte[100_000];
            try (ServerSocket socket = new ServerSocket(21102)) {
                socket.setSoTimeout(1000);
                while (mainThread.isAlive()) {
                    try (Socket clientSocket = socket.accept()) {
                        System.out.println("Accepted client socket");

                        ZipInputStream input = new ZipInputStream(clientSocket.getInputStream());
                        ZipOutputStream fileOutput = new ZipOutputStream(Files.newOutputStream(packFile.toPath()));

                        ZipEntry entry = input.getNextEntry();
                        while (entry != null) {
                            fileOutput.putNextEntry(new ZipEntry(entry.getName()));
                            propagate(input, fileOutput, buffer);
                            entry = input.getNextEntry();
                        }

                        fileOutput.close();

                        deleteRecursively(targetDirectory);
                        convert(buffer, converterDirectory);

                        ZipInputStream fileInput = new ZipInputStream(Files.newInputStream(targetFile.toPath()));
                        ZipOutputStream output = new ZipOutputStream(clientSocket.getOutputStream());

                        entry = fileInput.getNextEntry();
                        while (entry != null) {
                            output.putNextEntry(new ZipEntry(entry.getName()));
                            propagate(fileInput, output, buffer);
                            entry = fileInput.getNextEntry();
                        }

                        output.finish();
                        System.out.println("Finished handling client");
                    } catch (SocketTimeoutException timeout) {
                        // Continue with the next iteration
                    } catch (IOException clientFailed) {
                        System.out.println("A client failed: " + clientFailed.getMessage());
                    }
                }
            } catch (IOException serverFailed) {
                throw new RuntimeException(serverFailed);
            }
        }).start();
    }

    public static void main(String[] args) {
        File converterScript;
        if (args.length == 0) converterScript = new File("./converter.sh");
        else converterScript = new File(args[0]);

        if (!converterScript.isFile()) {
            System.out.println("Can't find converter.sh at " + converterScript.getAbsolutePath());
            return;
        }

        startServerThread(converterScript);

        Scanner console = new Scanner(System.in);
        while (console.hasNextLine()) {
            String command = console.nextLine();
            if (command.equals("stop") || command.equals("exit")) {
                return;
            } else {
                System.out.println("Unknown command. Use 'stop' or 'exit' to stop.");
            }
        }
    }
}
