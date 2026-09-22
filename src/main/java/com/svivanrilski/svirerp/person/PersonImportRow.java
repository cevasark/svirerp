package com.svivanrilski.svirerp.person;

/** One row of a Zeffy contacts export — already-validated/typed data only, no parsing logic
 *  itself (see {@link PersonImportService}). Secondary email, Language, Country, and Region are
 *  read from the file but not carried here — Person has no fields for them. */
public record PersonImportRow(
        String firstName,
        String lastName,
        String email,
        String phone,
        String addressLine1,
        String city,
        String zip,
        boolean unsubscribed) {
}
