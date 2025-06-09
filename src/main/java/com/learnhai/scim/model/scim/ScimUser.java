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
    private String password;
    private List<Email> emails;
    private List<PhoneNumber> phoneNumbers;
    private List<GroupReference> groups; // <-- ENSURE THIS IS PRESENT

    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    private EnterpriseUserExtension enterpriseUser;

    public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String SCHEMA_ENTERPRISE_USER = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

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
        private String type;
        private boolean primary;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhoneNumber {
        private String value;
        private String display;
        private String type;
        private boolean primary;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta { // This Meta class is defined in ScimUser
        private String resourceType = "User"; // Default for User's Meta
        private Instant created;
        private Instant lastModified;
        private String location;
        private String version;
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
            private String value;
            @JsonProperty("$ref")
            private String ref;
            private String displayName;
        }
    }

    // NEW INNER CLASS for group reference - ENSURE THIS IS PRESENT
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupReference {
        private String value;
        private String display;
        @JsonProperty("$ref")
        private String ref;
        private String type;
    }

     @Override
     public Meta getMeta() {
         if (super.getMeta() == null) {
             super.setMeta(new Meta());
         }

         if (super.getMeta().getResourceType() == null) {
              super.getMeta().setResourceType("User");
         }
         return super.getMeta();
     }

     @Override
     public List<String> getSchemas() {
         if (super.getSchemas() == null || super.getSchemas().isEmpty()) {
             super.setSchemas(new ArrayList<>(List.of(SCHEMA_CORE_USER)));
         } else if (!super.getSchemas().contains(SCHEMA_CORE_USER)) {
             List<String> updatedSchemas = new ArrayList<>(super.getSchemas());
             updatedSchemas.add(SCHEMA_CORE_USER);
             super.setSchemas(updatedSchemas);
         }
         return super.getSchemas();
     }
}