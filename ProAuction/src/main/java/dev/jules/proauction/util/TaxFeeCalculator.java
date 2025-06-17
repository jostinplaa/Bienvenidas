package dev.jules.proauction.util;

public class TaxFeeCalculator {

    public static class FeeCalculationResult {
        public final double feeAmount;
        // No net amount needed for listing fee as it's a direct charge

        public FeeCalculationResult(double feeAmount) {
            this.feeAmount = feeAmount;
        }
    }

    public static class TaxCalculationResult {
        public final double taxAmount;
        public final double netAmountForSeller;

        public TaxCalculationResult(double taxAmount, double netAmountForSeller) {
            this.taxAmount = taxAmount;
            this.netAmountForSeller = netAmountForSeller;
        }
    }

    public static FeeCalculationResult calculateListingFee(double startingPrice, double feePercentage) {
        if (feePercentage <= 0 || startingPrice <= 0) {
            return new FeeCalculationResult(0.0);
        }
        double fee = startingPrice * (feePercentage / 100.0);
        if (fee > 0 && fee < 0.01) {
            fee = 0.01; // Ensure a minimum meaningful fee if calculated positive but very small
        }
        fee = Math.round(fee * 100.0) / 100.0; // Round to 2 decimal places
        return new FeeCalculationResult(fee);
    }

    public static TaxCalculationResult calculateSalesTax(double finalPrice, double taxPercentage) {
        if (taxPercentage <= 0 || finalPrice <= 0) {
            return new TaxCalculationResult(0.0, finalPrice);
        }
        double tax = finalPrice * (taxPercentage / 100.0);
        if (tax > 0 && tax < 0.01) {
            tax = 0.01; // Ensure a minimum meaningful tax if calculated positive but very small
        }
        tax = Math.round(tax * 100.0) / 100.0; // Round to 2 decimal places

        double netAmount = finalPrice - tax;
        if (netAmount < 0) {
            netAmount = 0; // Ensure seller doesn't get negative money
        }
        // netAmount is already effectively rounded if finalPrice and tax were correctly rounded.
        // Forcing another round can be good for explicit safety:
        netAmount = Math.round(netAmount * 100.0) / 100.0;
        return new TaxCalculationResult(tax, netAmount);
    }
}
