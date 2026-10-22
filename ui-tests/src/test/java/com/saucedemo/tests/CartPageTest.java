package com.saucedemo.tests;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.saucedemo.config.TestConfig;
import com.saucedemo.helper.Money;
import com.saucedemo.pages.actions.CartActions;
import com.saucedemo.pages.actions.CheckoutActions;
import com.saucedemo.pages.actions.InventoryActions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.saucedemo.helper.ProductNames.SAUCE_LABS_BACKPACK;
import static com.saucedemo.helper.ProductNames.SAUCE_LABS_BIKE_LIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CartPageTest extends BaseTest {
    private static final String STANDARD_USER = "standard_user";
    private static final String PROBLEM_USER = "problem_user";

    @Test
    void TC13b_problemUserRemoveCartItemsOnCartPage() {
        InventoryActions inventory = loginAs(PROBLEM_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK, SAUCE_LABS_BIKE_LIGHT);
        cart.remove(SAUCE_LABS_BACKPACK);

        assertEquals(List.of(SAUCE_LABS_BIKE_LIGHT), cart.itemNames());
        PlaywrightAssertions.assertThat(cartLocators.cartBadge()).hasText("1");
    }

    @Test
    void TC31_checkoutTotalsChangeAfterRemovingAnItem() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK, SAUCE_LABS_BIKE_LIGHT);
        cart.remove(SAUCE_LABS_BACKPACK);
        PlaywrightAssertions.assertThat(cartLocators.items()).hasCount(1);
        BigDecimal remainingPrice = cart.itemPrices().get(0);
        CheckoutActions checkout = beginCheckout(cart);

        BigDecimal expectedTax = Money.tax(remainingPrice, TestConfig.TAX_RATE);
        assertEquals(remainingPrice, checkout.totalBeforeTax());
        assertEquals(expectedTax, checkout.tax());
        assertEquals(remainingPrice.add(expectedTax), checkout.total());
    }

    @Test
    void TC33_cartWasClearedAfterReturningFromOrderConfirmation() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        CheckoutActions checkout = beginCheckout(cart);
        checkout.finish();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).isVisible();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).hasText("Thank you for your order!");
        page.goBack();
        page.goBack();
        inventory.openCart();

        assertEquals(0, cartLocators.items().count());
        PlaywrightAssertions.assertThat(inventoryLocators.cartBadge()).not().isVisible();
    }
}