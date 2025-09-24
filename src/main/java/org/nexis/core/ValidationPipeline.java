/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.ArrayList;
import java.util.List;
import org.nexis.base.MessageValidator;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ValidationPipeline {
    private final List<MessageValidator> validators = new ArrayList<>();

    public void addValidator(MessageValidator validator) {
        validators.add(validator);
    }

    public void validate(NexusProtocol.NexusEnvelop envelop) {
        for (MessageValidator validator : validators) {
            if (validator.supports(envelop.getMessage())) {
                validator.validate(envelop);
            }
        }
    }
}

