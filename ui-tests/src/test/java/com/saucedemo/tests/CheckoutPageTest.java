package com.saucedemo.tests;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.saucedemo.config.TestConfig;
import com.saucedemo.helper.CheckoutTestData;
import com.saucedemo.helper.Money;
import com.saucedemo.pages.actions.CartActions;
import com.saucedemo.pages.actions.CheckoutActions;
import com.saucedemo.pages.actions.InventoryActions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static com.saucedemo.helper.ProductNames.SAUCE_LABS_BACKPACK;
import static com.saucedemo.helper.ProductNames.SAUCE_LABS_BIKE_LIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutPageTest extends BaseTest {
    private static final String STANDARD_USER = "standard_user";
    private static final String PROBLEM_USER = "problem_user";

    @Test
    @Tag("known-defect")
    void TC04_problemUserLastNameInputOverwritesFirstNameAndPreventsSubmission() {
        InventoryActions inventory = loginAs(PROBLEM_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        cart.checkout();
        CheckoutActions checkout = checkoutActions;
        CheckoutTestData data = CheckoutTestData.unique();
        checkout.enterInformation(data.firstName(), data.lastName(), data.postalCode());
        // Confirmed defect (highest severity in this suite): typing in the Last
        // Name field on the checkout form shifts the text into the First Name
        // field instead, leaving Last Name empty.
        // TODO: If the defect is fixed, assert both field values before continuing.
        // assertEquals(data.firstName(), checkoutLocators.firstName().inputValue());
        // assertEquals(data.lastName(), checkoutLocators.lastName().inputValue());
        checkout.continueToOverview();
        PlaywrightAssertions.assertThat(checkoutLocators.error()).containsText("Error: Last Name is required");
    }

    @ParameterizedTest(name = "TC14 checkout missing {0}")
    @CsvSource({
        "ALL, Error: First Name is required",
        "FIRST_NAME, Error: First Name is required",
        "LAST_NAME, Error: Last Name is required",
        "POSTAL_CODE, Error: Postal Code is required"
    })
    void TC14_missingCheckoutInformationIsRejected(String missingField, String expectedError) {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        cart.checkout();
        CheckoutActions checkout = checkoutActions;
        CheckoutTestData data = CheckoutTestData.unique();
        String firstName = missingField.equals("ALL") || missingField.equals("FIRST_NAME") ? "" : data.firstName();
        String lastName = missingField.equals("ALL") || missingField.equals("LAST_NAME") ? "" : data.lastName();
        String postalCode = missingField.equals("ALL") || missingField.equals("POSTAL_CODE") ? "" : data.postalCode();
        checkout.enterInformation(firstName, lastName, postalCode);
        checkout.continueToOverview();
        PlaywrightAssertions.assertThat(checkoutLocators.error()).containsText(expectedError);
    }

    @Test
    void TC19_singleItemSubtotalMatchesListedProductPrice() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        BigDecimal listedPrice = inventory.priceFor(SAUCE_LABS_BIKE_LIGHT);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BIKE_LIGHT);
        CheckoutActions checkout = beginCheckout(cart);

        assertEquals(listedPrice, checkout.totalBeforeTax());
    }

    @Test
    void TC20_TC21_TC22_verifyTotalAmount() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        List<String> selected = inventory.productNames().subList(0, 3);
        BigDecimal expectedSubtotal = selected.stream().map(inventory::priceFor).reduce(BigDecimal.ZERO, BigDecimal::add);
        CartActions cart = addAndOpenCart(inventory, selected.toArray(String[]::new));
        CheckoutActions checkout = beginCheckout(cart);

        BigDecimal expectedTax = Money.tax(expectedSubtotal, TestConfig.TAX_RATE);
        assertEquals(expectedSubtotal, checkout.totalBeforeTax());
        assertEquals(expectedTax, checkout.tax());
        assertEquals(expectedSubtotal.add(expectedTax), checkout.total());
    }

    @Test
    void TC29_canadianPostalCodeIsAccepted() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        cart.checkout();
        CheckoutActions checkout = checkoutActions;
        CheckoutTestData data = CheckoutTestData.uniqueWithPostalCode("A1A 1A1");
        checkout.enterInformation(data.firstName(), data.lastName(), data.postalCode());
        checkout.continueToOverview();
        PlaywrightAssertions.assertThat(page).hasURL(Pattern.compile(".*/checkout-step-two\\.html"));
    }

    @Test
    void TC32_cheapestProductSubtotalMatchesListedPrice() {
        assertSingleItemTotalForPriceExtreme(Comparator.naturalOrder());
    }

    @Test
    void TC32_mostExpensiveProductSubtotalMatchesListedPrice() {
        assertSingleItemTotalForPriceExtreme(Comparator.reverseOrder());
    }

    private void assertSingleItemTotalForPriceExtreme(Comparator<BigDecimal> order) {
        InventoryActions inventory = loginAs(STANDARD_USER);
        List<String> names = inventory.productNames();
        String selected = names.stream()
                .min(Comparator.comparing(inventory::priceFor, order))
                .orElseThrow();
        BigDecimal listedPrice = inventory.priceFor(selected);
        CartActions cart = addAndOpenCart(inventory, selected);
        CheckoutActions checkout = beginCheckout(cart, CheckoutTestData.unique());

        assertEquals(listedPrice, checkout.totalBeforeTax());
    }
}