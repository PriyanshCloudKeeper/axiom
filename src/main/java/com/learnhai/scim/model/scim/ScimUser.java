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
@EqualsAndHashCode(callSuper = true) // Ensure ScimResource's fields are included if it has them
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUser extends ScimResource { // Make sure ScimResource also has @Data or getters/setters

    // Initialize schemas here if it's always the same for a new ScimUser
    // However, ScimResource already initializes schemas.
    // If ScimResource correctly initializes schemas and meta, this might not be needed here.

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
    private boolean active = true; // Default value
    private String password;
    private List<Email> emails;
    private List<PhoneNumber> phoneNumbers;

    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    private EnterpriseUserExtension enterpriseUser;

    public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String SCHEMA_ENTERPRISE_USER = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    // Constructor for ScimResource to set default schemas
    // This will be called by super() if ScimResource defines such a constructor
    // If ScimResource has a no-arg constructor and initializes schemas,
    // then the specific User schema needs to be added.
    // One way is to override the getter if Lombok doesn't allow easy post-construction init.
    // Or ensure ScimResource(List<String>) is called correctly.

    // The super(List<String>) call needs a constructor if you remove the manual one.
    // Let's adjust ScimResource and how User/Group call it.

    // Inner classes (Name, Email, PhoneNumber, Meta, EnterpriseUserExtension) remain the same...
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
    @NoArgsConstructor // Meta needs a no-arg constructor
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

     // Override getMeta to ensure it's initialized if null from super
     @Override
     public Meta getMeta() {
         if (super.getMeta() == null) {
             super.setMeta(new Meta());
         }
         // Ensure resourceType is set for User if it wasn't already
         if (super.getMeta().getResourceType() == null) {
              super.getMeta().setResourceType("User");
         }
         return super.getMeta();
     }

     // Override getSchemas to ensure it's initialized correctly
     @Override
     public List<String> getSchemas() {
         if (super.getSchemas() == null || super.getSchemas().isEmpty()) {
             super.setSchemas(new ArrayList<>(List.of(SCHEMA_CORE_USER)));
         } else if (!super.getSchemas().contains(SCHEMA_CORE_USER)) {
             // If schemas list exists but doesn't contain the core user schema, add it.
             // This is a bit defensive, depends on how ScimResource initializes.
             List<String> updatedSchemas = new ArrayList<>(super.getSchemas());
             updatedSchemas.add(SCHEMA_CORE_USER);
             super.setSchemas(updatedSchemas);
         }
         return super.getSchemas();
     }

}