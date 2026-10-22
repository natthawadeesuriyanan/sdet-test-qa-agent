package com.saucedemo.helper;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record CheckoutTestData(String firstName, String lastName, String postalCode) {
    public static CheckoutTestData unique() {
        String postalCode = Integer.toString(ThreadLocalRandom.current().nextInt(10_000, 100_000));
        return uniqueWithPostalCode(postalCode);
    }

    public static CheckoutTestData uniqueWithPostalCode(String postalCode) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new CheckoutTestData("Name" + suffix, "Last" + suffix, postalCode);
    }
}