package com.mortgee.al_mobile.data.model

enum class LoanField(val path: String) {
    INCOME("householdAnnualGrossIncome"),
    DEBT("existingDebtLiabilities"),
    DEPOSIT("deposit"),
    TERM("requestedMortgageTerm"),
    PRICE("sellerAskingPrice"),
    COUNTY("property.county"),
    AREA("property.floor_area"),
    BEDS("property.bedrooms"),
    BATHS("property.bathrooms"),
    YEAR("property.year_built"),
    TYPE("property.property_type"),
    BER("property.ber_rating"),
}
