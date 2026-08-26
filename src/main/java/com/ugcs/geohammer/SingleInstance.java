package com.ugcs.geohammer;

import com.ugcs.geohammer.util.FilePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public final class SingleInstance {

	private static final Logger log = LoggerFactory.getLogger(SingleInstance.class);

	private static final Path SETTINGS_PATH = Path.of(System.getProperty("user.home"), ".geohammer");

	private static final Path LOCK_PATH = SETTINGS_PATH.resolve("instance.lock");

	private static final Path PORT_PATH = SETTINGS_PATH.resolve("instance.port");

	private static final String ACK = "geohammer";

	private static final int LISTEN_BACKLOG = 8;

	private static final int MAX_ACCEPT_FAILURES = 5;

	private static final int MAX_REQUEST_PATHS = 256;

	private static final int CONNECT_TIMEOUT_MS = 2000;

	private static final int READ_TIMEOUT_MS = 2000;

	private static final int PORT_WAIT_MS = 2000;

	private static final int PORT_RETRY_DELAY_MS = 50;

	private static FileChannel lockChannel;

	private static FileLock lock;

	private SingleInstance() {
	}

	public static boolean acquire() {
		FileChannel channel = null;
		try {
			Files.createDirectories(SETTINGS_PATH);
			channel = FileChannel.open(LOCK_PATH,
					StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
			FileLock fileLock = channel.tryLock();
			if (fileLock == null) {
				channel.close();
				return false;
			}
			startListener();
			lockChannel = channel;
			lock = fileLock;
			Runtime.getRuntime().addShutdownHook(new Thread(SingleInstance::release, "instance-lock-release"));
			return true;
		} catch (IOException | OverlappingFileLockException e) {
			log.warn("Cannot acquire an instance lock", e);
			closeQuietly(channel);
			return false;
		}
	}

	private static void startListener() throws IOException {
		int port = listenOnLoopback(FileOpenQueue::submit);
		Files.writeString(PORT_PATH, Integer.toString(port), StandardCharsets.UTF_8);
	}

	static int listenOnLoopback(Consumer<List<File>> openFiles) throws IOException {
		ServerSocket server = new ServerSocket(0, LISTEN_BACKLOG, InetAddress.getLoopbackAddress());

		Thread listener = new Thread(() -> listen(server, openFiles), "instance-listener");
		listener.setDaemon(true);
		listener.start();

		return server.getLocalPort();
	}

	private static void listen(ServerSocket server, Consumer<List<File>> openFiles) {
		int acceptFailures = 0;
		while (!server.isClosed() && acceptFailures < MAX_ACCEPT_FAILURES) {
			Socket accepted;
			try {
				accepted = server.accept();
			} catch (IOException e) {
				acceptFailures++;
				log.warn("Cannot accept a connection from another instance", e);
				continue;
			}
			acceptFailures = 0;
			try (Socket socket = accepted) {
				socket.setSoTimeout(READ_TIMEOUT_MS);
				receiveFiles(socket, openFiles);
			} catch (IOException | RuntimeException e) {
				log.warn("Cannot handle a request from another instance", e);
			}
		}
		log.warn("Stopped listening for other instances");
		closeQuietly(server);
		removePortFile();
	}

	private static void receiveFiles(Socket socket, Consumer<List<File>> openFiles) throws IOException {
		List<File> files = FilePaths.existingFiles(readPaths(socket));
		log.info("Received {} file(s) from another instance", files.size());
		openFiles.accept(files);

		sendConfirmation(socket);
	}

	private static List<String> readPaths(Socket socket) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		List<String> paths = new ArrayList<>();
		String line;
		while (paths.size() < MAX_REQUEST_PATHS && (line = reader.readLine()) != null) {
			if (!line.isBlank()) {
				paths.add(line);
			}
		}
		return paths;
	}

	private static void sendConfirmation(Socket socket) throws IOException {
		Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
		writer.write(ACK);
		writer.write('\n');
		writer.flush();
	}

	public static boolean forwardFiles(String[] args) {
		int port = readPort();
		if (port <= 0) {
			return false;
		}
		return sendFilesTo(port, args);
	}

	static boolean sendFilesTo(int port, String[] args) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
			socket.setSoTimeout(READ_TIMEOUT_MS);

			Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
			for (String arg : args) {
				writer.write(new File(arg).getAbsolutePath());
				writer.write('\n');
			}
			writer.flush();
			socket.shutdownOutput();

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			if (ACK.equals(reader.readLine())) {
				return true;
			}
			log.warn("Running instance did not accept files");
			return false;
		} catch (IOException e) {
			log.warn("Cannot pass files to the running instance", e);
			return false;
		}
	}

	private static int readPort() {
		long deadline = System.currentTimeMillis() + PORT_WAIT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (Files.exists(PORT_PATH)) {
				try {
					String value = Files.readString(PORT_PATH, StandardCharsets.UTF_8).trim();
					if (!value.isEmpty()) {
						return Integer.parseInt(value);
					}
				} catch (NumberFormatException e) {
					log.warn("Invalid port of the running instance", e);
					return -1;
				} catch (IOException e) {
					log.debug("Cannot read a port of the running instance", e);
				}
			}
			try {
				Thread.sleep(PORT_RETRY_DELAY_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return -1;
			}
		}
		return -1;
	}

	private static void release() {
		removePortFile();
		try {
			if (lock != null) {
				lock.release();
			}
		} catch (IOException e) {
			log.warn("Cannot release the instance lock", e);
		}
		closeQuietly(lockChannel);
	}

	private static void removePortFile() {
		try {
			Files.deleteIfExists(PORT_PATH);
		} catch (IOException e) {
			log.warn("Cannot remove the instance port file", e);
		}
	}

	private static void closeQuietly(AutoCloseable resource) {
		if (resource == null) {
			return;
		}
		try {
			resource.close();
		} catch (Exception e) {
			log.warn("Cannot close a resource", e);
		}
	}
}
