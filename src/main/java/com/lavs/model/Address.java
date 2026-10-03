package com.lavs.model;

public class Address {
    private Long addressId;
    private String customerId;
    private String street;
    private String suburb;
    private String city;
    private String postCode;
    private String country;
    private boolean isMailing;

    public Address(Long addressId, String customerId, String street, String suburb, String city, String postCode, String country, boolean isMailing) {
        this.addressId = addressId;
        this.customerId = customerId;
        this.street = street;
        this.suburb = suburb;
        this.city = city;
        this.postCode = postCode;
        this.country = country;
        this.isMailing = isMailing;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getSuburb() {
        return suburb;
    }

    public void setSuburb(String suburb) {
        this.suburb = suburb;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public boolean isMailing() {
        return isMailing;
    }

    public void setMailing(boolean isMailing) {
        this.isMailing = isMailing;
    }
    
}
