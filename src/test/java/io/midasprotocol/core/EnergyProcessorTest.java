package io.midasprotocol.core;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter.AdaptiveResourceLimitConstants;
import io.midasprotocol.core.config.Parameter.ChainConstant;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.EnergyProcessor;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

@Slf4j
public class EnergyProcessorTest {

	private static final String dbPath = "output_energy_processor_test";
	private static final String ASSET_NAME;
	private static final long ASSET_ID;
	private static final String CONTRACT_PROVIDER_ADDRESS;
	private static final String USER_ADDRESS;
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		ASSET_NAME = "test_token";
		ASSET_ID = 1;
		CONTRACT_PROVIDER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		USER_ADDRESS = Wallet.getAddressPreFixString() +  "abd4b9367799eaa3197fecb144eb71de1e049abc";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
	}

	/**
	 * Release resources.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void createCapsule() {
		AccountCapsule contractProvierCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("owner"),
						ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS)),
						AccountType.Normal,
						0L);
		contractProvierCapsule.addAsset(ASSET_ID, 100L);

		AccountCapsule userCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("asset"),
						ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)),
						AccountType.AssetIssue,
						dbManager.getDynamicPropertiesStore().getAssetIssueFee());

		dbManager.getAccountStore().reset();
		dbManager.getAccountStore()
				.put(contractProvierCapsule.getAddress().toByteArray(), contractProvierCapsule);
		dbManager.getAccountStore().put(userCapsule.getAddress().toByteArray(), userCapsule);

	}


	//todo ,replaced by smartContract later
	private AssetIssueContract getAssetIssueContract() {
		return Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(ASSET_NAME))
				.setFreeAssetBandwidthLimit(1000L)
				.setPublicFreeAssetBandwidthLimit(1000L)
				.build();
	}

	@Test
	public void testUseContractCreatorEnergy() throws Exception {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(10_000_000L);

		AccountCapsule ownerCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS));
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		EnergyProcessor processor = new EnergyProcessor(dbManager);
		long energy = 10000;
		long now = 1526647838000L;

		boolean result = processor.useEnergy(ownerCapsule, energy, now);
		Assert.assertFalse(result);

		ownerCapsule.setFrozenForEnergy(1_000_000_000L, 0L);
		result = processor.useEnergy(ownerCapsule, energy, now);
		Assert.assertTrue(result);

		AccountCapsule ownerCapsuleNew = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS));

		Assert.assertEquals(1526647838000L, ownerCapsuleNew.getLatestOperationTime());
		Assert.assertEquals(1526647838000L,
				ownerCapsuleNew.getAccountResource().getLatestEnergyConsumeTime());
		Assert.assertEquals(10000L, ownerCapsuleNew.getAccountResource().getEnergyUsage());

	}

	@Test
	public void updateAdaptiveTotalEnergyLimit() {
		EnergyProcessor processor = new EnergyProcessor(dbManager);

		// open
		dbManager.getDynamicPropertiesStore().saveAllowAdaptiveEnergy(1);

		// Test resource usage auto reply
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
		long now = dbManager.getWitnessController().getHeadSlot();
		dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageTime(now);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(4000L);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
				1526647838000L + AdaptiveResourceLimitConstants.PERIODS_MS / 2);
		processor.updateTotalEnergyAverageUsage();
		Assert.assertEquals(2000L,
				dbManager.getDynamicPropertiesStore().getTotalEnergyAverageUsage());

		// test saveTotalEnergyLimit
		long ratio = ChainConstant.WINDOW_SIZE_MS / AdaptiveResourceLimitConstants.PERIODS_MS;
		dbManager.getDynamicPropertiesStore().saveTotalEnergyLimit(10000L * ratio);
		Assert.assertEquals(1000L,
				dbManager.getDynamicPropertiesStore().getTotalEnergyTargetLimit());

		//Test exceeds resource limit
		dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(10000L * ratio);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(3000L);
		processor.updateAdaptiveTotalEnergyLimit();
		Assert.assertEquals(10000L * ratio,
				dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());

		//Test exceeds resource limit 2
		dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(20000L * ratio);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(3000L);
		processor.updateAdaptiveTotalEnergyLimit();
		Assert.assertEquals(20000L * ratio * 99 / 100L,
				dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());

		//Test less than resource limit
		dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(20000L * ratio);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(500L);
		processor.updateAdaptiveTotalEnergyLimit();
		Assert.assertEquals(20000L * ratio * 1000 / 999L,
				dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());
	}


}
