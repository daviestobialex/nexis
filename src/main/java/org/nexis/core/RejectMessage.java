/*
 * Copyright 2013 Matt Corallo
 * Copyright 2015 Andreas Schildbach
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
package org.nexis.core;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.nexis.base.VarInt;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 * A message sent by nodes when a message we sent was rejected (ie a transaction
 * had too little fee/was invalid/etc). See
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0061.mediawiki">BIP61</a>
 * for details.
 * <p>
 * Instances of this class are immutable.
 */
public class RejectMessage {

    public enum RejectCode {
        /**
         * The message was not able to be parsed
         */
        MALFORMED((byte) 0x01),
        /**
         * The message described an invalid object
         */
        INVALID((byte) 0x10),
        /**
         * The message was obsolete or described an object which is obsolete
         * (e.g. unsupported, old version, v1 block)
         */
        OBSOLETE((byte) 0x11),
        /**
         * The message was relayed multiple times or described an object which
         * is in conflict with another. This message can describe errors in
         * protocol implementation or the presence of an attempt to DOUBLE
         * SPEND.
         */
        DUPLICATE((byte) 0x12),
        /**
         * The message described an object was not standard and was thus not
         * accepted. Bitcoin Core has a concept of standard transaction forms,
         * which describe scripts and encodings which it is willing to relay
         * further. Other transactions are neither relayed nor mined, though
         * they are considered valid if they appear in a block.
         */
        NONSTANDARD((byte) 0x40),
        /**
         * This refers to a specific form of NONSTANDARD transactions, which
         * have an output smaller than some constant defining them as dust (this
         * is no longer used).
         */
        DUST((byte) 0x41),
        /**
         * The messages described an object which did not have sufficient fee to
         * be relayed further.
         */
        INSUFFICIENTFEE((byte) 0x42),
        /**
         * The message described a block which was invalid according to
         * hard-coded checkpoint blocks.
         */
        CHECKPOINT((byte) 0x43),
        OTHER((byte) 0xff);

        final byte code;

        RejectCode(byte code) {
            this.code = code;
        }

        public static RejectCode fromCode(byte code) {
            return Stream.of(RejectCode.values())
                    .filter(r -> r.code == code)
                    .findFirst()
                    .orElse(OTHER);
        }
    }

    private final String rejectedMessage;
    private final RejectCode code;
    private final String reason;
//    @Nullable
    private final Sha256Hash rejectedMessageHash;

    /**
     * Deserialize this message from a given payload.
     *
     * @param payload payload to deserialize from
     * @return read message
     * @throws BufferUnderflowException if the read message extends beyond the
     * remaining bytes of the payload
     */
    public static RejectMessage read(NexusProtocol.RejectMessage payload) throws BufferUnderflowException {
        RejectCode code = fromProtoCode(payload.getCode());
        Sha256Hash messageHash = Sha256Hash.wrap(payload.getRejectedMessageHash().toByteArray());
        return new RejectMessage(code, messageHash, payload.getRejectedMessage(), payload.getReason());
    }

    /**
     * Constructs a reject message that fingers the object with the given hash
     * as rejected for the given reason.
     *
     * @param code
     * @param rejectedMessageHash
     * @param rejectedMessage
     * @param reason
     */
    public RejectMessage(RejectCode code, Sha256Hash rejectedMessageHash, String rejectedMessage,
            String reason) {
        this.rejectedMessage = Objects.requireNonNull(rejectedMessage);
        this.code = Objects.requireNonNull(code);
        this.reason = Objects.requireNonNull(reason);
        this.rejectedMessageHash = rejectedMessageHash;
    }

    public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        byte[] messageBytes = rejectedMessage.getBytes(StandardCharsets.UTF_8);
        stream.write(VarInt.of(messageBytes.length).serialize());
        stream.write(messageBytes);
        stream.write(code.code);
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        stream.write(VarInt.of(reasonBytes.length).serialize());
        stream.write(reasonBytes);
        if ("block".equals(rejectedMessage) || "tx".equals(rejectedMessage)) {
            stream.write(rejectedMessageHash.serialize());
        }
    }

    /**
     * Provides the type of message which was rejected by the peer. Note that
     * this is ENTIRELY UNTRUSTED and should be sanity-checked before it is
     * printed or processed.
     *
     * @return rejected message type
     */
    public String rejectedMessage() {
        return rejectedMessage;
    }

    /**
     * @deprecated use {@link #rejectedMessage()}
     */
    @Deprecated
    public String getRejectedMessage() {
        return rejectedMessage();
    }

    /**
     * Provides the hash of the rejected object (if getRejectedMessage() is
     * either "tx" or "block"), otherwise null.
     *
     * @return hash of rejected object
     */
    public Sha256Hash rejectedMessageHash() {
        return rejectedMessageHash;
    }

    /**
     * @deprecated use {@link #rejectedMessageHash()}
     */
    @Deprecated
    public Sha256Hash getRejectedObjectHash() {
        return rejectedMessageHash();
    }

    /**
     * The reason code given for why the peer rejected the message.
     *
     * @return reject reason code
     */
    public RejectCode code() {
        return code;
    }

    /**
     * @deprecated use {@link #code()}
     */
    @Deprecated
    public RejectCode getReasonCode() {
        return code();
    }

    /**
     * The reason message given for rejection. Note that this is ENTIRELY
     * UNTRUSTED and should be sanity-checked before it is printed or processed.
     *
     * @return reject reason
     */
    public String reason() {
        return reason;
    }

    /**
     * @deprecated use {@link #reason()}
     */
    @Deprecated
    public String getReasonString() {
        return reason();
    }

    /**
     * A String representation of the relevant details of this reject message.
     * Be aware that the value returned by this method includes the value
     * returned by {@link #getReasonString() getReasonString}, which is taken
     * from the reject message unchecked. Through malice or otherwise, it might
     * contain control characters or other harmful content.
     */
    @Override
    public String toString() {
        return String.format(Locale.US, "Reject: %s %s for reason '%s' (%d)", rejectedMessage,
                rejectedMessageHash != null ? rejectedMessageHash : "", reason, code.code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RejectMessage other = (RejectMessage) o;
        return rejectedMessage.equals(other.rejectedMessage) && code.equals(other.code)
                && reason.equals(other.reason) && rejectedMessageHash.equals(other.rejectedMessageHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rejectedMessage, code, reason, rejectedMessageHash);
    }

    private static RejectMessage.RejectCode fromProtoCode(NexusProtocol.RejectCode protoCode) {
        switch (protoCode) {
            case REJECT_MALFORMED:
                return RejectMessage.RejectCode.MALFORMED;
            case REJECT_INVALID:
                return RejectMessage.RejectCode.INVALID;
            case REJECT_OBSOLETE:
                return RejectMessage.RejectCode.OBSOLETE;
            case REJECT_DUPLICATE:
                return RejectMessage.RejectCode.DUPLICATE;
            case REJECT_NONSTANDARD:
                return RejectMessage.RejectCode.NONSTANDARD;
            case REJECT_DUST:
                return RejectMessage.RejectCode.DUST;
            case REJECT_INSUFFICIENTFEE:
                return RejectMessage.RejectCode.INSUFFICIENTFEE;
            case REJECT_CHECKPOINT:
                return RejectMessage.RejectCode.CHECKPOINT;
            case REJECT_OTHER:
            default:
                return RejectMessage.RejectCode.OTHER;
        }
    }

    private static NexusProtocol.RejectCode toProtoCode(RejectMessage.RejectCode code) {
        switch (code) {
            case MALFORMED:
                return NexusProtocol.RejectCode.REJECT_MALFORMED;
            case INVALID:
                return NexusProtocol.RejectCode.REJECT_INVALID;
            case OBSOLETE:
                return NexusProtocol.RejectCode.REJECT_OBSOLETE;
            case DUPLICATE:
                return NexusProtocol.RejectCode.REJECT_DUPLICATE;
            case NONSTANDARD:
                return NexusProtocol.RejectCode.REJECT_NONSTANDARD;
            case DUST:
                return NexusProtocol.RejectCode.REJECT_DUST;
            case INSUFFICIENTFEE:
                return NexusProtocol.RejectCode.REJECT_INSUFFICIENTFEE;
            case CHECKPOINT:
                return NexusProtocol.RejectCode.REJECT_CHECKPOINT;
            case OTHER:
            default:
                return NexusProtocol.RejectCode.REJECT_OTHER;
        }
    }
}
