package io.midasprotocol.common.runtime.vm;

import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.runtime.TVMTestResult;
import io.midasprotocol.common.runtime.TVMTestUtils;
import io.midasprotocol.common.storage.DepositImpl;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ReceiptCheckErrException;
import io.midasprotocol.core.exception.VMIllegalException;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

@Slf4j
public class EnergyWhenSendAndTransferTest {

	private Manager dbManager;
	private ApplicationContext context;
	private DepositImpl deposit;
	private String dbPath = "output_EnergyWhenSendAndTransferTest";
	private String OWNER_ADDRESS;
	private Application AppT;
	private long totalBalance = 3_000_000_000_000_000L;

	/**
	 * Init data.
	 */
	@Before
	public void init() {
		Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		AppT = ApplicationFactory.create(context);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		dbManager = context.getBean(Manager.class);
		deposit = DepositImpl.createRoot(dbManager);
		deposit.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
		deposit.addBalance(Hex.decode(OWNER_ADDRESS), totalBalance);
		deposit.commit();
	}

	// solidity for callValueTest
	// pragma solidity ^0.4.0;
	//
	// contract SubContract {
	//
	//   constructor () payable {}
	//   mapping(uint256=>uint256) map;
	//
	//   function doSimple() public payable returns (uint ret) {
	//     return 42;
	//   }
	//
	//   function doComplex() public payable returns (uint ret) {
	//     for (uint i = 0; i < 10; i++) {
	//       map[i] = i;
	//     }
	//   }
	//
	// }
	//
	// contract TestForValueGasFunction {
	//
	//   SubContract subContract;
	//
	//   constructor () payable {
	//     subContract = new SubContract();
	//   }
	//
	//   function simpleCall() public { subContract.doSimple.value(10).gas(3)(); }
	//
	//   function complexCall() public { subContract.doComplex.value(10).gas(3)(); }
	//
	// }

	@Test
	public void callValueTest()
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {

		long value = 1000000000L;
		long feeLimit = 100_000_000_000L; // sun
		long consumeUserResourcePercent = 100;
		byte[] address = Hex.decode(OWNER_ADDRESS);
		TVMTestResult result = deployCallValueTestContract(value, feeLimit,
				consumeUserResourcePercent);

		long expectEnergyUsageTotal = 174639;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal);
		byte[] contractAddress = result.getContractAddress();
		Assert.assertEquals(deposit.getAccount(contractAddress).getBalance(), value);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - expectEnergyUsageTotal * Constant.MATOSHI_PER_ENERGY);

		/* =================================== CALL simpleCall() =================================== */
		byte[] triggerData = TVMTestUtils.parseABI("simpleCall()", null);
		result = TVMTestUtils
				.triggerContractAndReturnTVMTestResult(Hex.decode(OWNER_ADDRESS),
						contractAddress, triggerData, 0, feeLimit, dbManager, null);

		long expectEnergyUsageTotal2 = 7370;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal2);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - (expectEnergyUsageTotal + expectEnergyUsageTotal2) * Constant.MATOSHI_PER_ENERGY);

		/* =================================== CALL complexCall() =================================== */
		triggerData = TVMTestUtils.parseABI("complexCall()", null);
		result = TVMTestUtils
				.triggerContractAndReturnTVMTestResult(Hex.decode(OWNER_ADDRESS),
						contractAddress, triggerData, 0, feeLimit, dbManager, null);

		long expectEnergyUsageTotal3 = 9459;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal3);
		Assert.assertEquals(result.getRuntime().getResult().isRevert(), true);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value
						- (expectEnergyUsageTotal + expectEnergyUsageTotal2 + expectEnergyUsageTotal3) * Constant.MATOSHI_PER_ENERGY);
	}

	// solidity for sendTest and transferTest
	// pragma solidity ^0.4.0;
	//
	// contract SubContract {
	//
	//   constructor () payable {}
	//   mapping(uint256=>uint256) map;
	//
	//   function () payable {
	//     map[1] = 1;
	//   }
	// }
	//
	// contract TestForSendAndTransfer {
	//
	//   SubContract subContract;
	//
	//   constructor () payable {
	//     subContract = new SubContract();
	//   }
	//
	//
	//   function doSend() public { address(subContract).send(10000); }
	//
	//   function doTransfer() public { address(subContract).transfer(10000); }
	//
	//   function getBalance() public view returns(uint256 balance){
	//     balance = address(this).balance;
	//   }
	//
	// }


	@Test
	public void sendTest()
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {

		long value = 100_000L;
		long feeLimit = 100_000_000_000L; // sun
		long consumeUserResourcePercent = 100;
		byte[] address = Hex.decode(OWNER_ADDRESS);
		TVMTestResult result = deploySendAndTransferTestContract(value, feeLimit,
				consumeUserResourcePercent);

		long expectEnergyUsageTotal = 140194;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal);
		byte[] contractAddress = result.getContractAddress();
		Assert.assertEquals(deposit.getAccount(contractAddress).getBalance(), value);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - expectEnergyUsageTotal * Constant.MATOSHI_PER_ENERGY);

		/* =================================== CALL doSend() =================================== */
		byte[] triggerData = TVMTestUtils.parseABI("doSend()", null);
		result = TVMTestUtils
				.triggerContractAndReturnTVMTestResult(Hex.decode(OWNER_ADDRESS),
						contractAddress, triggerData, 0, feeLimit, dbManager, null);

		long expectEnergyUsageTotal2 = 9325;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal2);
		Assert.assertNull(result.getRuntime().getResult().getException());
		Assert.assertFalse(result.getRuntime().getResult().isRevert());
		Assert.assertEquals(deposit.getAccount(contractAddress).getBalance(), value);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - (expectEnergyUsageTotal + expectEnergyUsageTotal2) * Constant.MATOSHI_PER_ENERGY);
	}

	@Test
	public void transferTest()
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {

		long value = 100_000L;
		// long value = 10000000L;
		long feeLimit = 100_000_000_000L; // sun
		long consumeUserResourcePercent = 100;
		byte[] address = Hex.decode(OWNER_ADDRESS);
		TVMTestResult result = deploySendAndTransferTestContract(value, feeLimit,
				consumeUserResourcePercent);

		long expectEnergyUsageTotal = 140194;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal);
		byte[] contractAddress = result.getContractAddress();
		Assert.assertEquals(deposit.getAccount(contractAddress).getBalance(), value);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - expectEnergyUsageTotal * Constant.MATOSHI_PER_ENERGY);

		/* =================================== CALL doSend() =================================== */
		byte[] triggerData = TVMTestUtils.parseABI("doTransfer()", null);
		result = TVMTestUtils
				.triggerContractAndReturnTVMTestResult(Hex.decode(OWNER_ADDRESS),
						contractAddress, triggerData, 0, feeLimit, dbManager, null);

		long expectEnergyUsageTotal2 = 9330;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal2);
		Assert.assertNull(result.getRuntime().getResult().getException());
		Assert.assertTrue(result.getRuntime().getResult().isRevert());
		Assert.assertEquals(deposit.getAccount(contractAddress).getBalance(), value);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - value - (expectEnergyUsageTotal + expectEnergyUsageTotal2) * Constant.MATOSHI_PER_ENERGY);
	}

	public TVMTestResult deployCallValueTestContract(long value, long feeLimit,
													 long consumeUserResourcePercent)
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {
		String contractName = "TestForCallValue";
		byte[] address = Hex.decode(OWNER_ADDRESS);
		String ABI = "[{\"constant\":false,\"inputs\":[],\"name\":\"complexCall\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"simpleCall\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"constructor\"}]";
		String code = "608060405261000c61004e565b604051809103906000f080158015610028573d6000803e3d6000fd5b5060008054600160a060020a031916600160a060020a039290921691909117905561005d565b60405160d68061020b83390190565b61019f8061006c6000396000f3006080604052600436106100325763ffffffff60e060020a60003504166306ce93af811461003757806340de221c1461004e575b600080fd5b34801561004357600080fd5b5061004c610063565b005b34801561005a57600080fd5b5061004c610103565b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663cd95478c600a6003906040518363ffffffff1660e060020a0281526004016020604051808303818589803b1580156100d357600080fd5b5088f11580156100e7573d6000803e3d6000fd5b5050505050506040513d60208110156100ff57600080fd5b5050565b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663b993e5e2600a6003906040518363ffffffff1660e060020a0281526004016020604051808303818589803b1580156100d357600080fd00a165627a7a72305820cb5f172ca9f81235a8b33ee1ddef9dd1b398644cf61228569356ff051bfaf3d10029608060405260c4806100126000396000f30060806040526004361060485763ffffffff7c0100000000000000000000000000000000000000000000000000000000600035041663b993e5e28114604d578063cd95478c146065575b600080fd5b6053606b565b60408051918252519081900360200190f35b60536070565b602a90565b6000805b600a81101560945760008181526020819052604090208190556001016074565b50905600a165627a7a723058205ded543feb546472be4e116e713a2d46b8dafc823ca31256e67a1be92a6752730029";
		String libraryAddressPair = null;

		return TVMTestUtils
				.deployContractAndReturnTVMTestResult(contractName, address, ABI, code,
						value,
						feeLimit, consumeUserResourcePercent, libraryAddressPair,
						dbManager, null);
	}

	public TVMTestResult deploySendAndTransferTestContract(long value, long feeLimit,
														   long consumeUserResourcePercent)
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {
		String contractName = "TestForSendAndTransfer";
		byte[] address = Hex.decode(OWNER_ADDRESS);
		String ABI = "[{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"balance\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"doTransfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"doSend\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"constructor\"}]";
		String code = "608060405261000c61004e565b604051809103906000f080158015610028573d6000803e3d6000fd5b5060008054600160a060020a031916600160a060020a039290921691909117905561005d565b604051606f806101c583390190565b6101598061006c6000396000f3006080604052600436106100565763ffffffff7c010000000000000000000000000000000000000000000000000000000060003504166312065fe0811461005b57806333182e8f14610082578063e3d237f914610099575b600080fd5b34801561006757600080fd5b506100706100ae565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100976100b3565b005b3480156100a557600080fd5b506100976100f9565b303190565b6000805460405173ffffffffffffffffffffffffffffffffffffffff90911691906127109082818181858883f193505050501580156100f6573d6000803e3d6000fd5b50565b6000805460405173ffffffffffffffffffffffffffffffffffffffff90911691906127109082818181858883f150505050505600a165627a7a72305820677efa58ed7b277b589fe6626cb77f930caeb0f75c3ab638bfe07292db961a8200296080604052605e8060116000396000f3006080604052600160008181526020527fada5013122d395ba3c54772283fb069b10426056ef8ca54750cb9bb552a59e7d550000a165627a7a7230582029b27c10c1568d590fa66bc0b7d42537a314c78d028f59a188fa411f7fc15c4f0029";
		String libraryAddressPair = null;

		return TVMTestUtils
				.deployContractAndReturnTVMTestResult(contractName, address, ABI, code,
						value,
						feeLimit, consumeUserResourcePercent, libraryAddressPair,
						dbManager, null);
	}

	/**
	 * Release resources.
	 */
	@After
	public void destroy() {
		AppT.shutdownServices();
		AppT.shutdown();
		context.destroy();
		Args.clearParam();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.warn("Release resources failure.");
		}
	}

}
