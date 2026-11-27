package com.classpulse.client.network;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.Principal;

/**
 * Encapsulates negotiated TLS session details from the client perspective.
 */
public class TlsSessionInfo {

    private final String protocol;
    private final String cipherSuite;
    private final String peerPrincipal;
    private final String localPrincipal;
    private final String peerHost;
    private final int peerPort;
    private final long creationTime;

    public TlsSessionInfo(String protocol, String cipherSuite, String peerPrincipal,
                          String localPrincipal, String peerHost, int peerPort, long creationTime) {
        this.protocol = protocol != null ? protocol : "UNKNOWN";
        this.cipherSuite = cipherSuite != null ? cipherSuite : "UNKNOWN";
        this.peerPrincipal = peerPrincipal != null ? peerPrincipal : "NONE";
        this.localPrincipal = localPrincipal != null ? localPrincipal : "NONE";
        this.peerHost = peerHost != null ? peerHost : "unknown";
        this.peerPort = peerPort;
        this.creationTime = creationTime;
    }

    public static TlsSessionInfo from(SSLSession session) {
        if (session == null) {
            return new TlsSessionInfo("NONE", "NONE", "NONE", "NONE", "unknown", -1, 0);
        }

        String protocol = session.getProtocol();
        String cipherSuite = session.getCipherSuite();
        String peerHost = session.getPeerHost();
        int peerPort = session.getPeerPort();
        long creationTime = session.getCreationTime();

        String peerPrincipal = "NONE";
        try {
            Principal peer = session.getPeerPrincipal();
            if (peer != null) {
                peerPrincipal = peer.getName();
            }
        } catch (SSLPeerUnverifiedException ignored) {
        }

        String localPrincipal = "NONE";
        Principal local = session.getLocalPrincipal();
        if (local != null) {
            localPrincipal = local.getName();
        }

        return new TlsSessionInfo(protocol, cipherSuite, peerPrincipal, localPrincipal, peerHost, peerPort, creationTime);
    }

    public String getProtocol() {
        return protocol;
    }

    public String getCipherSuite() {
        return cipherSuite;
    }

    public String getPeerPrincipal() {
        return peerPrincipal;
    }

    public String getLocalPrincipal() {
        return localPrincipal;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String toString() {
        return String.format("[TLS %s | Cipher: %s | Server: %s]",
                protocol, cipherSuite, peerPrincipal);
    }
}
