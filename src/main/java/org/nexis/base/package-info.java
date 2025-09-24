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
 * The {@code org.nexis.base} package defines the foundational abstractions of the
 * <b>Nexis protocol</b>. These types represent the core building blocks of the messaging,
 * identity, and network model, and are designed to be lightweight, stable, and
 * free of unnecessary dependencies.
 *
 * <h2>Design Criteria</h2>
 * <ul>
 *   <li>No dependencies on higher-level Nexis packages</li>
 *   <li>No API dependencies on external libraries beyond the Java core libraries</li>
 *   <li>Interfaces and classes here represent <b>protocol primitives</b> that higher-level
 *       modules can rely on without risk of circular dependencies</li>
 * </ul>
 *
 * <p>
 * The goal of {@code base} is to provide a <i>zero-dependency foundation</i> upon which
 * the rest of the Nexis framework can be layered. By isolating core types like
 * {@code NexusMessage}, {@code Network}, and related utilities, we enable a modular,
 * testable, and evolvable protocol stack.
 * </p>
 *
 * <h2>Future Direction</h2>
 * <p>
 * In future releases, the {@code base} package may be distributed as a separate
 * JAR/module (tentatively {@code nexis-base}) to enforce its independence and allow
 * downstream projects to reuse the protocol primitives without pulling in the
 * entire Nexis ecosystem.
 * </p>
 */
package org.nexis.base;
