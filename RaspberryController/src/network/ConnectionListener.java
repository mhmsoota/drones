package network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import utils.NetworkUtils;
import commoninterfaceimpl.RealAquaticDroneCI;

public class ConnectionListener extends Thread {

	private static final int DEFAULT_PORT = 10101;
	protected ArrayList<ConnectionHandler> connections = new ArrayList<>();
	protected RealAquaticDroneCI drone;
	protected int port;
	protected ServerSocket serverSocket = null;

	public ConnectionListener(RealAquaticDroneCI controller) throws IOException {
		this(controller, DEFAULT_PORT);
	}

	public ConnectionListener(RealAquaticDroneCI drone, int port)
			throws IOException {
		this.drone = drone;
		this.port = port;

		serverSocket = new ServerSocket(port);
	}

	@Override
	public void run() {

		try {
			System.out
					.println("[CONNECTION HANDLER] Connection Handler Initialized on "
							+ NetworkUtils.getAddress("wlan0")
							+ ":"
							+ port);
			System.out
					.println("[CONNECTION HANDLER] Waiting for connection requests!");
			while (true) {
				Socket socket = serverSocket.accept();
				createHandler(socket);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				serverSocket.close();
			} catch (IOException e) {
				System.out
						.println("[CONNECTION HANDLER] Unable to close server socket.... there was an open socket?");
			}
		}
	}

	protected void addConnection(ConnectionHandler conn) {
		connections.add(conn);
	}

	protected void createHandler(Socket s) {
		ConnectionHandler conn = new ConnectionHandler(s, drone, this);
		addConnection(conn);
		conn.start();
	}

	public void closeConnections() {
		if (!connections.isEmpty()) {
			System.out.println("[CONNECTION HANDLER] Closing Connections!");
			for (ConnectionHandler conn : connections) {
				if (!conn.getSocket().isClosed())
					conn.closeConnectionWthoutDiscardConnListener();
			}
		}
	}

	public synchronized void removeConnection(ConnectionHandler conn) {
		connections.remove(conn);
	}

	public ArrayList<ConnectionHandler> getConnections() {
		return connections;
	}
}
