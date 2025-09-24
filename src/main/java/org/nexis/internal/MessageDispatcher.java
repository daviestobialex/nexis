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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.nexus.base.proto.NexusProtocol;

/**
 * Central coordinator that dispatches incoming
 * {@link NexusProtocol.NexusEnvelop} messages to the first registered
 * {@link MessageHandler} capable of handling them.
 *
 * <p>
 * <b>Responsibilities</b>
 * <ul>
 * <li>Maintain a registry of {@link MessageHandler} instances.</li>
 * <li>Route each incoming message to the appropriate handler.</li>
 * <li>Fail fast or fallback if no handler claims the message.</li>
 * </ul>
 *
 * <p>
 * <b>Design notes</b>
 * <ul>
 * <li>Thread-safe: uses {@link CopyOnWriteArrayList} to allow concurrent
 * registration and dispatch without external synchronization.</li>
 * <li>Open/Closed: new message types are supported by registering additional
 * handlers, without modifying this class.</li>
 * <li>Exception safety: handler exceptions are caught and logged, preventing a
 * single bad handler from crashing the dispatcher.</li>
 * </ul>
 *
 * Typical usage:
 * <pre>
 *   MessageDispatcher dispatcher = new MessageDispatcher();
 *   dispatcher.registerHandler(new HandshakeHandler());
 *   dispatcher.registerHandler(new PingHandler());
 *
 *   dispatcher.dispatch(envelope, ctx);
 * </pre>
 *
 * @author daviestobialex
 */
public final class MessageDispatcher {

    private final List<MessageHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * Register a new handler with the dispatcher. Handlers are evaluated in the
     * order they are registered.
     *
     * @param handler the handler to register
     */
    public void registerHandler(MessageHandler handler) {
        handlers.add(handler);
    }

    /**
     * Dispatch an incoming envelope to the first capable handler.
     *
     * @param envelop the incoming protocol message envelope
     * @param ctx the Netty channel context
     * @throws UnsupportedOperationException if no handler claims the message
     */
    public void dispatch(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        for (MessageHandler handler : handlers) {
            if (handler.canHandle(envelop.getMessage())) {
                handler.handle(envelop, ctx);
//                return; // stop at first capable handler
            }
        }
        throw new UnsupportedOperationException(
                "No handler found for message: " + envelop.getMessage().getPayloadCase());
    }
}
