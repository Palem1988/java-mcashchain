package stest.tron.wallet.dailybuild.trctoken;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.SmartContract;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.midasprotocol.protos.Protocol.TransactionInfo.Code.FAILED;


@Slf4j
public class ContractTrcToken018 {

	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 1000L;
	private static String tokenName = "testAssetIssue_" + now;
	private static long assetAccountId = 0;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
	private long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");
	private byte[] transferTokenContractAddress = null;
	private byte[] receiveTokenContractAddress = null;

	private String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	private String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");

	private ECKey ecKey1 = new ECKey(Utils.getRandom());
	private byte[] dev001Address = ecKey1.getAddress();
	private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

	private ECKey ecKey2 = new ECKey(Utils.getRandom());
	private byte[] user001Address = ecKey2.getAddress();
	private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

	private ECKey ecKey3 = new ECKey(Utils.getRandom());
	private byte[] tmpAddress = ecKey3.getAddress();
	private String tmp001Key = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */
	@BeforeClass(enabled = true)
	public void beforeClass() {

		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

		PublicMethed.printAddress(dev001Key);
		PublicMethed.printAddress(user001Key);
	}

	@Test(enabled = true, description = "Transfer token to an inactive account")
	public void testDeployTransferTokenContract() {
		Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L,
				0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;
		//Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
				10000, start, end, 1, description, url, 100000L,
				100000L, 1L, 1L, dev001Key, blockingStubFull));
		assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The token name: " + tokenName);
		logger.info("The token ID: " + assetAccountId);

		//before deploy, check account resource
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long energyLimit = accountResource.getEnergyLimit();
		long energyUsage = accountResource.getEnergyUsed();
		long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountBefore = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

		logger.info("before energyLimit is " + energyLimit);
		logger.info("before energyUsage is " + energyUsage);
		logger.info("before balanceBefore is " + balanceBefore);
		logger.info("before AssetId: " + assetAccountId
				+ ", devAssetCountBefore: " + devAssetCountBefore);

		String contractName = "transferTokenContract";
		String code = "608060405260e2806100126000396000f300608060405260043610603e5763ffffffff7c01000000"
				+ "000000000000000000000000000000000000000000000000006000350416633be9ece781146043575b600080"
				+ "fd5b606873ffffffffffffffffffffffffffffffffffffffff60043516602435604435606a565b005b604051"
				+ "73ffffffffffffffffffffffffffffffffffffffff84169082156108fc029083908590600081818185878a8a"
				+ "d094505050505015801560b0573d6000803e3d6000fd5b505050505600a165627a7a723058200ba246bdb58b"
				+ "e0f221ad07e1b19de843ab541150b329ddd01558c2f1cefe1e270029";
		String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"toAddress\",\"type\":\"address\"},"
				+ "{\"name\":\"id\",\"type\":\"trcToken\"},{\"name\":\"amount\",\"type\":\"uint256\"}],"
				+ "\"name\":\"TransferTokenTo\",\"outputs\":[],\"payable\":true,\"stateMutability\":"
				+ "\"payable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":true,\"stateMutability\":"
				+ "\"payable\",\"type\":\"constructor\"}]";

		String transferTokenTxid = PublicMethed
				.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
						maxFeeLimit, 0L, 0, 10000,
						assetAccountId, 100, null, dev001Key,
						dev001Address, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(transferTokenTxid, blockingStubFull);

		if (transferTokenTxid == null || infoById.get().getResultValue() != 0) {
			Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage());
		}

		transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();
		SmartContract smartContract = PublicMethed.getContract(transferTokenContractAddress,
				blockingStubFull);
		Assert.assertNotNull(smartContract.getAbi());

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountAfter = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

		logger.info("after energyLimit is " + energyLimit);
		logger.info("after energyUsage is " + energyUsage);
		logger.info("after balanceAfter is " + balanceAfter);
		logger.info("after AssetId: " + assetAccountId
				+ ", devAssetCountAfter: " + devAssetCountAfter);

		Assert.assertTrue(PublicMethed.transferAsset(transferTokenContractAddress,
				assetAccountId, 100L, dev001Address, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Long contractAssetCount = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("Contract has AssetId: " + assetAccountId + ", Count: "
				+ contractAssetCount);

		Assert.assertEquals(Long.valueOf(100), Long.valueOf(devAssetCountBefore - devAssetCountAfter));
		Assert.assertEquals(Long.valueOf(200), contractAssetCount);

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Assert.assertTrue(PublicMethed.transferAsset(user001Address,
				assetAccountId, 10L, dev001Address, dev001Key, blockingStubFull));

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long devEnergyLimitBefore = accountResource.getEnergyLimit();
		long devEnergyUsageBefore = accountResource.getEnergyUsed();
		long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

		logger.info("before trigger, devEnergyLimitBefore is " + devEnergyLimitBefore);
		logger.info("before trigger, devEnergyUsageBefore is " + devEnergyUsageBefore);
		logger.info("before trigger, devBalanceBefore is " + devBalanceBefore);

		accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
		long userEnergyLimitBefore = accountResource.getEnergyLimit();
		long userEnergyUsageBefore = accountResource.getEnergyUsed();
		long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull).getBalance();

		logger.info("before trigger, userEnergyLimitBefore is " + userEnergyLimitBefore);
		logger.info("before trigger, userEnergyUsageBefore is " + userEnergyUsageBefore);
		logger.info("before trigger, userBalanceBefore is " + userBalanceBefore);

		Long transferAssetBefore = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress, assetAccountId,
						blockingStubFull);
		logger.info("before trigger, transferTokenContractAddress has AssetId "
				+ assetAccountId + ", Count is " + transferAssetBefore);

		Long receiveAssetBefore = PublicMethed.getAssetIssueValue(tmpAddress, assetAccountId,
				blockingStubFull);
		logger.info("before trigger, receiveTokenContractAddress has AssetId "
				+ assetAccountId + ", Count is " + receiveAssetBefore);

		String param = "\"" + Base58.encodeBase58(tmpAddress) + "\"," + assetAccountId + ",\"1\"";

		final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"TransferTokenTo(address,trcToken,uint256)", param, false, 0,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		long devEnergyLimitAfter = accountResource.getEnergyLimit();
		long devEnergyUsageAfter = accountResource.getEnergyUsed();
		long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

		logger.info("after trigger, devEnergyLimitAfter is " + devEnergyLimitAfter);
		logger.info("after trigger, devEnergyUsageAfter is " + devEnergyUsageAfter);
		logger.info("after trigger, devBalanceAfter is " + devBalanceAfter);

		accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
		long userEnergyLimitAfter = accountResource.getEnergyLimit();
		long userEnergyUsageAfter = accountResource.getEnergyUsed();
		long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
				.getBalance();

		logger.info("after trigger, userEnergyLimitAfter is " + userEnergyLimitAfter);
		logger.info("after trigger, userEnergyUsageAfter is " + userEnergyUsageAfter);
		logger.info("after trigger, userBalanceAfter is " + userBalanceAfter);

		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<Transaction> trsById = PublicMethed.getTransactionById(triggerTxid, blockingStubFull);
		long feeLimit = trsById.get().getRawData().getFeeLimit();

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);

		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("validateForSmartContract failure",
				infoById.get().getResMessage().toStringUtf8());

		Long transferAssetAfter = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("after trigger, transferTokenContractAddress has AssetId "
				+ assetAccountId + ", transferAssetAfter is " + transferAssetAfter);

		Long receiveAssetAfter = PublicMethed.getAssetIssueValue(tmpAddress,
				assetAccountId, blockingStubFull);
		logger.info("after trigger, receiveTokenContractAddress has AssetId "
				+ assetAccountId + ", receiveAssetAfter is " + receiveAssetAfter);

		Assert.assertEquals(receiveAssetAfter, receiveAssetBefore);
		Assert.assertEquals(transferAssetBefore, transferAssetAfter);

		// unfreeze resource
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 0,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
				user001Address, blockingStubFull);
	}

	/**
	 * constructor.
	 */
	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}


