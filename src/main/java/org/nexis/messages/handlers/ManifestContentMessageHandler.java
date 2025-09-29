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

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.math.BigInteger;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.internal.MessageHandler;
import org.nexis.store.Storage;
import org.nexis.utilities.ByteUtils;
import org.nexus.base.proto.NexusProtocol;

/**
 * this get manifest content class is going to cache the received manifest and
 * also forward it to the requesting node
 *
 * @author daviestobialex
 */
public class ManifestContentMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Storage manifestStore;

    public ManifestContentMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            Storage store) {
        this.builder = builder;
        this.params = params;
        this.manifestStore = store;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifestContent();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());

        ByteString cid = envelop.getMessage().getManifestContent().getCid();
        ByteString rawJson = envelop.getMessage().getManifestContent().getRaw();
        // validate CID
        try {
            // update manifest store
            manifestStore.put(new BigInteger(cid.toByteArray()), ByteUtils.compress(rawJson.toByteArray()));
        } catch (IOException ex) {
            throw new RuntimeException("failed to store received manifest");
        }

        // forward manifest content to request if current node is not the requesting node
        ctx.writeAndFlush(builder.build(null));
    }

}
