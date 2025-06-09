package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimGroup extends ScimResource {

    private String displayName;
    private List<Member> members;

    public static final String SCHEMA_CORE_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";

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

     @Override
     public ScimUser.Meta getMeta() {
         if (super.getMeta() == null) {
             super.setMeta(new ScimUser.Meta());
         }

         if (super.getMeta().getResourceType() == null) {
              super.getMeta().setResourceType("Group");
         }
         return super.getMeta();
     }

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