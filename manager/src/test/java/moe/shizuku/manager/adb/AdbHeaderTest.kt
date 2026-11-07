package moe.shizuku.manager.adb

import org.junit.Assert.assertThrows
import org.junit.Test

class AdbHeaderTest {
    @Test fun acceptsEmptyAndMaximumFrames() {
        AdbMessage.validateHeader(AdbProtocol.A_CNXN, 0, AdbProtocol.A_CNXN xor -1)
        AdbMessage.validateHeader(AdbProtocol.A_WRTE, AdbProtocol.A_MAXDATA, AdbProtocol.A_WRTE xor -1)
    }

    @Test fun rejectsMalformedHeadersBeforePayloadAllocation() {
        for (size in listOf(-1, AdbProtocol.A_MAXDATA + 1, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                AdbMessage.validateHeader(AdbProtocol.A_WRTE, size, AdbProtocol.A_WRTE xor -1)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdbMessage.validateHeader(AdbProtocol.A_WRTE, 0, 0)
        }
    }
}
