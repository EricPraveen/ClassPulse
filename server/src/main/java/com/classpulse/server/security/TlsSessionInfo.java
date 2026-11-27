package com.classpulse.server.security;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.Principal;

/**
 * Encapsulates negotiated TLS session details for logging, verification, and diagnostics.
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

    /**
     * Extracts session metadata from an established {@link SSLSession}.
     *
     * @param session the active SSL session
     * @return populated {@link TlsSessionInfo}
     */
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
            Principal principal = session.getPeerPrincipal();
            if (principal != null) {
                peerPrincipal = principal.getName();
            }
        } catch (SSLPeerUnverifiedException ignored) {
            // Client certificate not presented or verified (standard for one-way TLS)
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
        return String.format("[TLS %s | Cipher: %s | Peer: %s | Local: %s]",
                protocol, cipherSuite, peerPrincipal, localPrincipal);
    }
}
