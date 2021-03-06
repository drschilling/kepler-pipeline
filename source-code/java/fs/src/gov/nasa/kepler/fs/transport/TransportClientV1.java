/*
 * Copyright 2017 United States Government as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All Rights Reserved.
 * 
 * This file is available under the terms of the NASA Open Source Agreement
 * (NOSA). You should have received a copy of this agreement with the
 * Kepler source code; see the file NASA-OPEN-SOURCE-AGREEMENT.doc.
 * 
 * No Warranty: THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY
 * WARRANTY OF ANY KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY,
 * INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE
 * WILL CONFORM TO SPECIFICATIONS, ANY IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR FREEDOM FROM
 * INFRINGEMENT, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL BE ERROR
 * FREE, OR ANY WARRANTY THAT DOCUMENTATION, IF PROVIDED, WILL CONFORM
 * TO THE SUBJECT SOFTWARE. THIS AGREEMENT DOES NOT, IN ANY MANNER,
 * CONSTITUTE AN ENDORSEMENT BY GOVERNMENT AGENCY OR ANY PRIOR RECIPIENT
 * OF ANY RESULTS, RESULTING DESIGNS, HARDWARE, SOFTWARE PRODUCTS OR ANY
 * OTHER APPLICATIONS RESULTING FROM USE OF THE SUBJECT SOFTWARE.
 * FURTHER, GOVERNMENT AGENCY DISCLAIMS ALL WARRANTIES AND LIABILITIES
 * REGARDING THIRD-PARTY SOFTWARE, IF PRESENT IN THE ORIGINAL SOFTWARE,
 * AND DISTRIBUTES IT "AS IS."
 * 
 * Waiver and Indemnity: RECIPIENT AGREES TO WAIVE ANY AND ALL CLAIMS
 * AGAINST THE UNITED STATES GOVERNMENT, ITS CONTRACTORS AND
 * SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT. IF RECIPIENT'S USE OF
 * THE SUBJECT SOFTWARE RESULTS IN ANY LIABILITIES, DEMANDS, DAMAGES,
 * EXPENSES OR LOSSES ARISING FROM SUCH USE, INCLUDING ANY DAMAGES FROM
 * PRODUCTS BASED ON, OR RESULTING FROM, RECIPIENT'S USE OF THE SUBJECT
 * SOFTWARE, RECIPIENT SHALL INDEMNIFY AND HOLD HARMLESS THE UNITED
 * STATES GOVERNMENT, ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY
 * PRIOR RECIPIENT, TO THE EXTENT PERMITTED BY LAW. RECIPIENT'S SOLE
 * REMEDY FOR ANY SUCH MATTER SHALL BE THE IMMEDIATE, UNILATERAL
 * TERMINATION OF THIS AGREEMENT.
 */

package gov.nasa.kepler.fs.transport;

import static gov.nasa.kepler.fs.transport.TransportConstantsV1.*;
import static gov.nasa.kepler.fs.FileStoreConstants.*;

import gov.nasa.kepler.fs.api.FileStoreException;
import gov.nasa.kepler.hibernate.dbservice.ConfigurationServiceFactory;
import gov.nasa.spiffy.common.io.FileUtil;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Random;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transport layer client side.  This class is not MT safe.  Don't use this
 * class for any kind of new implementations.
 * 
 * @author Sean McCauliff
 *
 */
class TransportClientV1 extends TransportV1 implements TransportClient {

    private static final Log log = LogFactory.getLog(TransportClientV2.class);
    
    private final SocketAddress serverAddr;
    private SocketChannel socketChannel;
    private static final int CONNECT_TIMEOUT_SECS = 8;
    private static final int MAX_OPEN_TRIES = 5;
    private static final Random randomRetryTime = new Random();
    private static final int MAX_RETRY_WAIT_MS = 1000*2;
    private int serverProtocolVersion = -1;
    private ByteOrder serverByteOrder;
    
    public TransportClientV1(SocketAddress serverAddr) {
        this.serverAddr = serverAddr;
    }
    
    /* (non-Javadoc)
     * @see gov.nasa.kepler.fs.transport.TransportClient#close()
     */
    @Override
    public void close() throws IOException {
        try {
            if (socketChannel != null) {
                socketChannel.close();
            }
        } finally {
            socketChannel = null;
            inputStream = null;
            outputStream = null;
        }
    }
    
    /* (non-Javadoc)
     * @see gov.nasa.kepler.fs.transport.TransportClient#initConnection()
     */
    @Override
    public void initConnection() throws IOException {
 
        int tries = 0;
        boolean ok = false;
        if (socketChannel == null || !socketChannel.isConnected() || !socketChannel.isOpen()) {
            while (true) {
                try {
                    socketChannel = SocketChannel.open();
                    inputStream = null;
                    outputStream = null;

                    Socket socket = socketChannel.socket();
                    // socket.setKeepAlive(true);
                    // socket.setReuseAddress(true);
                    socket.setReceiveBufferSize(RECV_BUF_SIZE);
                    socket.setSendBufferSize(SEND_BUF_SIZE);
                    socket.setSoLinger(false, 0);
                    socket.setSoTimeout(CONNECT_TIMEOUT_SECS * 1000);
                    socket.setTcpNoDelay(true);
                    socketChannel.configureBlocking(true);

                    socketChannel.connect(serverAddr);
                    
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    
                    doAdmit();
                    
                    doVersionCheck(socketChannel);
                    
                    ok = true;
                    break;
                } catch (SocketTimeoutException stex) {
                    if (++tries >= MAX_OPEN_TRIES) {
                        throw stex;
                    }
                    try {
                        Thread.sleep(randomRetryTime.nextInt(MAX_RETRY_WAIT_MS));
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException("While waiting to reconnect.");
                    }
                    //continue;
                } finally {
                    if (!ok && socketChannel != null && socketChannel.isOpen()) {
                        FileUtil.close(socketChannel);
                    }
                }
            }
        }
        
        
        if (inputStream == null) {
            inputStream = new TInV1(socketChannel.socket().getInputStream(), this);
        }
        
        if (outputStream == null) {
            outputStream = new TOutV1(socketChannel.socket().getOutputStream(), this);
        }
        
    }

    private void doAdmit() throws IOException {
        ByteBuffer admitMessage = ByteBuffer.allocate(1);
        socketChannel.read(admitMessage);
        admitMessage.position(0);
        byte admitByte = admitMessage.get();
        switch (admitByte) {
            case ADMIT_YES:
                break;
            case ADMIT_NO:
                FileUtil.close(socketChannel);
                throw new FileStoreServerTooManyClientsException();
            default:
                throw new FileStoreException("Bad admit byte " + admitByte);
        }
    }
    
    private void doVersionCheck(SocketChannel channel) throws IOException {
        Configuration config = ConfigurationServiceFactory.getInstance();
        if (!config.getBoolean(FS_PROTOCOL_VERSION_CHECK_PROP, 
                FS_PROTOCOL_VERSION_CHECK_DEFAULT)) {
            log.info("Skipping protocol version check.");
            return;
        }
        
        ByteBuffer versionBuf = versionBuffer();
        channel.write(versionBuf);
        versionBuf.position(0);
        channel.read(versionBuf);
        versionBuf.position(0);
        serverByteOrder = versionBuf.get() == BIG_ENDIAN_BYTE ? 
            ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        serverProtocolVersion = versionBuf.getInt();
        
        if (serverByteOrder != ByteOrder.nativeOrder()) {
            log.warn("File store server byte order " +
                    "and my byte order do not match.");
        }
        if (serverProtocolVersion != CURRENT_PROTOCOL_VERSION) {
            log.warn("File store server protocol version is \"" + 
                        serverProtocolVersion + 
                        " but my protocol version is " + 
                        CURRENT_PROTOCOL_VERSION + ".");
        }
    }
    
    @Override
    protected SocketChannel socketChannel() throws IOException {
        initConnection();
        return socketChannel;
    }
    
    /**
     * This implementation does nothing.
     */
    @Override
    public void startMethod() {
        
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void setTimeOut(int seconds) {
        
    }

    @Override
    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }
}
