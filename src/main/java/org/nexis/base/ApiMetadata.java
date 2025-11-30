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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.base;

import java.util.Objects;

/**
 * Metadata about a specific API endpoint.
 * Contains information needed to route, validate, and price API calls.
 *
 * @author daviestobialex
 */
public class ApiMetadata {
    private final String id;                    // API endpoint ID (e.g., "/transfer")
    private final String httpMethod;            // HTTP method (GET, POST, etc.)
    private final ApiType type;                 // READ_ONLY, TRANSACTIONAL, or PAYMENT_REQUIRED
    private final Coin cost;                    // Cost to invoke (Coin.ZERO for free APIs)
    private final String serviceProvider;       // Node address of the service provider
    private final String summary;               // Human-readable description
    
    public ApiMetadata(String id, String httpMethod, ApiType type, Coin cost, 
                       String serviceProvider, String summary) {
        this.id = Objects.requireNonNull(id, "API id cannot be null");
        this.httpMethod = Objects.requireNonNull(httpMethod, "HTTP method cannot be null").toUpperCase();
        this.type = Objects.requireNonNull(type, "API type cannot be null");
        this.cost = cost != null ? cost : Coin.ZERO;
        this.serviceProvider = serviceProvider;
        this.summary = summary != null ? summary : "";
    }
    
    /**
     * Get the API endpoint identifier (e.g., "/balance", "/transfer")
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    public String getHttpMethod() {
        return httpMethod;
    }
    
    /**
     * Get the API type (determines blockchain recording behavior)
     */
    public ApiType getType() {
        return type;
    }
    
    /**
     * Get the cost to invoke this API (Coin.ZERO for free APIs)
     */
    public Coin getCost() {
        return cost;
    }
    
    /**
     * Get the service provider node address
     */
    public String getServiceProvider() {
        return serviceProvider;
    }
    
    /**
     * Get a human-readable summary/description
     */
    public String getSummary() {
        return summary;
    }
    
    /**
     * Check if this API requires payment
     */
    public boolean requiresPayment() {
        return type == ApiType.PAYMENT_REQUIRED || (cost != null && cost.isPositive());
    }
    
    /**
     * Check if this API should be recorded on the blockchain
     */
    public boolean shouldRecordOnChain() {
        return type == ApiType.TRANSACTIONAL || type == ApiType.PAYMENT_REQUIRED;
    }
    
    /**
     * Get a composite key for this API (method + path)
     */
    public String getKey() {
        return httpMethod + " " + id;
    }
    
    @Override
    public String toString() {
        return String.format("ApiMetadata{id='%s', method='%s', type=%s, cost=%s, provider='%s'}",
                id, httpMethod, type, cost, serviceProvider);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiMetadata that = (ApiMetadata) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(httpMethod, that.httpMethod);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, httpMethod);
    }
}
