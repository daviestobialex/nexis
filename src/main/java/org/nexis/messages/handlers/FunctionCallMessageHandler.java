/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.net.HttpClientExecutor;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class FunctionCallMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(FunctionCallMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Manifest manifest;
    private final PeerRegistry registery;

    public FunctionCallMessageHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = PeerRegistry.getInstance();
    }

    // for test
    public FunctionCallMessageHandler(
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
        return message.hasFunctionCall();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        // TODO: manifest is used to initiate calls to down stream APIs
        Manifest.ApiClientContext context = manifest.getContext();
        HttpClientExecutor clientExecutor = manifest.getClientExecutor();
        // genertae block out of this interaction or data set
    }

}
