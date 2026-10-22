package com.saucedemo.tests;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.saucedemo.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.regex.Pattern;

class LoginPageTest extends BaseTest {
    private static final String STANDARD_USER = "standard_user";

    @Test
    void TC03_lockedOutUserIsRejectedWithSpecificMessage() {
        loginActions.login("locked_out_user", TestConfig.PASSWORD);

        PlaywrightAssertions.assertThat(loginLocators.error())
                .containsText("Epic sadface: Sorry, this user has been locked out.");
        PlaywrightAssertions.assertThat(loginLocators.container()).isVisible();
    }

    @ParameterizedTest(name = "TC05/TC06 invalid username/password={0}")
    @CsvSource({"standard_user,wrong_password", "not_a_real_user,secret_sauce"})
    void TC05_TC06_invalidCredentialsUseSameGenericError(String username, String password) {
        loginActions.login(username, password);

        PlaywrightAssertions.assertThat(loginLocators.error())
                .containsText("Epic sadface: Username and password do not match any user in this service");
    }

    @Test
    void TC07_emptyCredentialsRequireUsername() {
        loginActions.enterPassword(TestConfig.PASSWORD);
        loginActions.clickLogin();
        PlaywrightAssertions.assertThat(loginLocators.error()).containsText("Epic sadface: Username is required");
    }

    @Test
    void TC08_validUsernameWithoutPassword() {
        loginActions.enterUsername(STANDARD_USER);
        loginActions.clickLogin();
        PlaywrightAssertions.assertThat(loginLocators.error()).containsText("Epic sadface: Password is required");
    }

    @Test
    void TC36_oversizedCredentialsFailWithoutLeavingLoginPage() {
        String oversized = "x".repeat(300);
        loginActions.login(oversized, oversized);

        PlaywrightAssertions.assertThat(loginLocators.error())
                .containsText("Epic sadface: Username and password do not match any user in this service");
        PlaywrightAssertions.assertThat(page).hasURL(Pattern.compile("https://www\\.saucedemo\\.com/?"));
    }
}