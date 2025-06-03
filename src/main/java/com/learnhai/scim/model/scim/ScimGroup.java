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

    public ScimGroup() {
        super(new ArrayList<>(List.of(SCHEMA_CORE_GROUP)));
        if (getMeta() != null) {
            getMeta().setResourceType("Group");
        } else {
            setMeta(new ScimUser.Meta()); // Re-use Meta structure from ScimUser
            getMeta().setResourceType("Group");
        }
    }
    public static final String SCHEMA_CORE_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";


    // Inner classes
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Member {
        private String value; // User ID or Group ID
        private String display; // Username or Group display name
        private String type; // "User" or "Group"
        @JsonProperty("$ref")
        private String ref; // SCIM URI to the member resource
    }
}