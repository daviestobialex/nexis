/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.ArrayList;
import java.util.List;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.base.Validator;
import org.nexis.exceptions.ProtocolException;

/**
 *
 * @author daviestobialex
 */
public class ValidationPipeline {
    private final List<Validator> validators = new ArrayList<>();

    public void addValidator(Validator validator) {
        validators.add(validator);
    }

    public void validate(NexusProtocol.NexusEnvelop envelop) throws ProtocolException{
        for (Validator validator : validators) {
            if (validator.supports(envelop.getMessage())) {
                validator.validate(envelop);
            }
        }
    }
}

