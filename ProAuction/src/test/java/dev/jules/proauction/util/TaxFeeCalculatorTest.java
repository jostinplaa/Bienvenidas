package dev.jules.proauction.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import dev.jules.proauction.util.TaxFeeCalculator.FeeCalculationResult;
import dev.jules.proauction.util.TaxFeeCalculator.TaxCalculationResult;

public class TaxFeeCalculatorTest extends TestCase {

    public TaxFeeCalculatorTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TaxFeeCalculatorTest.class);
    }

    // Helper for double comparison due to potential floating point inaccuracies
    private static final double DELTA = 1e-9; // A small tolerance

    // Tests for calculateListingFee
    public void testCalculateListingFee_ZeroPercent() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(100.0, 0.0);
        assertEquals(0.0, result.feeAmount, DELTA);
    }

    public void testCalculateListingFee_ZeroPrice() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(0.0, 10.0);
        assertEquals(0.0, result.feeAmount, DELTA);
    }

    public void testCalculateListingFee_StandardCase() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(100.0, 10.0); // 10% of 100 = 10
        assertEquals(10.0, result.feeAmount, DELTA);
    }

    public void testCalculateListingFee_Rounding() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(33.33, 10.0); // 10% of 33.33 = 3.333
        assertEquals(3.33, result.feeAmount, DELTA); // Should be rounded to 3.33
    }

    public void testCalculateListingFee_MinimumFee_SmallPercentage() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(10.0, 0.01); // 0.01% of 10 = 0.001
        assertEquals(0.01, result.feeAmount, DELTA); // Should be adjusted to 0.01
    }

    public void testCalculateListingFee_MinimumFee_SlightlyAboveMinimum() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(150.0, 0.01); // 0.01% of 150 = 0.015
        assertEquals(0.02, result.feeAmount, DELTA); // Should be rounded to 0.02 after calculation
    }

    public void testCalculateListingFee_ExactMinimumThreshold() {
        FeeCalculationResult result = TaxFeeCalculator.calculateListingFee(1.0, 0.5); // 0.5% of 1.0 = 0.005
        assertEquals(0.01, result.feeAmount, DELTA); // Should be adjusted to 0.01
    }


    // Tests for calculateSalesTax
    public void testCalculateSalesTax_ZeroPercent() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(100.0, 0.0);
        assertEquals(0.0, result.taxAmount, DELTA);
        assertEquals(100.0, result.netAmountForSeller, DELTA);
    }

    public void testCalculateSalesTax_ZeroPrice() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(0.0, 10.0);
        assertEquals(0.0, result.taxAmount, DELTA);
        assertEquals(0.0, result.netAmountForSeller, DELTA);
    }

    public void testCalculateSalesTax_StandardCase() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(100.0, 10.0); // Tax 10, Net 90
        assertEquals(10.0, result.taxAmount, DELTA);
        assertEquals(90.0, result.netAmountForSeller, DELTA);
    }

    public void testCalculateSalesTax_Rounding() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(99.99, 5.0); // Tax 5.0% of 99.99 = 4.9995
        assertEquals(5.00, result.taxAmount, DELTA); // Rounded to 5.00
        assertEquals(94.99, result.netAmountForSeller, DELTA); // 99.99 - 5.00 = 94.99
    }

    public void testCalculateSalesTax_RoundingNet() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(33.33, 10.0); // Tax 3.333 -> 3.33. Net 29.997 -> 30.00
        assertEquals(3.33, result.taxAmount, DELTA);
        assertEquals(30.00, result.netAmountForSeller, DELTA); // 33.33 - 3.33 = 30.00. Corrected expected value
    }


    public void testCalculateSalesTax_MinimumTax_SmallPercentage() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(20.0, 0.02); // 0.02% of 20 = 0.004
        assertEquals(0.01, result.taxAmount, DELTA); // Adjusted to 0.01
        assertEquals(19.99, result.netAmountForSeller, DELTA); // 20.00 - 0.01 = 19.99
    }

    public void testCalculateSalesTax_MinimumTax_SlightlyAboveMinimum() {
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(150.0, 0.01); // 0.01% of 150 = 0.015 -> 0.02
        assertEquals(0.02, result.taxAmount, DELTA);
        assertEquals(149.98, result.netAmountForSeller, DELTA);
    }

    public void testCalculateSalesTax_FullTax() { // 100% tax
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(100.0, 100.0);
        assertEquals(100.0, result.taxAmount, DELTA);
        assertEquals(0.0, result.netAmountForSeller, DELTA);
    }

    public void testCalculateSalesTax_OverTax() { // More than 100% tax (should cap net at 0)
        TaxCalculationResult result = TaxFeeCalculator.calculateSalesTax(100.0, 110.0);
        assertEquals(110.0, result.taxAmount, DELTA); // Tax is calculated as is
        assertEquals(0.0, result.netAmountForSeller, DELTA); // Net amount capped at 0
    }
}
