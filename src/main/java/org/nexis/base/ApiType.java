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
package org.nexis.base;

/**
 * Enum representing the type of API operation. Used to determine how API calls
 * should be processed and whether they should be recorded on the blockchain.
 *
 * @author daviestobialex
 */
public enum ApiType {
    /**
     * Read-only operations (typically GET requests) that don't modify state.
     * These are NOT written to the blockchain. Examples: /balance, /status,
     * /users/{id}
     */
    READ_ONLY,
    /**
     * Operations that modify state or represent business transactions, but they
     * are not written to the block chain
     *
     * Examples: POST /transfer, PUT /profile, DELETE /resource
     */
    NON_TRANSACTIONAL,
    /**
     * Operations that modify state or represent business transactions. These
     * ARE written to the blockchain as proof-of-service. this would be
     * indicated by the property tag, that the request requires performs a
     * service and then charges the caller for that service
     */
    TRANSACTIONAL,
    /**
     * Operations that require explicit payment/fee. These ARE written to the
     * blockchain with fee splits to governance nodes. Examples: Premium API
     * access, paid services
     */
    PAYMENT_REQUIRED
}
