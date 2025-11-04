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
package org.nexis.messages;

import org.nexis.core.AbstractNexusMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetManifestContentRequest extends AbstractNexusMessage {

    protected final NexusProtocol.GetManifestContent manifest;

    public GetManifestContentRequest(
            NexusNetworkConfiguration params,
            NexusProtocol.GetManifestContent manifest,
            byte[] nodeId) {
        super(params, nodeId);
        this.manifest = manifest;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setGetManifestContent(manifest) // wrap Manifest into NexusMessage
                .build();
    }
}
