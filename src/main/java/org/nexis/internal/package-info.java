/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

/**
 * The {@code org.nexis.internal} package contains internal utilities and
 * low-level infrastructure components for the <b>Nexis protocol</b>.
 * These classes are <i>not</i> intended to form part of the public API
 * and may change without notice between releases.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li><b>Message dispatching:</b> e.g. {@code MessageDispatcher}, used to route
 *       envelopes to registered handlers.</li>
 *   <li><b>Network I/O producers:</b> e.g. {@code NioProducer}, providing
 *       abstractions for writing to Netty {@code Channel}s.</li>
 *   <li><b>Internal utilities:</b> helpers for byte manipulation, hashing,
 *       and protocol-specific encoding/decoding.</li>
 * </ul>
 *
 * <h2>Guidelines</h2>
 * <p>
 * Because this package exists to support core Nexis mechanics, its classes are
 * subject to change as the protocol evolves. External consumers should prefer
 * the stable abstractions in {@code org.nexis.base} unless explicitly working
 * on extending or maintaining the internals of the system.
 * </p>
 */
package org.nexis.internal;
