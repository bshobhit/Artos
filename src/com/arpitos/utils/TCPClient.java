package com.arpitos.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.arpitos.interfaces.Connectable;

public class TCPClient implements Connectable {

	String ip;
	int nPort;
	Socket clientSocket;
	BufferedReader inFromServer;
	DataOutputStream outToServer;
	Queue<byte[]> queue = new LinkedList<byte[]>();

	public TCPClient(String ip, int nPort) {
		this.ip = ip;
		this.nPort = nPort;
	}

	public void connect() throws Exception {

		System.out.println("Connecting on Port : " + nPort);

		clientSocket = new Socket(ip, nPort);
		if (clientSocket.isConnected()) {
			System.out.println("Connected to " + ip + ":" + nPort);
		}

		// Start Reading task in parallel thread
		readFromSocket();
	}

	public boolean isConnected() {
		return clientSocket.isConnected();
	}

	public void disconnect() throws Exception {
		clientSocket.close();
		System.out.println("Connection Closed");
	}

	public void sendData(String data) throws Exception {
		outToServer = new DataOutputStream(clientSocket.getOutputStream());
		outToServer.writeBytes(data);
	}

	@Override
	public void sendMsg(byte[] data) throws Exception {
		outToServer = new DataOutputStream(clientSocket.getOutputStream());
		outToServer.write(data);
	}

	@Override
	public byte[] getNextMsg() {
		if (hasNextMsg()) {
			return queue.poll();
		}
		return null;
	}

	@Override
	public boolean hasNextMsg() {
		if (queue.isEmpty()) {
			return false;
		}
		return true;
	}

	public byte[] getNextMSG(long Timeout, TimeUnit timeunit) throws Exception {
		boolean isTimeout = false;
		long startTime = System.nanoTime();
		long finishTime;
		long maxAllowedTime = TimeUnit.NANOSECONDS.convert(Timeout, timeunit);

		while (!isTimeout) {
			if (hasNextMsg()) {
				return queue.poll();
			}
			finishTime = System.nanoTime();
			if ((finishTime - startTime) > maxAllowedTime) {
				return null;
			}
			// Give system some time to do other things
			Thread.sleep(20);
		}
		return null;
	}

	private void readFromSocket() {
		final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);
		final Runnable clientTask = new Runnable() {
			@Override
			public void run() {
				try {
					clientProcessingPool.submit(new ServerTask(clientSocket, queue));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		Thread clientThread = new Thread(clientTask);
		clientThread.start();
	}

	public Socket getConnector() {
		return clientSocket;
	}

	public BufferedReader getInFromClient() {
		return inFromServer;
	}

	public DataOutputStream getOutToClient() {
		return outToServer;
	}

	public Queue<byte[]> getQueue() {
		return queue;
	}

	public void cleanQueue() {
		queue.clear();
	}

}

/**
 * Inner Class which acts as receiver thread for incoming data. All Data will be
 * added to the Queue
 * 
 * @author arpit
 *
 */
class ServerTask implements Runnable {
	private final Socket connector;
	int read = -1;
	byte[] buffer = new byte[4 * 1024]; // a read buffer of 4KiB
	byte[] readData;
	String redDataText;
	Queue<byte[]> queue;

	ServerTask(Socket connector, Queue<byte[]> queue) {
		this.connector = connector;
		this.queue = queue;
	}

	@Override
	public void run() {
		try {
			while ((read = connector.getInputStream().read(buffer)) > -1) {
				readData = new byte[read];
				System.arraycopy(buffer, 0, readData, 0, read);
				if (readData.length > 0) {
					queue.add(readData);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
