package io.midasprotocol.common.runtime.vm;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
@Ignore
public class TimeBenchmarkTest {

	private Manager dbManager;
	private ApplicationContext context;
	private DepositImpl deposit;
	private String dbPath = "output_TimeBenchmarkTest";
	private String OWNER_ADDRESS;
	private Application AppT;
	private long totalBalance = 30_000_000_000_000L;


	/**
	 * Init data.
	 */
	@Before
	public void init() {
		Args.setParam(new String[]{"--output-directory", dbPath, "--debug"},
				Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		AppT = ApplicationFactory.create(context);
		OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
		dbManager = context.getBean(Manager.class);
		deposit = DepositImpl.createRoot(dbManager);
		deposit.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
		deposit.addBalance(Hex.decode(OWNER_ADDRESS), totalBalance);
		deposit.commit();
	}

	// pragma solidity ^0.4.2;
	//
	// contract Fibonacci {
	//
	//   event Notify(uint input, uint result);
	//
	//   function fibonacci(uint number) constant returns(uint result) {
	//     if (number == 0) {
	//       return 0;
	//     }
	//     else if (number == 1) {
	//       return 1;
	//     }
	//     else {
	//       uint256 first = 0;
	//       uint256 second = 1;
	//       uint256 ret = 0;
	//       for(uint256 i = 2; i <= number; i++) {
	//         ret = first + second;
	//         first = second;
	//         second = ret;
	//       }
	//       return ret;
	//     }
	//   }
	//
	//   function fibonacciNotify(uint number) returns(uint result) {
	//     result = fibonacci(number);
	//     Notify(number, result);
	//   }
	// }

	@Test
	public void timeBenchmark()
			throws ContractExeException, ContractValidateException, ReceiptCheckErrException, VMIllegalException {
		long value = 0;
		long feeLimit = 200_000_000L; // sun
		long consumeUserResourcePercent = 100;

		String contractName = "timeBenchmark";
		byte[] address = Hex.decode(OWNER_ADDRESS);
		String ABI = "[{\"constant\":false,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],\"name\":\"fibonacciNotify\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],\"name\":\"fibonacci\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"input\",\"type\":\"uint256\"},{\"indexed\":false,\"name\":\"result\",\"type\":\"uint256\"}],\"name\":\"Notify\",\"type\":\"event\"}]";
		String code = "608060405234801561001057600080fd5b506101ba806100206000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633c7fdc701461005157806361047ff414610092575b600080fd5b34801561005d57600080fd5b5061007c600480360381019080803590602001909291905050506100d3565b6040518082815260200191505060405180910390f35b34801561009e57600080fd5b506100bd60048036038101908080359060200190929190505050610124565b6040518082815260200191505060405180910390f35b60006100de82610124565b90507f71e71a8458267085d5ab16980fd5f114d2d37f232479c245d523ce8d23ca40ed8282604051808381526020018281526020019250505060405180910390a1919050565b60008060008060008086141561013d5760009450610185565b600186141561014f5760019450610185565b600093506001925060009150600290505b85811115156101815782840191508293508192508080600101915050610160565b8194505b505050509190505600a165627a7a72305820637e163344c180cd57f4b3a01b07a5267ad54811a5a2858b5d67330a2724ee680029";
		String libraryAddressPair = null;

		TVMTestResult result = TVMTestUtils
				.deployContractAndReturnTVMTestResult(contractName, address, ABI, code,
						value,
						feeLimit, consumeUserResourcePercent, libraryAddressPair,
						dbManager, null);

		long expectEnergyUsageTotal = 88529;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - expectEnergyUsageTotal * 100);
		byte[] contractAddress = result.getContractAddress();

		/* ====================================================================== */
		byte[] triggerData = TVMTestUtils.parseABI("fibonacciNotify(uint)", "");
		result = TVMTestUtils
				.triggerContractAndReturnTVMTestResult(Hex.decode(OWNER_ADDRESS),
						contractAddress, triggerData, 0, feeLimit, dbManager, null);

		long expectEnergyUsageTotal2 = 110;
		Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), expectEnergyUsageTotal2);
		Assert.assertEquals(result.getRuntime().getResult().isRevert(), true);
		Assert.assertTrue(
				result.getRuntime().getResult().getException() == null);
		Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
				totalBalance - (expectEnergyUsageTotal + expectEnergyUsageTotal2) * 100);
	}

	/**
	 * Release resources.
	 */
	@After
	public void destroy() {
		Args.clearParam();
		AppT.shutdownServices();
		AppT.shutdown();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}
}
