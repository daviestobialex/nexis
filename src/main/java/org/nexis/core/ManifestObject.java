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
        int version,
        String organizationName,
        String organizationUrl,
        int protocolVersion,
        Map<String, String> organizationRegistrationNumbers,
        List<String> countryCodes,
        List<String> categories,
        ContactInfo contact,
        String policyUrl,
        String termsUrl,
        List<String> dependencies,
        String specification,
        String baseUrl) {

    public record ContactInfo(
            String fullName,
            String email,
            String mobileNumber,
            String organisationRole) {

    }

    public static class Builder {

        private int version;
        private String organizationName;
        private String organizationUrl;
        private int protocolVersion;
        private Map<String, String> organizationRegistrationNumbers;
        private List<String> countryCodes;
        private List<String> categories;
        private ContactInfo contact;
        private String policyUrl;
        private String termsUrl;
        private List<String> dependencies;
        private String specification;
        private String baseUrl;

        public Builder version(int version) {
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

        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public Builder specifications(String specification) {
            this.specification = specification;
            return this;
        }
        
            public Builder protocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public ManifestObject build() {
            return new ManifestObject(
                    version,
                    organizationName,
                    organizationUrl,
                    protocolVersion,
                    organizationRegistrationNumbers,
                    countryCodes != null ? countryCodes : List.of(),
                    categories,
                    contact,
                    policyUrl,
                    termsUrl,
                    dependencies != null ? dependencies : List.of(),
                    specification,
                    baseUrl
            );
        }
    }

    public List<String> getDependencies() {
        return dependencies != null ? dependencies : Collections.emptyList();
    }

    public String getSpecifications() {
        return specification;
    }
}
