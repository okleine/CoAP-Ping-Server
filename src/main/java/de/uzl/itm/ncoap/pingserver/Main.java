package de.uzl.itm.ncoap.pingserver;

import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebservice;
import de.uniluebeck.itm.ncoap.communication.dispatching.server.NotFoundHandler;
import de.uniluebeck.itm.ncoap.message.*;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by olli on 25.05.15.
 */
public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    public static void configureDefaultLogging() throws Exception{
        System.out.println("Use default logging configuration, i.e. INFO level...\n");
        URL url = Main.class.getClassLoader().getResource("log4j.xml");
        System.out.println("Use config file " + url);
        DOMConfigurator.configure(url);
    }


    public static void main(String[] args) throws Exception {
        configureDefaultLogging();
        CoapServerApplication coapServer = new CoapServerApplication(NotFoundHandler.getDefault());
        coapServer.registerService(new PingService("/ping", coapServer.getExecutor()));
    }


    private static class PingService extends NotObservableWebservice<Void>{

        protected PingService(String servicePath, ScheduledExecutorService executor) {
            super(servicePath, null, 0, executor);
        }

        @Override
        public byte[] getEtag(long contentFormat) {
            return new byte[1];
        }

        @Override
        public void updateEtag(Void resourceStatus) {
            //nothing to do
        }

        @Override
        public void shutdown() {
            //nothing to do
        }

        @Override
        public void processCoapRequest(final SettableFuture<CoapResponse> responseFuture, CoapRequest coapRequest, final InetSocketAddress remoteEndpoint) throws Exception {
            if(!coapRequest.getMessageCodeName().equals(MessageCode.Name.GET)){
                CoapResponse coapResponse = new CoapResponse(coapRequest.getMessageTypeName(), MessageCode.Name.METHOD_NOT_ALLOWED_405);
                coapResponse.setContent("Only GET is supported!".getBytes(CoapMessage.CHARSET), ContentFormat.TEXT_PLAIN_UTF8);
                responseFuture.set(coapResponse);
                return;
            }

            String queryValue = coapRequest.getUriQueryParameterValue("delay");
            final long delay;
            if(queryValue != null){
                delay = Long.valueOf(queryValue);
            }
            else{
                delay = 0;
            }

            log.info("Request received from {} (requested delay: {})", remoteEndpoint, delay);

            this.getExecutor().schedule(new Runnable(){

                @Override
                public void run() {
                    CoapResponse coapResponse = new CoapResponse(MessageType.Name.NON, MessageCode.Name.CONTENT_205);
                    byte[] part1 = getSerializedResourceStatus(ContentFormat.TEXT_PLAIN_UTF8);
                    byte[] part2 = (" (delay: " + delay + " sec)\n  Client Socket: " + remoteEndpoint.toString()).getBytes(CoapMessage.CHARSET);
                    coapResponse.setContent(ChannelBuffers.wrappedBuffer(part1, part2), ContentFormat.TEXT_PLAIN_UTF8);
                    responseFuture.set(coapResponse);
                    log.info("Response sent to {} for delay: {})", remoteEndpoint, delay);
                }
            }, delay, TimeUnit.SECONDS);
        }

        @Override
        public byte[] getSerializedResourceStatus(long contentFormat) {
            return "PONG".getBytes(CoapMessage.CHARSET);
        }
    }
}
