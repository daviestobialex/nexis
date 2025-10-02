/*
 * Copyright 2013 Google Inc.
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
 * A generic handler which is used in {@link NioServer}, {@link NioClient} and
 * {@link BlockingClient} to handle incoming data streams.
 *
 * Used to be called StreamParser.
 */
public interface StreamConnection {

    /**
     * Called when the connection socket is closed
     */
    void connectionClosed();

    /**
     * Called when the connection socket is first opened
     *
     * @return
     */
    StreamConnection connectionOpened();
    
    StreamConnection connectionOpened(String host, int port);

}
