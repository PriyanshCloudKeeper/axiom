package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor; // Keep this

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor // Lombok will generate the no-arg constructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimGroup extends ScimResource {

    private String displayName;
    private List<Member> members;

    // THIS MANUAL CONSTRUCTOR IS THE PROBLEM - DELETE IT OR COMMENT IT OUT
    /*
    public ScimGroup() {
        super(new ArrayList<>(List.of(SCHEMA_CORE_GROUP)));
        if (getMeta() == null) {
            setMeta(new ScimUser.Meta()); // Re-use Meta structure
        }
        getMeta().setResourceType("Group");
    }
    */

    public static final String SCHEMA_CORE_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";

    // Inner classes (Member) remain the same...
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Member {
        private String value;
        private String display;
        private String type;
        @JsonProperty("$ref")
        private String ref;
    }

     // Override getMeta to ensure it's initialized if null from super
     @Override
     public ScimUser.Meta getMeta() { // Assuming ScimResource.meta is of type ScimUser.Meta
         if (super.getMeta() == null) {
             super.setMeta(new ScimUser.Meta());
         }
          // Ensure resourceType is set for Group
         if (super.getMeta().getResourceType() == null) {
              super.getMeta().setResourceType("Group");
         }
         return super.getMeta();
     }

     // Override getSchemas to ensure it's initialized correctly
     @Override
     public List<String> getSchemas() {
         if (super.getSchemas() == null || super.getSchemas().isEmpty()) {
             super.setSchemas(new ArrayList<>(List.of(SCHEMA_CORE_GROUP)));
         } else if (!super.getSchemas().contains(SCHEMA_CORE_GROUP)) {
             List<String> updatedSchemas = new ArrayList<>(super.getSchemas());
             updatedSchemas.add(SCHEMA_CORE_GROUP);
             super.setSchemas(updatedSchemas);
         }
         return super.getSchemas();
     }
}