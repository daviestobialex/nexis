/*
 * Copyright (c) 2025 Nexis Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
/**
 * The {@code org.nexis.core} package provides the core runtime functionality
 * for the Nexis P2P network.
 * <p>
 * This package contains classes and utilities that rely on external libraries
 * such as:
 * </p>
 * <ul>
 * <li><b>Netty:</b> for asynchronous TCP/IP networking, channel management, and
 * non-blocking I/O.</li>
 * <li><b>Google Protocol Buffers (Protobuf):</b> for binary serialization of
 * messages between nodes.</li>
 * </ul>
 *
 * <p>
 * Responsibilities of the core package include:
 * </p>
 * <ul>
 * <li>Establishing and managing peer-to-peer connections.</li>
 * <li>Handling message serialization/deserialization with Protobuf.</li>
 * <li>Implementing the messaging protocol including handshake, ping/pong, and
 * manifest exchange.</li>
 * <li>Dispatching messages to appropriate handlers in a SOLID-compliant and
 * modular fashion.</li>
 * <li>Performing cryptographic operations, including signature
 * generation/verification and payload checksums.</li>
 * <li>Providing abstractions for asynchronous and non-blocking message
 * processing.</li>
 * </ul>
 *
 * <p>
 * This package is critical for the runtime operation of the Nexis network. All
 * network-level operations, message routing, and low-level protocol enforcement
 * are implemented here. Higher-level modules such as {@code org.nexis.base}
 * provide type definitions, validators, and message abstractions used by this
 * package.
 * </p>
 *
 * <p>
 * Core design principles include:
 * </p>
 * <ul>
 * <li>Modularity: separation of transport, protocol, and message handling.</li>
 * <li>Asynchronous processing: non-blocking I/O to maximize scalability.</li>
 * <li>Security-first: every message undergoes checksum and signature
 * validation.</li>
 * <li>Extensibility: supports adding new message types, handlers, and protocol
 * rules without major refactoring.</li>
 * </ul>
 *
 * @see io.netty
 * @see com.google.protobuf
 */
package org.nexis.core;
