/*
 * Copyright (c) 2025 Nexis Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This package provides core validation abstractions for Nexus protocol messages.
 * Validators are responsible for enforcing message integrity, authenticity,
 * and protocol-specific rules before further processing.
 */
package org.nexis.base;

import org.nexus.base.proto.NexusProtocol;


/**
 * {@code Validator} defines a contract for objects that can perform validation
 * on messages within the Nexis peer-to-peer network.
 * <p>
 * Validators ensure that incoming messages are well-formed, adhere to protocol
 * rules, and pass security checks such as checksum verification, signature
 * validation, or other domain-specific rules.
 * </p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * Validator validator = new ChecksumAndSignatureValidator();
 * if (validator.supports(envelop.getMessage())) {
 *     validator.validate(envelop);
 * }
 * }</pre>
 *
 * <p>Implementations may support:</p>
 * <ul>
 *     <li>Checksum verification for payload integrity</li>
 *     <li>Signature validation for authentication</li>
 *     <li>Protocol-specific constraints (e.g., manifest schema validation)</li>
 * </ul>
 *
 * <p><b>Suggested alternative names:</b></p>
 * <ul>
 *     <li>{@code MessageValidator} – emphasizes that the validator acts on protocol messages.</li>
 *     <li>{@code EnvelopValidator} – highlights that the entire NexusEnvelop is validated.</li>
 *     <li>{@code SecurityValidator} – appropriate if validators primarily enforce cryptographic checks.</li>
 * </ul>
 * 
 * @author
 *   daviestobialex
 */
public interface Validator {

    /**
     * Determines whether this validator supports the given message type.
     *
     * @param message the NexusMessage to check
     * @return true if this validator can validate the message; false otherwise
     */
    boolean supports(NexusProtocol.NexusMessage message);

    /**
     * Validates the given message envelop.
     * <p>
     * Throws a {@link SecurityException} if the envelop fails validation checks.
     * </p>
     *
     * @param envelop the NexusEnvelop to validate
     * @throws SecurityException if the validation fails
     */
    void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException;
}
