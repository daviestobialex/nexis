/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class FunctionCallResponseMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(FunctionCallMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Manifest manifest;
    private final PeerRegistry registery;

    public FunctionCallResponseMessageHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = PeerRegistry.getInstance();
    }

    // for test
    public FunctionCallResponseMessageHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest,
            PeerRegistry registery) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = registery;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        // at this stage this is the response to the function call hence it should write its own request 
        // and response to its own block chain which will be verified as well, hence if multiple peers interacting 
        // on operations, write chains that are first broadcasted verified based compuatting the same hash with 
        // the same network data  and matching prev hash or header hashes that match this then
        // forms the chains whihc are then written to the block chain
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
