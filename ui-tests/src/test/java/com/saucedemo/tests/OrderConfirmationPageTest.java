package com.saucedemo.tests;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.saucedemo.config.TestConfig;
import com.saucedemo.helper.CheckoutTestData;
import com.saucedemo.helper.TestMetrics;
import com.saucedemo.pages.actions.CartActions;
import com.saucedemo.pages.actions.CheckoutActions;
import com.saucedemo.pages.actions.InventoryActions;
import org.junit.jupiter.api.Test;

import static com.saucedemo.helper.ProductNames.SAUCE_LABS_BACKPACK;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderConfirmationPageTest extends BaseTest {
    private static final String STANDARD_USER = "standard_user";
    private static final String PERFORMANCE_GLITCH_USER = "performance_glitch_user";

    @Test
    void TC02_standardUserCompletesCheckout() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        CheckoutActions checkout = beginCheckout(cart);
        checkout.finish();

        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).isVisible();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).hasText("Thank you for your order!");
        PlaywrightAssertions.assertThat(checkoutLocators.checkoutTile()).hasText("Checkout: Complete!");
    }

    @Test
    void TC26_performanceGlitchUserCompletesCheckoutWithinConfiguredLimit() {
        long start = System.nanoTime();
        InventoryActions inventory = loginAs(PERFORMANCE_GLITCH_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        CheckoutActions checkout = beginCheckout(cart);
        checkout.finish();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).isVisible();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).hasText("Thank you for your order!");
        long elapsed = TestMetrics.elapsedMillis(start);
        long limit = TestConfig.performanceGlitchCheckoutMaxMs();
        TestMetrics.log("performance_glitch_user full checkout", elapsed, limit);

        if (TestConfig.performanceAssertionsEnabled()) {
            assertTrue(elapsed <= limit, () -> "Checkout took " + elapsed + " ms; configured limit is " + limit + " ms");
        }
    }

    @Test
    void TC37_downloadPdfAfterOrderCompleted() {
        InventoryActions inventory = loginAs(STANDARD_USER);
        CartActions cart = addAndOpenCart(inventory, SAUCE_LABS_BACKPACK);
        CheckoutActions checkout = beginCheckout(cart, CheckoutTestData.unique());
        checkout.finish();
        PlaywrightAssertions.assertThat(checkoutLocators.confirmation()).isVisible();
        Download download = page.waitForDownload(() -> checkoutLocators.downloadReceiptButton().click());

        assertTrue(download.suggestedFilename().endsWith(".pdf"),
                () -> "Expected a PDF file but got: " + download.suggestedFilename());

        java.nio.file.Path savedPath = download.path();
        assertTrue(java.nio.file.Files.exists(savedPath));
        assertTrue(savedPath.toFile().length() > 0, "Downloaded PDF should not be empty");
    }
}