package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor; // Good to have for Jackson and potential subclass needs

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor // For Jackson and subclasses that might call super() implicitly
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ScimResource {
    // Field initializations are done when an object is created,
    // including via Lombok's @NoArgsConstructor
    private List<String> schemas = new ArrayList<>();
    private String id;
    private String externalId;
    private ScimUser.Meta meta = new ScimUser.Meta(); // Default Meta, resourceType set by subclasses
}