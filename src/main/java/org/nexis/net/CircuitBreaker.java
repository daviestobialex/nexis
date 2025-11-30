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
package org.nexis.net;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Circuit breaker implementation to prevent cascading failures when downstream services are unhealthy.
 * 
 * <p>The circuit breaker operates in three states:</p>
 * <ul>
 *   <li><b>CLOSED:</b> Normal operation, requests pass through. Failures are counted.</li>
 *   <li><b>OPEN:</b> Too many failures detected, all requests fail immediately without calling downstream.</li>
 *   <li><b>HALF_OPEN:</b> After timeout, allows limited requests to test if service has recovered.</li>
 * </ul>
 * 
 * <p><b>State Transitions:</b></p>
 * <ul>
 *   <li>CLOSED → OPEN: When failure count exceeds threshold</li>
 *   <li>OPEN → HALF_OPEN: After cooldown period expires</li>
 *   <li>HALF_OPEN → CLOSED: When a request succeeds</li>
 *   <li>HALF_OPEN → OPEN: When a request fails</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is thread-safe using atomic operations.</p>
 * 
 * @author daviestobialex
 */
public class CircuitBreaker {
    
    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());
    
    /**
     * Circuit breaker states
     */
    public enum State {
        /** Normal operation, requests pass through */
        CLOSED,
        /** Too many failures, rejecting all requests */
        OPEN,
        /** Testing if service has recovered */
        HALF_OPEN
    }
    
    private final String name;
    private final int failureThreshold;
    private final Duration cooldownPeriod;
    
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicReference<Instant> lastFailureTime;
    
    /**
     * Creates a new circuit breaker with default configuration.
     * 
     * @param name identifier for this circuit breaker (used in logging)
     */
    public CircuitBreaker(String name) {
        this(name, 5, Duration.ofSeconds(30));
    }
    
    /**
     * Creates a new circuit breaker with custom configuration.
     * 
     * @param name identifier for this circuit breaker (used in logging)
     * @param failureThreshold number of consecutive failures before opening circuit
     * @param cooldownPeriod duration to wait in OPEN state before transitioning to HALF_OPEN
     */
    public CircuitBreaker(String name, int failureThreshold, Duration cooldownPeriod) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownPeriod = cooldownPeriod;
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicReference<>();
    }
    
    /**
     * Checks if the circuit is currently allowing requests.
     * 
     * <p>If the circuit is OPEN and the cooldown period has elapsed, it transitions to HALF_OPEN
     * to allow a test request.</p>
     * 
     * @return {@code true} if requests can proceed, {@code false} if circuit is open
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        if (currentState == State.CLOSED) {
            return true;
        }
        
        if (currentState == State.HALF_OPEN) {
            return true;
        }
        
        // OPEN state - check if cooldown period has elapsed
        Instant lastFailure = lastFailureTime.get();
        if (lastFailure != null && Duration.between(lastFailure, Instant.now()).compareTo(cooldownPeriod) >= 0) {
            // Transition to HALF_OPEN to test recovery
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                LOGGER.info(String.format("Circuit breaker '%s' transitioning to HALF_OPEN", name));
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Records a successful request.
     * 
     * <p>Resets failure count and closes the circuit if it was HALF_OPEN.</p>
     */
    public void recordSuccess() {
        failureCount.set(0);
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                LOGGER.info(String.format("Circuit breaker '%s' recovered, transitioning to CLOSED", name));
            }
        }
    }
    
    /**
     * Records a failed request.
     * 
     * <p>Increments failure count. If threshold is exceeded, opens the circuit.
     * If already HALF_OPEN, immediately reopens the circuit.</p>
     */
    public void recordFailure() {
        lastFailureTime.set(Instant.now());
        int failures = failureCount.incrementAndGet();
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // Failed during recovery test - reopen circuit
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                LOGGER.warning(String.format("Circuit breaker '%s' failed recovery test, reopening circuit", name));
            }
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            // Too many failures - open circuit
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                LOGGER.warning(String.format(
                    "Circuit breaker '%s' opened after %d failures (threshold: %d)", 
                    name, failures, failureThreshold));
            }
        }
    }
    
    /**
     * Gets the current state of the circuit breaker.
     * 
     * @return current state (CLOSED, OPEN, or HALF_OPEN)
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Gets the current failure count.
     * 
     * @return number of consecutive failures recorded
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Manually resets the circuit breaker to CLOSED state.
     * 
     * <p>Clears failure count and resets state. Useful for administrative intervention
     * or testing scenarios.</p>
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        lastFailureTime.set(null);
        LOGGER.info(String.format("Circuit breaker '%s' manually reset", name));
    }
}
