/*
 * eID Applet Project.
 * Copyright (C) 2009 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package be.fedict.eid.applet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * eID Applet specific implementation of an SSL Socket Factory.
 * 
 * <p>
 * Makes sure that the SSL session doesn't change during eID Applet operations.
 * Gives us access to the SSL session identifier so we can implement secure
 * tunnel binding in our authentication protocol.
 * </p>
 * 
 * @author fcorneli
 * 
 */
public class AppletSSLSocketFactory extends SSLSocketFactory implements
		HandshakeCompletedListener {

	private final View view;

	private final SSLSocketFactory originalSslSocketFactory;

	private byte[] sslSessionId;

	public AppletSSLSocketFactory(View view,
			SSLSocketFactory originalSslSocketFactory) {
		this.view = view;
		this.originalSslSocketFactory = originalSslSocketFactory;
		this.view.addDetailMessage("original SSL socket factory: "
				+ originalSslSocketFactory.getClass().getName());
	}

	@Override
	public Socket createSocket(Socket s, String host, int port,
			boolean autoClose) throws IOException {
		Socket socket = this.originalSslSocketFactory.createSocket(s, host,
				port, autoClose);
		/*
		 * Important here not to try to access the SSL session identifier via
		 * getSession. This can cause problems when sitting behind an HTTP
		 * proxy. The only way to get access to the SSL session identifier is
		 * via the TLS handshake completed listener.
		 */
		installHandshakeCompletedListener(socket);
		return socket;
	}

	private void installHandshakeCompletedListener(Socket socket)
			throws IOException {
		SSLSocket sslSocket = (SSLSocket) socket;
		sslSocket.addHandshakeCompletedListener(this);
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return this.originalSslSocketFactory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return this.originalSslSocketFactory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException,
			UnknownHostException {
		Socket socket = this.originalSslSocketFactory.createSocket(host, port);
		installHandshakeCompletedListener(socket);
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		Socket socket = this.originalSslSocketFactory.createSocket(host, port);
		installHandshakeCompletedListener(socket);
		return socket;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost,
			int localPort) throws IOException, UnknownHostException {
		Socket socket = this.originalSslSocketFactory.createSocket(host, port,
				localHost, localPort);
		installHandshakeCompletedListener(socket);
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress host, int port,
			InetAddress localHost, int localPort) throws IOException {
		Socket socket = this.originalSslSocketFactory.createSocket(host, port,
				localHost, localPort);
		installHandshakeCompletedListener(socket);
		return socket;
	}

	/**
	 * Gives back the SSL session identifier.
	 * 
	 * @return the SSL session Id.
	 */
	public byte[] getSessionId() {
		if (null == this.sslSessionId) {
			throw new IllegalStateException("SSL session identifier unknown");
		}
		return this.sslSessionId;
	}

	@Override
	public Socket createSocket() throws IOException {
		Socket socket = this.originalSslSocketFactory.createSocket();
		installHandshakeCompletedListener(socket);
		return socket;
	}

	public void handshakeCompleted(HandshakeCompletedEvent event) {
		String cipherSuite = event.getCipherSuite();
		this.view.addDetailMessage("SSL handshake finish cipher suite: "
				+ cipherSuite);
		SSLSession sslSession = event.getSession();
		byte[] sslSessionId = sslSession.getId();
		if (null == this.sslSessionId) {
			this.sslSessionId = sslSessionId;
		} else {
			if (false == Arrays.equals(this.sslSessionId, sslSessionId)) {
				this.view
						.addDetailMessage("SSL handshake finish; session Id mismatch!");
			}
		}
	}

	public static SocketFactory getDefault() {
		SSLSocketFactory sslSocketFactory = HttpsURLConnection
				.getDefaultSSLSocketFactory();
		return sslSocketFactory;
	}

	/**
	 * Installs this socket factory within the JRE.
	 * 
	 * @param view
	 */
	public static void installSocketFactory(View view) {
		SSLSocketFactory sslSocketFactory = HttpsURLConnection
				.getDefaultSSLSocketFactory();
		if (sslSocketFactory instanceof AppletSSLSocketFactory) {
			// already installed
			return;
		}
		AppletSSLSocketFactory appletSslSocketFactory = new AppletSSLSocketFactory(
				view, sslSocketFactory);
		HttpsURLConnection.setDefaultSSLSocketFactory(appletSslSocketFactory);
	}

	/**
	 * Returns the actual SSL session identifier.
	 * 
	 * @return
	 */
	public static byte[] getActualSessionId() {
		SSLSocketFactory sslSocketFactory = HttpsURLConnection
				.getDefaultSSLSocketFactory();
		if (false == sslSocketFactory instanceof AppletSSLSocketFactory) {
			throw new SecurityException("wrong SSL socket factory");
		}
		AppletSSLSocketFactory appletSslSocketFactory = (AppletSSLSocketFactory) sslSocketFactory;
		byte[] sessionId = appletSslSocketFactory.getSessionId();
		return sessionId;
	}
}
