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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerAddress;
import org.nexis.base.PeerConnection;
import org.nexis.base.SignedManifest;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.store.Storage;
import org.nexis.utilities.ByteUtils;
import org.nexis.utilities.HexFormat;
import org.nexus.base.proto.NexusProtocol;

/**
 * this get manifest content class is going to cache the received manifest and
 * also forward it to the requesting node
 *
 * @author daviestobialex
 */
public class ManifestContentMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestContentMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Storage manifestStore;
    private final Manifest manifest;

    public ManifestContentMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifestStore = ManifestRegistry.getInstance().getStore();
        this.manifest = manifest;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifestContent();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {

        ByteString cid = envelop.getMessage().getManifestContent().getCid();
        ByteString rawJson = envelop.getMessage().getManifestContent().getRaw();

        ManifestRegistry.getInstance().complete(cid.toString(), rawJson.toString());

        try {
            // update manifest store
            manifestStore.put(new BigInteger(cid.toByteArray()), ByteUtils.compress(rawJson.toByteArray()));
        } catch (IOException ex) {
            throw new RuntimeException("failed to store received manifest");
        }

        try {
            // validate cid is same as node
            SignedManifest signedManifest = new SignedManifest(manifest, builder.getNode());
            String hexedCid = HexFormat.bytesToHex(cid.toByteArray());
            // TODO: validate signers or approvers of the manifest and ensure it traces back to the genesis manifest or is part of the markel chain via validation
            
            // forward manifest content to request if current node is not the requesting node
            if (!signedManifest.getHexSignature().equalsIgnoreCase(hexedCid)) {
                // check if cid is present in manifest, forward to peer directly or gossip to all active peers
                PeerAddress peerById = PeerRegistry.getInstance().getPeerById(cid.toByteArray());
                if (peerById == null) {
                    PeerRegistry.getInstance().getActivePeers()
                            .forEach(peerConnection -> peerConnection.channel().writeAndFlush(envelop));
                } else {
                    PeerConnection peerConnection = PeerRegistry.getInstance().getPeerByAddress(peerById.id());// consider returning null safe check Optional
                    if (peerConnection != null) {
                        peerConnection.channel().writeAndFlush(envelop);
                    }
                }
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            Logger.getLogger(ChallangeResponseHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
