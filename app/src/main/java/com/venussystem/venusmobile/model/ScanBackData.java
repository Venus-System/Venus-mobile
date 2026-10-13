package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dados estruturados extraídos da foto traseira.
 *
 * A estrutura já contempla os campos necessários para uma futura
 * submissão ao Mongo e posterior aprovação administrativa no PostgreSQL,
 * mas esta classe não realiza persistência nem upload.
 */
public class ScanBackData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String fullText;
    private final List<String> lines;
    private final boolean ingredientSectionFound;
    private final String ingredientsRawText;
    private final List<ScanBackIngredientCandidate> ingredients;
    private final String manufacturer;
    private final String manufacturerAddress;
    private final String contact;
    private final String country;
    private final String batch;
    private final String registrationNumber;
    private final String netContent;
    private final String usage;
    private final String precautions;
    private final String warnings;
    private final List<String> claims;
    private final String barcode;
    private final String otherText;
    private final String formulaSignatureCandidate;

    public ScanBackData(
            @NonNull String fullText,
            @NonNull List<String> lines,
            boolean ingredientSectionFound,
            @NonNull String ingredientsRawText,
            @NonNull List<ScanBackIngredientCandidate> ingredients,
            @NonNull String manufacturer,
            @NonNull String manufacturerAddress,
            @NonNull String contact,
            @NonNull String country,
            @NonNull String batch,
            @NonNull String registrationNumber,
            @NonNull String netContent,
            @NonNull String usage,
            @NonNull String precautions,
            @NonNull String warnings,
            @NonNull List<String> claims,
            @NonNull String barcode,
            @NonNull String otherText,
            @NonNull String formulaSignatureCandidate
    ) {
        this.fullText = fullText;
        this.lines = new ArrayList<>(lines);
        this.ingredientSectionFound = ingredientSectionFound;
        this.ingredientsRawText = ingredientsRawText;
        this.ingredients = new ArrayList<>(ingredients);
        this.manufacturer = manufacturer;
        this.manufacturerAddress = manufacturerAddress;
        this.contact = contact;
        this.country = country;
        this.batch = batch;
        this.registrationNumber = registrationNumber;
        this.netContent = netContent;
        this.usage = usage;
        this.precautions = precautions;
        this.warnings = warnings;
        this.claims = new ArrayList<>(claims);
        this.barcode = barcode;
        this.otherText = otherText;
        this.formulaSignatureCandidate = formulaSignatureCandidate;
    }

    @NonNull
    public String getFullText() { return fullText; }

    @NonNull
    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isIngredientSectionFound() { return ingredientSectionFound; }

    @NonNull
    public String getIngredientsRawText() { return ingredientsRawText; }

    @NonNull
    public List<ScanBackIngredientCandidate> getIngredients() {
        return Collections.unmodifiableList(ingredients);
    }

    @NonNull
    public String getManufacturer() { return manufacturer; }

    @NonNull
    public String getManufacturerAddress() { return manufacturerAddress; }

    @NonNull
    public String getContact() { return contact; }

    @NonNull
    public String getCountry() { return country; }

    @NonNull
    public String getBatch() { return batch; }

    @NonNull
    public String getRegistrationNumber() { return registrationNumber; }

    @NonNull
    public String getNetContent() { return netContent; }

    @NonNull
    public String getUsage() { return usage; }

    @NonNull
    public String getPrecautions() { return precautions; }

    @NonNull
    public String getWarnings() { return warnings; }

    @NonNull
    public List<String> getClaims() {
        return Collections.unmodifiableList(claims);
    }

    @NonNull
    public String getBarcode() { return barcode; }

    @NonNull
    public String getOtherText() { return otherText; }

    @NonNull
    public String getFormulaSignatureCandidate() {
        return formulaSignatureCandidate;
    }

    public boolean hasIngredients() {
        return !ingredients.isEmpty();
    }
}
