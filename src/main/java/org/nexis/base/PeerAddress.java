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
 * TODO: need to seeif network actually needs to be here based on the design but
 * I want to get the crypto part done first then I decide based on the
 * architecture I am going for
 *
 * @author daviestobialex
 */
public interface PeerAddress extends PublicNodeProperties {

    public String id();
}
