/*
 * @(#) $Id$
 */
package org.apache.mina.examples.echoserver;

import java.net.InetSocketAddress;

import junit.framework.Assert;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.examples.echoserver.ssl.BogusSSLContextFactory;
import org.apache.mina.io.IoConnector;
import org.apache.mina.io.IoHandlerAdapter;
import org.apache.mina.io.IoSession;
import org.apache.mina.io.datagram.DatagramConnector;
import org.apache.mina.io.filter.SSLFilter;
import org.apache.mina.io.socket.SocketConnector;

/**
 * Tests echo server example.
 * 
 * @author Trustin Lee (trustin@apache.org)
 * @version $Rev$, $Date$
 */
public class ConnectorTest extends AbstractTest
{
    public void testTCP() throws Exception
    {
        IoConnector connector = new SocketConnector();
        connector.addFilter( Integer.MAX_VALUE, super.threadPoolFilter );
        testTCP0( connector );
    }

    /**
     * Client-side SSL doesn't work for now.
     */
    public void _testTCPWithSSL() throws Exception
    {
        // Add an SSL filter to acceptor
        SSLFilter acceptorSSLFilter = new SSLFilter( BogusSSLContextFactory.getInstance( true ) );
        acceptor.addFilter( Integer.MAX_VALUE - 1, acceptorSSLFilter );

        // Create a connector
        IoConnector connector = new SocketConnector();
        connector.addFilter( Integer.MAX_VALUE, super.threadPoolFilter );
        
        // Add an SSL filter to connector
        SSLFilter connectorSSLFilter = new SSLFilter( BogusSSLContextFactory.getInstance( false ) );
        connectorSSLFilter.setDebug( SSLFilter.Debug.ON );
        connector.addFilter( Integer.MAX_VALUE - 1, connectorSSLFilter );

        testTCP0( connector );
    }
    
    private void testTCP0( IoConnector connector ) throws Exception
    {
        EchoConnectorHandler handler = new EchoConnectorHandler();
        ByteBuffer readBuf = handler.readBuf;
        IoSession session = connector.connect( new InetSocketAddress( port ),
                                               handler );
        
        for( int i = 0; i < 10; i ++ )
        {
            ByteBuffer buf = ByteBuffer.allocate( 16 );
            buf.limit( 16 );
            fillWriteBuffer( buf, i );
            buf.flip();

            Object marker;
            if( ( i & 1 ) == 0 )
            {
                marker = new Integer( i );
            }
            else
            {
                marker = null;
            }

            session.write( buf, marker );

            // This will align message arrival order in UDP
            for( int j = 0; j < 30; j ++ )
            {
                if( readBuf.position() == ( i + 1 ) * 16 )
                {
                    break;
                }
                Thread.sleep( 10 );
            }
        }
        
        Thread.sleep( 300 );
        session.close();
        
        Assert.assertEquals( 160, readBuf.position() );
        readBuf.flip();
        
        ByteBuffer expectedBuf = ByteBuffer.allocate( 160 );
        for( int i = 0; i < 10; i ++ ) {
            expectedBuf.limit( ( i + 1 ) * 16 );
            fillWriteBuffer( expectedBuf, i );
        }
        expectedBuf.position( 0 );
        assertEquals(expectedBuf, readBuf);
    }

    public void testUDP() throws Exception
    {
        IoConnector connector = new DatagramConnector();
        connector.addFilter( Integer.MAX_VALUE, super.threadPoolFilter );
        testTCP0( connector );
    }
    
    private void fillWriteBuffer( ByteBuffer writeBuf, int i )
    {
        while( writeBuf.remaining() > 0 )
        {
            writeBuf.put( ( byte ) ( i ++ ) );
        }
    }

    public static void main( String[] args )
    {
        junit.textui.TestRunner.run( ConnectorTest.class );
    }
    
    private static class EchoConnectorHandler extends IoHandlerAdapter
    {
        private ByteBuffer readBuf = ByteBuffer.allocate( 1024 );
        private int counter = 0;

        public void dataRead( IoSession session, ByteBuffer buf )
        {
            readBuf.put( buf );
        }
        
        public void dataWritten( IoSession session, Object marker )
        {
            if( ( counter & 1 ) == 0 )
            {
                Assert.assertEquals( new Integer( counter ), marker );
            }
            else
            {
                Assert.assertNull( marker );
            }
            
            counter ++;
        }

        public void exceptionCaught( IoSession session, Throwable cause )
        {
            cause.printStackTrace();
        }
    }
}
