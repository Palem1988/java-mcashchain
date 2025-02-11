/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.core;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.capsule.utils.TxInputUtil;
import io.midasprotocol.protos.Protocol.TXInput;

@Slf4j
public class TxInputUtilTest {

	@Test
	public void testNewput() {
		byte[] bytes = new byte[32];
		for (int i = 0; i < bytes.length; i++) {
			System.out.println("-----------" + bytes[i]);
		}
	}

	@Test
	public void testNewTxInput() {
		byte[] txId = ByteArray
				.fromHexString("2c0937534dd1b3832d05d865e8e6f2bf23218300b33a992740d45ccab7d4f519");
		long vout = 777L;
		byte[] signature = ByteArray
				.fromHexString("ded9c2181fd7ea468a7a7b1475defe90bb0fc0ca8d0f2096b0617465cea6568c");
		byte[] pubkey = ByteArray
				.fromHexString("a0c9d5524c055381fe8b1950e0c3b09d252add57a7aec061ae258aa03ee25822");
		TXInput txInput = TxInputUtil.newTxInput(txId, vout, signature, pubkey);

		Assert.assertArrayEquals(txId, txInput.getRawData().getTxId().toByteArray());
		Assert.assertEquals(vout, txInput.getRawData().getVout());
		Assert.assertArrayEquals(signature, txInput.getSignature().toByteArray());
		Assert.assertArrayEquals(pubkey, txInput.getRawData().getPubKey().toByteArray());

	}
}
