package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUser extends ScimResource {

    private String userName;
    private Name name;
    private String displayName;
    private String nickName;
    private String profileUrl;
    private String title;
    private String userType;
    private String preferredLanguage;
    private String locale;
    private String timezone;
    private boolean active = true;
    private String password; // Write-only usually
    private List<Email> emails;
    private List<PhoneNumber> phoneNumbers;
    // Add other SCIM core attributes as needed: ims, photos, addresses, groups, entitlements, roles, x509Certificates

    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    private EnterpriseUserExtension enterpriseUser;

    public ScimUser() {
        super(new ArrayList<>(List.of(SCHEMA_CORE_USER)));
        if (getMeta() != null) { // Ensure meta is initialized from super
            getMeta().setResourceType("User");
        } else {
            setMeta(new Meta()); // Fallback if super didn't initialize
            getMeta().setResourceType("User");
        }
    }

    public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String SCHEMA_ENTERPRISE_USER = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";


    // Inner classes
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Name {
        private String formatted;
        private String familyName;
        private String givenName;
        private String middleName;
        private String honorificPrefix;
        private String honorificSuffix;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Email {
        private String value;
        private String display;
        private String type; // e.g., work, home
        private boolean primary;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhoneNumber {
        private String value;
        private String display;
        private String type; // e.g., work, mobile
        private boolean primary;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        private String resourceType;
        private Instant created;
        private Instant lastModified;
        private String location;
        private String version; // ETag
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnterpriseUserExtension {
        private String employeeNumber;
        private String costCenter;
        private String organization;
        private String division;
        private String department;
        private Manager manager;

        @Data
        @NoArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Manager {
            private String value; // Manager's User ID
            @JsonProperty("$ref")
            private String ref;   // SCIM URI to the manager resource
            private String displayName; // Manager's display name
        }
    }
}