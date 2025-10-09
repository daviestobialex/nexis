/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author daviestobialex
 */
public final record ManifestObject(
        String version,
        String organizationName,
        String organizationUrl,
        Map<String, String> organizationRegistrationNumbers,
        List<String> countryCodes,
        String category,
        ContactInfo contact,
        String policyUrl,
        String termsUrl,
        List<String> dependencies,
        List<String> specifications) {

    public record ContactInfo(
            String fullName,
            String email,
            String mobileNumber,
            String organisationRole) {

    }

    public static class Builder {

        private String version;
        private String organizationName;
        private String organizationUrl;
        private Map<String, String> organizationRegistrationNumbers;
        private List<String> countryCodes;
        private String category;
        private ContactInfo contact;
        private String policyUrl;
        private String termsUrl;
        private List<String> dependencies;
        private List<String> specifications;

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder organizationName(String organizationName) {
            this.organizationName = organizationName;
            return this;
        }

        public Builder organizationUrl(String organizationUrl) {
            this.organizationUrl = organizationUrl;
            return this;
        }

        public Builder organizationRegistrationNumbers(Map<String, String> organizationRegistrationNumbers) {
            this.organizationRegistrationNumbers = organizationRegistrationNumbers;
            return this;
        }

        public Builder countryCodes(List<String> countryCodes) {
            this.countryCodes = countryCodes;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder contact(ContactInfo contact) {
            this.contact = contact;
            return this;
        }

        public Builder policyUrl(String policyUrl) {
            this.policyUrl = policyUrl;
            return this;
        }

        public Builder termsUrl(String termsUrl) {
            this.termsUrl = termsUrl;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder specifications(List<String> specifications) {
            this.specifications = specifications;
            return this;
        }

        public ManifestObject build() {
            return new ManifestObject(
                    version,
                    organizationName,
                    organizationUrl,
                    organizationRegistrationNumbers,
                    countryCodes != null ? countryCodes : List.of(),
                    category,
                    contact,
                    policyUrl,
                    termsUrl,
                    dependencies != null ? dependencies : List.of(),
                    specifications != null ? specifications : List.of()
            );
        }
    }

    public List<String> getDependencies() {
        return dependencies != null ? dependencies : Collections.emptyList();
    }

    public List<String> getSpecifications() {
        return specifications != null ? specifications : Collections.emptyList();
    }
}
