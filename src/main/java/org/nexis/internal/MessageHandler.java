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
package org.nexis.internal;

import io.netty.channel.ChannelHandlerContext;
import org.nexus.base.proto.NexusProtocol;

/**
 * Strategy interface for handling specific types of Nexus messages.
 *
 * @author daviestobialex
 */
public interface MessageHandler {

    /**
     * Whether this handler can process the given message.
     *
     * @param message the incoming protocol message
     * @return true if this handler supports it
     */
    boolean canHandle(NexusProtocol.NexusMessage message);

    /**
     * Process the message.
     *
     * @param envelop the full protocol envelope
     * @param ctx the Netty channel context
     */
    void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx);
}
