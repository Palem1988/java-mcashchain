package org.tron.core.util;

import com.google.common.math.LongMath;
import org.tron.core.config.Parameter;

public class ConversionUtil {
	public static long McashToMatoshi(long mcashAmount) {
		return LongMath.checkedMultiply(mcashAmount, Parameter.ChainConstant.TEN_POW_DECIMALS);
	}

	public static long McashToMatoshi(double mcashAmount) {
		mcashAmount *= Parameter.ChainConstant.TEN_POW_DECIMALS;
		return (long) mcashAmount;
	}

}
