package org.nexis.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

import org.nexis.base.utils.ByteUtils;
import org.nexis.base.Sha256Hash;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionOutput;
import org.nexis.base.Coin;
import org.nexus.base.proto.NexusProtocol;

public class DiagnosticTransactionProtoTest {

    @Test
    public void testProtoRoundtripDiff() {
    // Create a simple genesis transaction and an output with explicit script bytes
    // genesis input script must be at least 2 bytes per Transaction.genesis precondition
    Transaction tx = Transaction.genesis(new byte[]{0x01, 0x02});
        tx.addOutput(new TransactionOutput(tx, Coin.valueOf(100_000), new byte[]{0x02}));

        byte[] before = tx.serialize();
        Sha256Hash idBefore = tx.getTxId();

        NexusProtocol.Transaction proto = tx.toProto();

        Transaction tx2 = Transaction.read(proto);
        byte[] after = tx2.serialize();
        Sha256Hash idAfter = tx2.getTxId();

        System.out.println("DiagnosticTransactionProtoTest: before txId = " + idBefore);
        System.out.println("DiagnosticTransactionProtoTest: after  txId = " + idAfter);

        if (!idBefore.equals(idAfter)) {
                StringBuilder sb = new StringBuilder();
                sb.append("TXID mismatch after proto roundtrip\n");
                sb.append("before txId: ").append(idBefore).append("\n");
                sb.append(" after txId: ").append(idAfter).append("\n");
                sb.append("before hex: ").append(ByteUtils.formatHex(before)).append("\n");
                sb.append(" after hex: ").append(ByteUtils.formatHex(after)).append("\n");

                int min = Math.min(before.length, after.length);
                int diffIndex = -1;
                for (int i = 0; i < min; i++) {
                    if (before[i] != after[i]) { diffIndex = i; break; }
                }
                sb.append("first diff index: ").append(diffIndex).append("\n");
                int start = diffIndex <= 0 ? 0 : Math.max(0, diffIndex - 8);
                int end = diffIndex < 0 ? Math.min(before.length, 16) : Math.min(after.length, diffIndex + 8);
                sb.append("before segment: ").append(ByteUtils.formatHex(Arrays.copyOfRange(before, start, end))).append("\n");
                sb.append(" after segment: ").append(ByteUtils.formatHex(Arrays.copyOfRange(after, start, end))).append("\n");

                fail(sb.toString());
        }

        assertEquals(idBefore, idAfter, "TxId changed after proto roundtrip");
    }
}
